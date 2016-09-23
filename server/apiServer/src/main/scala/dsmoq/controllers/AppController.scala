package dsmoq.controllers

import java.util.ResourceBundle

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.json4s.DefaultFormats
import org.json4s.Formats
import org.scalatra.NotFound
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.controllers.ResponseUtil.generateContentDispositionValue
import dsmoq.controllers.ResponseUtil.toActionResult
import dsmoq.exceptions.NotFoundException
import dsmoq.logic.CheckUtil
import dsmoq.services.AccountService
import dsmoq.services.DatasetService
import dsmoq.services.User

class AppController(
  val resource: ResourceBundle
) extends ScalatraServlet with JacksonJsonSupport with LazyLogging {

  protected implicit val jsonFormats: Formats = DefaultFormats

  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("APP_LOG")

  /**
   * DatasetServiceのインスタンス
   */
  val datasetService = new DatasetService(resource)

  /**
   * AccountServiceのインスタンス
   */
  val accountService = new AccountService(resource)

  /**
   * CheckUtilのインスタンス
   */
  val checkUtil = new CheckUtil(resource)

  before() {
    contentType = formats("json")
  }

  get("/*") {
    NotFound(AjaxResponse("NotFound")) // 404
  }

  get("/:userId/:datasetId.jnlp") {
    val userId = params("userId")
    val datasetId = params("datasetId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("userId", userId)
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      jnlp <- datasetService.getAppJnlp(datasetId, userId)
    } yield {
      contentType = "application/x-java-jnlp-file"
      response.setDateHeader("Last-Modified", jnlp.lastModified.getMillis)
      response.setHeader("Content-Disposition", generateContentDispositionValue(s"${jnlp.name}.jnlp"))
      jnlp.content
    }
    toActionResult(ret)
  }

  get("/:userId/:datasetId.jar") {
    val userId = params("userId")
    val datasetId = params("datasetId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("userId", userId)
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      file <- datasetService.getAppFile(datasetId, userId)
    } yield {
      contentType = "application/java-archive"
      response.setDateHeader("Last-Modified", file.lastModified.getMillis)
      file.content
    }
    toActionResult(ret)
  }
}
