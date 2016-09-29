package dsmoq.services

import java.nio.file.Paths
import java.util.UUID

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.joda.time.DateTime
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.AppConf
import dsmoq.persistence
import dsmoq.persistence.GroupType
import dsmoq.persistence.PostgresqlHelper.PgSQLSyntaxType
import dsmoq.persistence.SuggestType
import dsmoq.services.json.SuggestData
import dsmoq.services.json.TagData.TagDetail
import scalax.io.Resource
import scalikejdbc.DB
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef
import scalikejdbc.scalikejdbcSQLSyntaxToStringImplicitDef
import scalikejdbc.select
import scalikejdbc.sqls
import scalikejdbc.withSQL

object SystemService extends LazyLogging {
  private val userImageDownloadRoot = AppConf.imageDownloadRoot + "user/"
  private val groupImageDownloadRoot = AppConf.imageDownloadRoot + "groups/"

  private val LOG_MARKER_USER_GROUP = MarkerFactory.getMarker("USER_GROUP")

  /**
   * データセットへのアクセスログを記入する。
   *
   * @param datasetId データセットID
   * @param user ユーザオブジェクト
   * @return エラーが発生した場合は、例外をFailureに包んで返却する。
   */
  def writeDatasetAccessLog(datasetId: String, user: User): Try[Unit] = {
    Try {
      DB.localTx { implicit s =>
        if (persistence.Dataset.find(datasetId).nonEmpty) {
          persistence.DatasetAccessLog.create(UUID.randomUUID().toString, datasetId, user.id, DateTime.now)
        }
      }
    }
  }

  /**
   * ライセンスの一覧を取得する。
   *
   * @return ライセンスの一覧。エラーが発生した場合は、例外をFailureに包んで返却する。
   */
  def getLicenses(): Try[Seq[dsmoq.services.json.License]] = {
    Try {
      val licenses = DB.readOnly { implicit s =>
        persistence.License.findOrderedAll()
      }
      licenses.map { x =>
        dsmoq.services.json.License(
          id = x.id,
          name = x.name
        )
      }
    }
  }

  /**
   * ユーザの一覧を取得する。
   *
   * @return ユーザの一覧。エラーが発生した場合は、例外をFailureに包んで返却する。
   */
  def getAccounts(): Try[Seq[User]] = {
    Try {
      DB.readOnly { implicit s =>
        val u = persistence.User.u
        val ma = persistence.MailAddress.ma
        withSQL {
          select(u.result.*, ma.result.address)
            .from(persistence.User as u)
            .innerJoin(persistence.MailAddress as ma).on(u.id, ma.userId)
            .where
            .eq(u.disabled, false)
            .and
            .isNull(ma.deletedAt)
            .orderBy(u.name)
        }.map(rs => (persistence.User(u.resultName)(rs), rs.string(ma.resultName.address)))
          .list
          .apply()
          .map(x => User(x._1, x._2))
      }
    }
  }

  /**
   * ユーザの一覧を取得します。
   * 条件を指定すれば取得対象を絞り込みます。
   * (取得順：users.name の昇順。)
   *
   * @param query 絞り込み条件 (比較対象：DB:users.name, users.fullname, mail_addresses.address)
   * @param limit 取得件数
   * @param offset 取得位置
   * @return (条件に該当する) ユーザ一覧。エラーが発生した場合は、例外をFailureに包んで返却する。
   */
  def getUsers(query: Option[String], limit: Option[Int], offset: Option[Int]): Try[Seq[SuggestData.User]] = {
    logger.debug(
      LOG_MARKER_USER_GROUP,
      "getUsers: start : [query] = {}, [offset] = {}, [limit] = {}",
      query,
      offset,
      limit
    )
    Try {
      DB.readOnly { implicit s =>
        val u = persistence.User.u // TB: users
        val ma = persistence.MailAddress.ma // TB: mail_addresses

        /* - if query == None
         *  select u.id, u.name, u.fullname, u.organization, u.title, u.description
         *  from users as u
         *  left join mail_addresses as ma on u.id = ma.user_id
         *  where u.deleted_at is null
         *  order by u.name offset "offset" limit "limit";
         * - else
         *  select u.id, u.name, u.fullname, u.organization, u.title, u.description
         *  from users as u
         *  left join mail_addresses as ma on u.id = ma.user_id
         *  where u.deleted_at is null
         *    and ( u.name like '"query"%' or u.fullname like '"query"%' or ma.address like '"query"%' )
         *  order by u.name offset "offset" limit "limit";
         *
         * - if offset == None offset = 0
         * - if limit == None limit = 100
         */
        val result = withSQL {
          select.all[persistence.User](u)
            .from(persistence.User as u)
            .leftJoin(persistence.MailAddress as ma)
            .on(u.id, ma.userId)
            .where
            .eq(u.disabled, false)
            .map { sql =>
              query match {
                case Some(x) =>
                  sql.and.like(u.name, x + "%").or.like(u.fullname, x + "%").or.like(ma.address, x + "%")
                case None =>
                  sql
              }
            }
            .orderBy(sqls"name")
            .offset(offset.getOrElse(0))
            .limit(limit.getOrElse(100))
        }.map(persistence.User(u.resultName)(_)).list.apply().map { x =>
          SuggestData.User(
            id = x.id,
            name = x.name,
            fullname = x.fullname,
            organization = x.organization,
            title = x.title,
            description = x.description,
            image = userImageDownloadRoot + x.id + "/" + x.imageId
          )
        }
        logger.info(
          LOG_MARKER_USER_GROUP,
          "getUsers: [result size] = {} : [query] = {}, [offset] = {}, [limit] = {}",
          result.size.toString,
          query,
          offset.getOrElse(0).toString,
          limit.getOrElse(0).toString
        )
        logger.debug(LOG_MARKER_USER_GROUP, "getUsers: end : [result] = {}", result)
        result
      }
    }
  }

