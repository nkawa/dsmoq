package dsmoq.controllers

import java.util.ResourceBundle

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.scalatra.ScalatraServlet
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.controllers.ResponseUtil.toActionResult
import dsmoq.logic.CheckUtil
import dsmoq.services.DatasetService

class AppController(val resource: ResourceBundle) extends ScalatraServlet with LazyLogging with AuthTrait {
  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("APP_LOG")

  /**
   * DatasetServiceのインスタンス
   */
  val datasetService = new DatasetService(resource)

  /**
   * CheckUtilのインスタンス
   */
  val checkUtil = new CheckUtil(resource)

  /**
   * HTTPレスポンスのLast-Modifiedヘッダ名
   */
  val LAST_MODIFIED_HEADER = "Last-Modified"

  get("/:datasetId/:appId.jnlp") {
    val datasetId = params("datasetId")
    val appId = params("appId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _ <- checkUtil.validUuidForUrl("appId", appId)
      user <- getUser(allowGuest = false)
      (jnlp, lastModified) <- datasetService.getAppJnlp(datasetId, appId, user)
    } yield {
      contentType = "application/x-java-jnlp-file"
      response.setDateHeader(LAST_MODIFIED_HEADER, lastModified.getMillis)
      jnlp
    }
    toActionResult(ret)
  }

  get("/:datasetId/:appId/:appVersionId.jar") {
    val datasetId = params("datasetId")
    val appId = params("appId")
    val appVersionId = params("appVersionId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _ <- checkUtil.validUuidForUrl("appId", appId)
      _ <- checkUtil.validUuidForUrl("appVersionId", appVersionId)
      user <- getUser(allowGuest = false)
      (stream, lastModified) <- datasetService.getAppFile(datasetId, appId, appVersionId, user)
    } yield {
      contentType = "application/java-archive"
      response.setDateHeader(LAST_MODIFIED_HEADER, lastModified.getMillis)
      stream
    }
    toActionResult(ret)
  }
}
