package dsmoq.controllers

import java.util.ResourceBundle

import org.scalatra.{NotFound, ScalatraServlet}
import dsmoq.services.{ImageService, User}
import scala.util.{Try, Success, Failure}
import dsmoq.exceptions._

class ImageController(resource: ResourceBundle) extends ScalatraServlet with SessionTrait {

  val imageService = new ImageService(resource)

  before("/*") {
    // TODO session control
  }

  get("/user/:userId/:imageId") {
    val imageId = params("imageId")
    val userId = params("userId")
    getImage(imageService.getUserFile(userId, imageId, None))
  }

  get("/user/:userId/:imageId/:size") {
    val imageId = params("imageId")
    val userId = params("userId")
    val size = params.get("size")
    getImage(imageService.getUserFile(userId, imageId, size))
  }

  get("/datasets/:datasetId/:imageId") {
    val imageId = params("imageId")
    val datasetId = params("datasetId")
    getImage(imageService.getDatasetFile(datasetId, imageId, None, currentUser))
  }

  get("/datasets/:datasetId/:imageId/:size") {
    val imageId = params("imageId")
    val datasetId = params("datasetId")
    val size = params.get("size")
    getImage(imageService.getDatasetFile(datasetId, imageId, size, currentUser))
  }

  get("/groups/:groupId/:imageId") {
    val imageId = params("imageId")
    val groupId = params("groupId")
    getImage(imageService.getGroupFile(groupId, imageId, None))
  }

  get("/groups/:groupId/:imageId/:size") {
    val imageId = params("imageId")
    val groupId = params("groupId")
    val size = params.get("size")
    getImage(imageService.getGroupFile(groupId, imageId, size))
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
        case e: InputValidationException => halt(status = 403, reason = "imageId is not related to target", body="imageId is not related to target")
        case e: NotAuthorizedException => halt(status = 403, reason = "Forbidden", body="Forbidden")
        case e: RuntimeException => NotFound(e.getMessage)
      }
    }
  }
}
