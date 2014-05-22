package dsmoq.services

import dsmoq.{persistence, AppConf}
import scalikejdbc._
import scala.util.{Success, Failure}
import dsmoq.logic.ImageSaveLogic

object ImageService {
  def getFile(imageId: String, size: Option[String]) = {
    try {
      val f = DB readOnly { implicit s =>
        persistence.Image.find(imageId)
      } match {
        case Some(x) => x
        case None => throw new RuntimeException("data not found.")
      }

      // ファイルサイズ指定が合致すればその画像サイズ
      val fileName = size match {
        case Some(x) => if (ImageSaveLogic.imageSizes.contains(x)) {
          x
        } else {
          ImageSaveLogic.defaultFileName
        }
        case None => ImageSaveLogic.defaultFileName
      }
      val file = new java.io.File(java.nio.file.Paths.get(AppConf.imageDir, f.filePath, fileName).toString)
      if (!file.exists()) throw new RuntimeException("file not found.")
      Success((file, f.name))
    } catch {
      case e: Exception => Failure(e)
    }
  }
}
