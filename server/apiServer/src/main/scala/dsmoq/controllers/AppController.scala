package dsmoq.controllers

import java.util.ResourceBundle

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.scalatra.ScalatraServlet
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.controllers.ResponseUtil.generateContentDispositionValue
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

  get("/:datasetId/:appId.jnlp") {
    val datasetId = params("datasetId")
    val appId = params("appId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _ <- checkUtil.validUuidForUrl("appId", appId)
      user <- getUser(allowGuest = false)
      jnlp <- datasetService.getAppJnlp(datasetId, appId, user)
    } yield {
      contentType = "application/x-java-jnlp-file"
      response.setDateHeader("Last-Modified", jnlp.lastModified.getMillis)
      response.setHeader("Content-Disposition", generateContentDispositionValue(s"${jnlp.name}.jnlp"))
      jnlp.content
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
      file <- datasetService.getAppFile(datasetId, appId, appVersionId, user)
    } yield {
      contentType = "application/java-archive"
      response.setDateHeader("Last-Modified", file.lastModified.getMillis)
      file.content
    }
    toActionResult(ret)
  }
}
