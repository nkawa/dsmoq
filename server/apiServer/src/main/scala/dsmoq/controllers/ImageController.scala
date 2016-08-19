package dsmoq.controllers

import java.util.ResourceBundle

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.scalatra.ScalatraServlet
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.exceptions.AccessDeniedException
import dsmoq.exceptions.NotFoundException
import dsmoq.services.ImageService

class ImageController(val resource: ResourceBundle) extends ScalatraServlet with LazyLogging with AuthTrait {

  /**
   * ImageServiceのインスタンス
   */
  val imageService = new ImageService(resource)

  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("IMAGE_LOG")

  before("/*") {
    // TODO session control
  }

  get("/user/:userId/:imageId") {
    val imageId = params("imageId")
    val userId = params("userId")
    logger.info(LOG_MARKER, "Receive user image request, userId={}, imageId={}", userId, imageId)
    getImage(imageService.getUserFile(userId, imageId, None))
  }

  get("/user/:userId/:imageId/:size") {
    val imageId = params("imageId")
    val userId = params("userId")
    val size = params.get("size")
    logger.info(LOG_MARKER, "Receive user image request, userId={}, imageId={}, size={}", userId, imageId, size)
    getImage(imageService.getUserFile(userId, imageId, size))
  }

  get("/datasets/:datasetId/:imageId") {
    val imageId = params("imageId")
    val datasetId = params("datasetId")
    logger.info(LOG_MARKER, "Receive dataset image request, datasetId={}, imageId={}", datasetId, imageId)
    getImage(imageService.getDatasetFile(datasetId, imageId, None, getUserFromSession))
  }

  get("/datasets/:datasetId/:imageId/:size") {
    val imageId = params("imageId")
    val datasetId = params("datasetId")
    val size = params.get("size")
    logger.info(
      LOG_MARKER,
      "Receive dataset image request, datasetId={}, imageId={}, size={}", datasetId, imageId, size
    )
    getImage(imageService.getDatasetFile(datasetId, imageId, size, getUserFromSession))
  }

  get("/groups/:groupId/:imageId") {
    val imageId = params("imageId")
    val groupId = params("groupId")
    logger.info(LOG_MARKER, "Receive group image request, groupId={}, imageId={}", groupId, imageId)
    getImage(imageService.getGroupFile(groupId, imageId, None))
  }

  get("/groups/:groupId/:imageId/:size") {
    val imageId = params("imageId")
    val groupId = params("groupId")
    val size = params.get("size")
    logger.info(
      LOG_MARKER,
      "Receive group image request, groupId={}, imageId={}, size={}", groupId, imageId, size
    )
    getImage(imageService.getGroupFile(groupId, imageId, size))
  }

  private def getImage(result: Try[(java.io.File, String)]) = {
    result match {
      case Success(x) =>
        logger.debug(LOG_MARKER, "getImage succeeded")
        response.setHeader("Content-Disposition", "inline; filename=" + x._2)
        response.setHeader("Content-Type", "application/octet-stream;charset=binary")
        x._1
      case Failure(exp) => {
        logger.error(LOG_MARKER, "getImage failed", exp)
        exp match {
          case _: NotFoundException => {
            // 403 Forbidden
            halt(status = 404, reason = "Not Found", body = "Not Found")
          }
          case _: AccessDeniedException => {
            // 403 Forbidden
            halt(status = 403, reason = "Forbidden", body = "Access Denied")
          }
          case _: Exception => {
            // 500 Internal Server Error
            halt(status = 500, reason = "Internal Server Error", body = "Internal Server Error")
          }
        }
      }
    }
  }
}
