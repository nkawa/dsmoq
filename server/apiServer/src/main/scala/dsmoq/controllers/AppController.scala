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
import dsmoq.exceptions.NotFoundException
import dsmoq.logic.CheckUtil
import dsmoq.services.AccountService
import dsmoq.services.DatasetService
import dsmoq.services.User

class AppController(val resource: ResourceBundle) extends ScalatraServlet with LazyLogging {
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

  get("/:userId/:datasetId/:appId.jnlp") {
    val userId = params("userId")
    val datasetId = params("datasetId")
    val appId = params("appId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("userId", userId)
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _ <- checkUtil.validUuidForUrl("appId", appId)
      user <- getUser(userId)
      jnlp <- datasetService.getAppJnlp(datasetId, appId, user)
    } yield {
      contentType = "application/x-java-jnlp-file"
      response.setDateHeader("Last-Modified", jnlp.lastModified.getMillis)
      response.setHeader("Content-Disposition", generateContentDispositionValue(s"${jnlp.name}.jnlp"))
      jnlp.content
    }
    toActionResult(ret)
  }

  get("/:userId/:datasetId/:appId.jar") {
    val userId = params("userId")
    val datasetId = params("datasetId")
    val appId = params("appId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("userId", userId)
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _ <- checkUtil.validUuidForUrl("appId", appId)
      user <- getUser(userId)
      file <- datasetService.getAppFile(datasetId, appId, user)
    } yield {
      contentType = "application/java-archive"
      response.setDateHeader("Last-Modified", file.lastModified.getMillis)
      file.content
    }
    toActionResult(ret)
  }

  /**
   * 指定したIDのユーザを取得します。
   *
   * @param id ユーザID
   * @return
   *   Success(User) 取得したユーザ
   *   Failure(NotFoundException) 取得したユーザ
   */
  private def getUser(id: String): Try[User] = {
    accountService.getUser(id).filter(!_.isDisabled)
      .map(Success.apply)
      .getOrElse(Failure(new NotFoundException))
  }
}