  /**
   * グループの一覧を取得します。
   * 条件を指定すれば取得対象を絞り込みます。
   *
   * @param query 絞り込み条件 (比較対象：DB:groups.name)
   * @param limit 取得件数
   * @param offset 取得位置
   * @return グループ一覧。エラーが発生した場合は、例外をFailureに包んで返却する。
   */
  def getGroups(query: Option[String], limit: Option[Int], offset: Option[Int]): Try[Seq[SuggestData.Group]] = {
    Try {
      val escapedQuery = query match {
        case Some(x) => x.replaceAll("%", "\\\\%").replaceAll("_", "\\\\_") + "%"
        case None => ""
      }

      val g = persistence.Group.g
      val gi = persistence.GroupImage.gi
      DB.readOnly { implicit s =>
        withSQL {
          select(g.result.*, gi.result.*)
            .from(persistence.Group as g)
            .innerJoin(persistence.GroupImage as gi)
            .on(sqls.eq(g.id, gi.groupId).and.eq(gi.isPrimary, true).and.isNull(gi.deletedAt))
            .where
            .like(g.name, escapedQuery)
            .and
            .eq(g.groupType, GroupType.Public)
            .and
            .isNull(g.deletedAt)
            .orderBy(g.name, g.createdAt).desc
            .offset(offset.getOrElse(0))
            .limit(limit.getOrElse(100))
        }.map(rs => (persistence.Group(g.resultName)(rs), persistence.GroupImage(gi.resultName)(rs))).list.apply()
          .map { x =>
            SuggestData.Group(
              id = x._1.id,
              name = x._1.name,
              image = groupImageDownloadRoot + x._1.id + "/" + x._2.imageId
            )
          }
      }
    }
  }

