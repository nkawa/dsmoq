package dsmoq.services

import java.nio.file.Paths
import java.util.UUID

import dsmoq.services.json.TagData.TagDetail
import dsmoq.{AppConf, persistence}
import dsmoq.persistence.{PostgresqlHelper, SuggestType, GroupType}
import dsmoq.services.json.SuggestData
import org.joda.time.DateTime
import scalikejdbc._
import scala.util.{Failure, Success, Try}
import PostgresqlHelper._
import scalax.io.Resource
import com.typesafe.scalalogging.LazyLogging
import org.slf4j.MarkerFactory

object SystemService extends LazyLogging {
  private val userImageDownloadRoot = AppConf.imageDownloadRoot + "user/"
  private val groupImageDownloadRoot = AppConf.imageDownloadRoot + "groups/"

  private val LOG_MARKER_USER_GROUP = MarkerFactory.getMarker("USER_GROUP")

  def writeDatasetAccessLog(datasetId: String, user: User): Try[Unit] = {
    try {
      DB localTx { implicit s =>
        if (persistence.Dataset.find(datasetId).nonEmpty) {
          persistence.DatasetAccessLog.create(UUID.randomUUID().toString, datasetId, user.id, DateTime.now)
        }
      }
      Success(Unit)
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  def getLicenses()  = {
    val licenses = DB readOnly { implicit s =>
      persistence.License.findOrderedAll()
    }
    licenses.map(x =>
      dsmoq.services.json.License(
        id = x.id,
        name = x.name
      ))
  }

  def getAccounts() = {
    DB readOnly { implicit s =>
      val u = persistence.User.u
      val ma = persistence.MailAddress.ma
      withSQL {
        select(u.result.*, ma.result.address)
          .from(persistence.User as u)
          .innerJoin(persistence.MailAddress as ma).on(u.id, ma.userId)
          .where
          .isNull(u.deletedAt)
          .and
          .isNull(ma.deletedAt)
          .orderBy(u.name)
      }.map(rs => (persistence.User(u.resultName)(rs), rs.string(ma.resultName.address))).list().apply
        .map(x => User(x._1, x._2))
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
    * @return (条件に該当する) ユーザ一覧
    */
  def getUsers(query: Option[String], limit: Option[Int], offset: Option[Int]) = {
    logger.debug(LOG_MARKER_USER_GROUP, "getUsers: start : [query] = {}, [offset] = {}, [limit] = {}", query, offset, limit)
    DB readOnly { implicit s =>
      val u = persistence.User.u              // TB: users
      val ma = persistence.MailAddress.ma     // TB: mail_addresses

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
          .isNull(u.deletedAt)
          .map{sql =>
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
      }.map(persistence.User(u.resultName)(_)).list().apply.map {x =>
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
      logger.info(LOG_MARKER_USER_GROUP, "getUsers: [result size] = {} : [query] = {}, [offset] = {}, [limit] = {}",
          result.size.toString, query, offset.getOrElse(0).toString, limit.getOrElse(0).toString)
      logger.debug(LOG_MARKER_USER_GROUP, "getUsers: end : [result] = {}", result)
      result
    }
  }

  /**
   * グループの一覧を取得します。
   * @param param
   * @return
   */
  def getGroups(param: Option[String]) = {
    val query = param match {
      case Some(x) => x.replaceAll("%", "\\\\%").replaceAll("_", "\\\\_") + "%"
      case None => ""
    }

    val g = persistence.Group.g
    val gi = persistence.GroupImage.gi
    DB readOnly { implicit s =>
      withSQL {
        select(g.result.*, gi.result.*)
          .from(persistence.Group as g)
          .innerJoin(persistence.GroupImage as gi)
          .on(sqls.eq(g.id, gi.groupId).and.eq(gi.isPrimary, true).and.isNull(gi.deletedAt))
          .where
          .like(g.name, query)
          .and
          .eq(g.groupType, GroupType.Public)
          .and
          .isNull(g.deletedAt)
          .orderBy(g.name, g.createdAt).desc
          .limit(100)
      }.map(rs => (persistence.Group(g.resultName)(rs), persistence.GroupImage(gi.resultName)(rs))).list().apply
        .map{ x =>
        SuggestData.Group(
          id = x._1.id,
          name = x._1.name,
          image = groupImageDownloadRoot + x._1.id + "/" + x._2.imageId
        )
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
    * @return (条件に該当する) ユーザとグループの一覧
    */
  def getUsersAndGroups(param: Option[String], limit: Option[Int], offset: Option[Int], excludeIds: Seq[String]) = {
    logger.debug(LOG_MARKER_USER_GROUP, "getUsersAndGroups: start : [param] = {}, [offset] = {}, [limit] = {}, [excludeIds] = {}",
        param, offset, limit, excludeIds)
    val query = param match {
      case Some(x) => x.replaceAll("%", "\\\\%").replaceAll("_", "\\\\_") + "%"
      case None => "%"
    }

    DB readOnly { implicit s =>
      val u = persistence.User.u              // TB: users
      val ma = persistence.MailAddress.ma     // TB: mail_addresses
      val g = persistence.Group.g             // TB: groups
      val gi = persistence.GroupImage.gi      // TB: group_images

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
            .isNull(u.deletedAt)
          .union(
            select(g.id, g.name,gi.imageId, sqls"null, null, '2' as type")
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
      }.map(rs => (rs.string("id"),
        rs.string("name"),
        rs.string("image_id"),
        rs.string("fullname"),
        rs.string("organization"),
        rs.int("type"))).list().apply
        .map {x =>
        if(x._6 == SuggestType.User) {
          SuggestData.UserWithType(
            id = x._1,
            name = x._2,
            fullname = x._4,
            organization = x._5,
            image = userImageDownloadRoot + x._1 + "/" + x._3
          )
        } else if (x._6 == SuggestType.Group){
          SuggestData.GroupWithType(
            id = x._1,
            name = x._2,
            image = groupImageDownloadRoot + x._1 + "/" + x._3
          )
        }
      }
      logger.info(LOG_MARKER_USER_GROUP, "getUsersAndGroups: [result size] = {} : [param] = {}, [offset] = {}, [limit] = {}, [excludeIds] = {}",
          result.size.toString, param, offset.getOrElse(0).toString, limit.getOrElse(0).toString, excludeIds)
      logger.debug(LOG_MARKER_USER_GROUP, "getUsersAndGroups: end : [result] = {}", result)
      result
    }
  }

  def getAttributes(query: Option[String]) = {
    val a = persistence.Annotation.a
    DB readOnly { implicit s =>
      val attributes = withSQL {
        select.apply[Any](a.result.*)
          .from(persistence.Annotation as a)
          .where
          .map {sql =>
            query match {
                case Some(x) => sql.like(a.name, x.replaceAll("%", "\\\\%").replaceAll("_", "\\\\_") + "%").and
                case None => sql
              }
          }
          .isNull(a.deletedAt)
          .orderBy(a.name)
          .limit(100)
      }.map(rs => rs.string(a.resultName.name)).list().apply
      attributes
    }
  }

  def getTags() = {
    DB readOnly { implicit s =>
      val t = persistence.Tag.t
      withSQL {
        select
          .from(persistence.Tag as t)
          .where
            .isNull(t.deletedBy)
            .and
            .isNull(t.deletedAt)
          .orderBy(t.tag)
      }.map(persistence.Tag(t.resultName)).list().apply().map { x =>
        TagDetail(x.tag, x.color)
      }
    }
  }

  def getMessage(): String = {
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
