package dsmoq.controllers

import org.scalatra.{NotFound, ScalatraServlet}
import dsmoq.services.{ImageService, User}
import scala.util.{Try, Success, Failure}
import dsmoq.exceptions._

class ImageController extends ScalatraServlet with SessionTrait {
  before("/*") {
    // TODO session control
  }

  get("/user/:userId/:imageId") {
    val imageId = params("imageId")
    val userId = params("userId")
    getImage(ImageService.getUserFile(userId, imageId, None))
  }

  get("/user/:userId/:imageId/:size") {
    val imageId = params("imageId")
    val userId = params("userId")
    val size = params.get("size")
    getImage(ImageService.getUserFile(userId, imageId, size))
  }

  get("/datasets/:datasetId/:imageId") {
    val imageId = params("imageId")
    val datasetId = params("datasetId")
    getImage(ImageService.getDatasetFile(datasetId, imageId, None, currentUser))
  }

  get("/datasets/:datasetId/:imageId/:size") {
    val imageId = params("imageId")
    val datasetId = params("datasetId")
    val size = params.get("size")
    getImage(ImageService.getDatasetFile(datasetId, imageId, size, currentUser))
  }

  get("/groups/:groupId/:imageId") {
    val imageId = params("imageId")
    val groupId = params("groupId")
    getImage(ImageService.getGroupFile(groupId, imageId, None))
  }

  get("/groups/:groupId/:imageId/:size") {
    val imageId = params("imageId")
    val groupId = params("groupId")
    val size = params.get("size")
    getImage(ImageService.getGroupFile(groupId, imageId, size))
  }
  private def currentUser: User = {
    signedInUser match {
      case SignedInUser(user) => user
      case GuestUser(user) => user
    }
  }
  private def getImage(result: Try[(java.io.File, String)]) = {
    result match {
      case Success(x) =>
        response.setHeader("Content-Disposition", "inline; filename=" + x._2)
        response.setHeader("Content-Type", "application/octet-stream;charset=binary")
        x._1
      case Failure(exp) => exp match {
        case _: NotFoundException => halt(status = 404, reason = "NotFound", body = "NotFound")
        case _: AccessDeniedException => halt(status = 403, reason = "Forbidden", body = "AccessDenied")
        case _: Exception => halt(status = 500, reason = "InternalServerError", body = "InternalServerError")
      }
    }
  }
}
