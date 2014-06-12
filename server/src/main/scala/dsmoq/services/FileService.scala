package dsmoq.services

import dsmoq.persistence.AccessLevel
import scalikejdbc._, SQLInterpolation._
import dsmoq.{AppConf, persistence}
import dsmoq.persistence.PostgresqlHelper._
import scala.util.{Failure, Success}
import java.nio.file.Paths
import dsmoq.services.data.User
import dsmoq.exceptions.NotFoundException

object FileService {
  def getFile(datasetId: String, fileId: String, user: User) = {
    try {
      val fileInfo = DB readOnly { implicit s =>
        // 権限によりダウンロード可否の決定
        val permission = if (user.isGuest) {
          DatasetService.getGuestAccessLevel(datasetId)
        } else {
          val groups = DatasetService.getJoinedGroups(user)
          DatasetService.getPermission(datasetId, groups).getOrElse(AccessLevel.Deny)
        }
        if (permission < AccessLevel.AllowRead) {
          throw new RuntimeException("access denied")
        }

        // datasetが削除されていないか
        if (!isValidDataset(datasetId)) throw new NotFoundException

        val file = persistence.File.find(fileId)
        val fh = persistence.FileHistory.syntax("fh")
        val filePath = withSQL {
          select(fh.result.filePath)
            .from(persistence.FileHistory as fh)
            .where
            .eq(fh.fileId, sqls.uuid(fileId))
            .and
            .isNull(fh.deletedAt)
        }.map(rs => rs.string(fh.resultName.filePath)).single().apply
        file match {
          case Some(f) => (f, filePath.get)
          case None => throw new RuntimeException("data not found.")
        }
      }

      val filePath = Paths.get(AppConf.fileDir, fileInfo._2).toFile
      if (!filePath.exists()) throw new RuntimeException("file not found")

      val file = new java.io.File(filePath.toString)
      Success((file, fileInfo._1.name))
    } catch {
      case e: Exception => Failure(e)
    }
  }

  private def isValidDataset(datasetId: String)(implicit s: DBSession) = {
    val d = persistence.Dataset.syntax("d")
    withSQL {
      select(d.result.*)
        .from(persistence.Dataset as d)
        .where
        .eq(d.id, sqls.uuid(datasetId))
        .and
        .isNull(d.deletedAt)
    }.map(persistence.Dataset(d.resultName)).single().apply() match {
      case Some(x) => true
      case None => false
    }
  }
}