  /**
   * ユーザとグループの一覧を取得します。
   * 条件を指定すれば取得対象を絞り込みます。
   * (取得順：users.name,groups.name の昇順。)
   *
   * @param param 絞り込み条件 (比較対象：DB:users.name, users.fullname, mail_addresses.address)
   * @param limit 取得件数
   * @param offset 取得位置
   * @param excludeIds 除外対象のid (除外対象：DB:users.id, groups.id)
   * @return (条件に該当する) ユーザとグループの一覧。エラーが発生した場合は、例外をFailureに包んで返却する。
   */
  def getUsersAndGroups(
    param: Option[String],
    limit: Option[Int],
    offset: Option[Int],
    excludeIds: Seq[String]
  ): Try[Seq[SuggestData.WithType]] = {
    logger.debug(
      LOG_MARKER_USER_GROUP,
      "getUsersAndGroups: start : [param] = {}, [offset] = {}, [limit] = {}, [excludeIds] = {}",
      param,
      offset,
      limit,
      excludeIds
    )
    val query = param match {
      case Some(x) => x.replaceAll("%", "\\\\%").replaceAll("_", "\\\\_") + "%"
      case None => "%"
    }
    Try {
      DB.readOnly { implicit s =>
        val u = persistence.User.u // TB: users
        val ma = persistence.MailAddress.ma // TB: mail_addresses
        val g = persistence.Group.g // TB: groups
        val gi = persistence.GroupImage.gi // TB: group_images

        /* select u.id, u.name, u.image_id, u.fullname, u.organization, 1 as type
         * from users as u
         * left join mail_addresses as ma on u.id = ma.user_id
         * where u.id not in ( "excludeIds" )
         *   and ( u.name like "query" or u.fullname like "query" or ma.address like "query" )
         *   and u.deleted_at is null
         * union ( select g.id, g.name, gi.image_id, null, null, 2 as type
         *   from groups as g
         *   inner join group_images as gi on g.id = gi.group_id and gi.is_primary = true and g.deleted_at is null
         *   where g.id not in ( "excludeIds" )
         *     and g.name like '"query"%'
         *     and g.group_type = 0
         *     and g.deleted_at is null )
         * order by name offset "offset" limit "limit";
         *
         * - if offset == None offset = 0
         * - if limit == None limit = 100
         * - GroupType.Public = 0
         */
        val result = withSQL {
          select(u.id, u.name, u.imageId, u.fullname, u.organization, sqls"'1' as type")
            .from(persistence.User as u)
            .leftJoin(persistence.MailAddress as ma)
            .on(u.id, ma.userId)
            .where
            .notIn(u.id, excludeIds.map(sqls.uuid))
            .and
            .append(sqls"(")
            .like(u.name, query)
            .or
            .like(u.fullname, query)
            .or
            .like(ma.address, query)
            .append(sqls")")
            .and
            .eq(u.disabled, false)
            .union(
              select(g.id, g.name, gi.imageId, sqls"null, null, '2' as type")
                .from(persistence.Group as g)
                .innerJoin(persistence.GroupImage as gi)
                .on(sqls.eq(g.id, gi.groupId).and.eq(gi.isPrimary, true).and.isNull(gi.deletedAt))
                .where
                .notIn(g.id, excludeIds.map(sqls.uuid))
                .and
                .like(g.name, query)
                .and
                .eq(g.groupType, GroupType.Public)
                .and
                .isNull(g.deletedAt)
            )
            .orderBy(sqls"name")
            .offset(offset.getOrElse(0))
            .limit(limit.getOrElse(100))
        }.map { rs =>
          (
            rs.string(persistence.User.id),
            rs.string(persistence.User.name),
            rs.string(persistence.User.imageId),
            rs.string(persistence.User.fullname),
            rs.string(persistence.User.organization),
            rs.int("type")
          )
        }.list.apply().flatMap {
          case (id, name, imageId, fullname, organization, SuggestType.User) => {
            Some(
              SuggestData.UserWithType(
                id = id,
                name = name,
                fullname = fullname,
                organization = organization,
                image = userImageDownloadRoot + id + "/" + imageId
              )
            )
          }
          case (id, name, imageId, fullname, organization, SuggestType.Group) => {
            Some(
              SuggestData.GroupWithType(
                id = id,
                name = name,
                image = groupImageDownloadRoot + id + "/" + imageId
              )
            )
          }
          case _ => {
            None
          }
        }
        logger.info(
          LOG_MARKER_USER_GROUP,
          "getUsersAndGroups: [result size] = {} : [param] = {}, [offset] = {}, [limit] = {}, [excludeIds] = {}",
          result.size.toString,
          param,
          offset.getOrElse(0).toString,
          limit.getOrElse(0).toString,
          excludeIds
        )
        logger.debug(LOG_MARKER_USER_GROUP, "getUsersAndGroups: end : [result] = {}", result)
        result
      }
    }
  }

  /**
   * 属性の一覧を取得します。
   * 条件を指定すれば取得対象を絞り込みます。
   *
   * @param query 絞り込み条件 (比較対象：DB:annotation.name)
   * @param limit 取得件数
   * @param offset 取得位置
   * @return 属性の一覧。エラーが発生した場合は、例外をFailureに包んで返却する。
   */
  def getAttributes(query: Option[String], limit: Option[Int], offset: Option[Int]): Try[Seq[String]] = {
    Try {
      val a = persistence.Annotation.a
      DB.readOnly { implicit s =>
        withSQL {
          select.apply[Any](a.result.*)
            .from(persistence.Annotation as a)
            .where
            .map { sql =>
              query match {
                case Some(x) => sql.like(a.name, x.replaceAll("%", "\\\\%").replaceAll("_", "\\\\_") + "%").and
                case None => sql
              }
            }
            .isNull(a.deletedAt)
            .orderBy(a.name)
            .offset(offset.getOrElse(0))
            .limit(limit.getOrElse(100))
        }.map(rs => rs.string(a.resultName.name)).list.apply
      }
    }
  }

  /**
   * タグの一覧を取得する。
   *
   * @return タグの一覧。エラーが発生した場合は、例外をFailureに包んで返却する。
   */
  def getTags(): Try[Seq[TagDetail]] = {
    Try {
      DB.readOnly { implicit s =>
        val t = persistence.Tag.t
        withSQL {
          select
            .from(persistence.Tag as t)
            .where
            .isNull(t.deletedBy)
            .and
            .isNull(t.deletedAt)
            .orderBy(t.tag)
        }.map(persistence.Tag(t.resultName)).list.apply().map { x =>
          TagDetail(x.tag, x.color)
        }
      }
    }
  }

  /**
   * トップページに表示するメッセージを取得する。
   *
   * @return メッセージの文字列。エラーが発生した場合は、例外をFailureに包んで返却する。
   */
  def getMessage(): Try[String] = {
    Try {
      val file = Paths.get(AppConf.messageDir, "message.txt").toFile
      if (file.exists()) {
        val resource = Resource.fromFile(file)
        resource.string
      } else {
        // ファイルが存在していない場合は空文字を返す
        ""
      }
    }
  }
}
