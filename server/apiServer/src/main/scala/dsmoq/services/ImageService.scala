package dsmoq.services

import java.util.ResourceBundle

import dsmoq.exceptions._
import dsmoq.persistence.GroupType
import dsmoq.{persistence, AppConf}
import scalikejdbc._
import scala.util.{Success, Failure}
import dsmoq.logic.ImageSaveLogic
import dsmoq.persistence.PostgresqlHelper._

class ImageService(resource: ResourceBundle) {
  def getUserFile(userId: String, imageId: String, size: Option[String]) = {
    try {
      DB readOnly { implicit s =>
        val user = persistence.User.find(userId)
        user match {
          case Some(u) => {
            if (u.imageId != imageId) throw new InputValidationException(Map("imageId" -> "imageId is not related to target"))
          }
          case None => throw new NotAuthorizedException
        }
      }
      Success(getFile(imageId, size))
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def getDatasetFile(datasetId: String, imageId: String, size: Option[String], user: User) = {
    try {
      DB readOnly { implicit s =>
        (for {
          groups <- Some(getJoinedGroups(user))
          permission <- getPermission(datasetId, groups)
        } yield {
          if (permission == 0) throw new NotAuthorizedException
          if (!isRelatedToDataset(datasetId, imageId)) throw new InputValidationException(Map("imageId" -> "imageId is not related to target"))
          getFile(imageId, size)
        }).map(x => Success(x)).getOrElse(Failure(new NotAuthorizedException()))
      }
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def getGroupFile(groupId: String, imageId: String, size: Option[String]) = {
    try {
      DB readOnly { implicit s =>
        if (!isRelatedToGroup(groupId, imageId)) throw new InputValidationException(Map("imageId" -> "imageId is not related to target"))
        Success(getFile(imageId, size))
      }
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def getFile(imageId: String, size: Option[String]) = {
    val f = DB readOnly { implicit s =>
      persistence.Image.find(imageId)
    } match {
      case Some(x) => x
      case None => throw new RuntimeException("data not found.")
    }

    // ファイルサイズ指定が合致すればその画像サイズ
    val fileName = size match {
      case Some(x) => if (ImageSaveLogic.imageSizes.contains(x.toInt)) {
        x
      } else {
        ImageSaveLogic.defaultFileName
      }
      case None => ImageSaveLogic.defaultFileName
    }
    val file = new java.io.File(java.nio.file.Paths.get(AppConf.imageDir, f.filePath, fileName).toString)
    if (!file.exists()) throw new RuntimeException("file not found.")
    (file, f.name)
  }

  def getJoinedGroups(user: User)(implicit s: DBSession): Seq[String] = {
    if (user.isGuest) {
      Seq.empty
    } else {
      val g = persistence.Group.syntax("g")
      val m = persistence.Member.syntax("m")
      withSQL {
        select(g.id)
          .from(persistence.Group as g)
          .innerJoin(persistence.Member as m).on(m.groupId, g.id)
          .where
          .eq(m.userId, sqls.uuid(user.id))
          .and
          .isNull(g.deletedAt)
          .and
          .isNull(m.deletedAt)
      }.map(_.string("id")).list().apply()
    }
  }

  private def getPermission(id: String, groups: Seq[String])(implicit s: DBSession) = {
    val o = persistence.Ownership.syntax("o")
    val g = persistence.Group.g
    val permissions = withSQL {
      select(o.result.accessLevel, g.result.groupType)
        .from(persistence.Ownership as o)
        .innerJoin(persistence.Group as g).on(o.groupId, g.id)
        .where
        .eq(o.datasetId, sqls.uuid(id))
        .and
        .inUuid(o.groupId, Seq.concat(groups, Seq(AppConf.guestGroupId)))
    }.map(rs => (rs.int(o.resultName.accessLevel), rs.int(g.resultName.groupType))).list().apply
    // 上記のSQLではゲストユーザーは取得できないため、別途取得する必要がある
    val guestPermission = (getGuestAccessLevel(id), GroupType.Personal)
    // Provider権限のGroupはWriteできない
    (guestPermission :: permissions) match {
      case x :: xs => Some((guestPermission :: permissions).map(x => if (x._1 == 3 && x._2 == GroupType.Public) { 2 } else { x._1 }).max)
      case Nil => None
    }
  }

  private def isRelatedToDataset(datasetId: String, imageId: String)(implicit s: DBSession): Boolean = {
    val di = persistence.DatasetImage.di
    val count = withSQL {
      select(sqls"count(1)")
        .from(persistence.DatasetImage as di)
        .where
          .eqUuid(di.imageId, imageId)
          .and
          .eqUuid(di.datasetId, datasetId)
    }.map(_.long(1)).single.apply().get
    count > 0
  }

  private def isRelatedToGroup(groupId: String, imageId: String)(implicit s: DBSession): Boolean = {
    val gi = persistence.GroupImage.gi
    val count = withSQL {
      select(sqls"count(1)")
        .from(persistence.GroupImage as gi)
        .where
        .eqUuid(gi.imageId, imageId)
        .and
        .eqUuid(gi.groupId, groupId)
    }.map(_.long(1)).single.apply().get
    count > 0
  }

  private def getGuestAccessLevel(datasetId: String)(implicit s: DBSession) = {
    val o = persistence.Ownership.syntax("o")
    withSQL {
      select(o.result.accessLevel)
        .from(persistence.Ownership as o)
        .where
        .eq(o.datasetId, sqls.uuid(datasetId))
        .and
        .eq(o.groupId, sqls.uuid(AppConf.guestGroupId))
        .and
        .isNull(o.deletedAt)
    }.map(_.int(o.resultName.accessLevel)).single().apply().getOrElse(0)
  }
}
