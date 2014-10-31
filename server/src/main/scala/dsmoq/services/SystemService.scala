package dsmoq.services

import java.util.UUID

import dsmoq.{AppConf, persistence}
import dsmoq.persistence.{SuggestType, GroupType}
import dsmoq.services.json.SuggestData
import org.joda.time.DateTime
import scalikejdbc._
import scala.util.{Failure, Success, Try}

object SystemService {
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

  def getUsers(query: Option[String], limit: Option[Int], offset: Option[Int]) = {
    DB readOnly { implicit s =>
      val u = persistence.User.u
      withSQL {
        select.all[persistence.User](u)
          .from(persistence.User as u)
          .where
          .isNull(u.deletedAt)
          .map{sql =>
            query match {
              case Some(x) =>
                sql.and.like(u.name, x + "%").or.like(u.fullname, x + "%")
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
            image = AppConf.imageDownloadRoot + x.imageId
          )
      }
    }
  }

  /**
   * グループの一覧を取得します。
   * @param param
   * @return
   */
  def getGroups(param: Option[String]) = {
    val query = param match {
      case Some(x) => x + "%"
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
          image = AppConf.imageDownloadRoot + x._2.imageId
        )
      }
    }
  }

  def getUsersAndGroups(param: Option[String]) = {
    val query = param match {
      case Some(x) => x + "%"
      case None => ""
    }

    DB readOnly { implicit s =>
      val u = persistence.User.u
      val g = persistence.Group.g
      val gi = persistence.GroupImage.gi

      withSQL {
        select(u.id, u.name, u.imageId, u.fullname, u.organization, sqls"'1' as type")
          .from(persistence.User as u)
          .where
          .like(u.name, query)
          .or
          .like(u.fullname, query)
          .and
          .isNull(u.deletedAt)
          .union(
            select(g.id, g.name,gi.imageId, sqls"null, null, '2' as type")
              .from(persistence.Group as g)
              .innerJoin(persistence.GroupImage as gi)
              .on(sqls.eq(g.id, gi.groupId).and.eq(gi.isPrimary, true).and.isNull(gi.deletedAt))
              .where
              .like(g.name, query)
              .and
              .eq(g.groupType, GroupType.Public)
              .and
              .isNull(g.deletedAt)
          )
          .orderBy(sqls"name")
          .limit(100)
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
            image = AppConf.imageDownloadRoot + x._3
          )
        } else if (x._6 == SuggestType.Group){
          SuggestData.GroupWithType(
            id = x._1,
            name = x._2,
            image = AppConf.imageDownloadRoot + x._3
          )
        }
      }
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
                case Some(x) => sql.like(a.name, x + "%").and
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
}