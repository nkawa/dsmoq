package dsmoq.controllers

import java.util.ResourceBundle

import org.scalatra.{NotFound, ScalatraServlet}
import dsmoq.services.{AuthService, ImageService, User}
import scala.util.{Try, Success, Failure}
import dsmoq.exceptions.{AccessDeniedException, NotFoundException}

class ImageController(resource: ResourceBundle) extends ScalatraServlet {

  /**
   * AuthServiceのインスタンス
   */
  val authService = new AuthService(resource, this)

  /**
   * ImageServiceのインスタンス
   */
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
    getImage(imageService.getDatasetFile(datasetId, imageId, None, authService.getUserFromSession))
  }

  get("/datasets/:datasetId/:imageId/:size") {
    val imageId = params("imageId")
    val datasetId = params("datasetId")
    val size = params.get("size")
    getImage(imageService.getDatasetFile(datasetId, imageId, size, authService.getUserFromSession))
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

  private def getImage(result: Try[(java.io.File, String)]) = {
    result match {
      case Success(x) =>
        response.setHeader("Content-Disposition", "inline; filename=" + x._2)
        response.setHeader("Content-Type", "application/octet-stream;charset=binary")
        x._1
      case Failure(exp) => exp match {
        case _: NotFoundException => halt(status = 404, reason = "Not Found", body = "Not Found") // 403 Forbidden
        case _: AccessDeniedException => halt(status = 403, reason = "Forbidden", body = "Access Denied") // 403 Forbidden
        case _: Exception => halt(status = 500, reason = "Internal Server Error", body = "Internal Server Error") // 500 Internal Server Error
      }
    }
  }
}
