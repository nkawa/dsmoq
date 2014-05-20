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
    ImageService.getFile(imageId) match {
      case Success(x) =>
        response.setHeader("Content-Disposition", "inline; filename=" + x._2)
        x._1
      case Failure(e) =>
        NotFound("file not found.")
    }
  }
}
