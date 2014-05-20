package dsmoq.services

import dsmoq.{persistence, AppConf}
import scalikejdbc._
import scala.util.{Success, Failure}

object ImageService {
  def getFile(imageId: String) = {
    // FIXME imageIdがperset_imageの時の処理
    try {
      val f = DB readOnly { implicit s =>
        persistence.Image.find(imageId)
      } match {
        case Some(x) => x
        case None => throw new RuntimeException("data not found.")
      }

      val file = new java.io.File(java.nio.file.Paths.get(AppConf.imageDir, imageId).toString)
      if (!file.exists()) throw new RuntimeException("file not found.")
      Success((file, f.name))
    } catch {
      case e: Exception => Failure(e)
    }
  }
}
