package dsmoq.controllers

import org.scalatra.{NotFound, ScalatraServlet}
import dsmoq.services.ImageService
import scala.util.{Success, Failure}

class ImageController extends ScalatraServlet with SessionTrait {
  before("/*") {
    // TODO session control
  }

  get("/:imageId") {
    val imageId = params("imageId")
    getImage(imageId, None)
  }

  get("/:imageId/:size") {
    val imageId = params("imageId")
    val size = params.get("size")
    getImage(imageId, size)
  }

  private def getImage(imageId: String, size: Option[String]) = {
    ImageService.getFile(imageId, size) match {
      case Success(x) =>
        response.setHeader("Content-Disposition", "inline; filename=" + x._2)
        x._1
      case Failure(e) =>
        NotFound("file not found.")
    }
  }
}
