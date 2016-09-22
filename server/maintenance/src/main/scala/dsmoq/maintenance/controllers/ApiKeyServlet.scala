package dsmoq.maintenance.controllers

import org.scalatra.CsrfTokenSupport
import org.scalatra.Forbidden
import org.scalatra.Ok
import org.scalatra.ScalatraServlet
import org.scalatra.SeeOther
import org.scalatra.scalate.ScalateSupport
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.maintenance.AppConfig
import dsmoq.maintenance.controllers.ResponseUtil.resultAs
import dsmoq.maintenance.data.apikey.AddParameter
import dsmoq.maintenance.data.apikey.DisableParameter
import dsmoq.maintenance.services.ApiKeyService

/**
 * APIキー処理系画面のサーブレット
 */
class ApiKeyServlet extends ScalatraServlet with ScalateSupport with LazyLogging with CsrfTokenSupport {
  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("MAINTENANCE_APIKEY_LOG")

  before() {
    contentType = "text/html"
  }

  /**
   * CSRFが検出された場合のActionを定義する。
   */
  override def handleForgery() {
    contentType = "text/html"
    val message = "無効なトークンが指定されました。"
    logger.error(LOG_MARKER, message)
    halt(
      Forbidden(errorPage(message))
    )
  }

  get("/") {
    Ok(list())
  }

  post("/apply") {
    val result = for {
      _ <- ApiKeyService.disable(DisableParameter.fromMap(params))
    } yield {
      SeeOther(url("/"))
    }
    resultAs(result) { error =>
      errorPage(error)
    }
  }

  /**
   * 一覧画面を作成する。
   *
   * @return 一覧画面のHTML
   */
  def list(): String = {
    val keys = ApiKeyService.list()
    ssp(
      "apikey/index",
      "csrfKey" -> csrfKey,
      "csrfToken" -> csrfToken,
      "keys" -> keys
    )
  }

  get("/add") {
    ssp(
      "apikey/add",
      "csrfKey" -> csrfKey,
      "csrfToken" -> csrfToken
    )
  }

  post("/add/apply") {
    val result = for {
      _ <- ApiKeyService.add(AddParameter.fromMap(params))
    } yield {
      SeeOther(url("/"))
    }
    resultAs(result) { error =>
      errorPage(error)
    }
  }

  /**
   * エラーページを作成する。
   *
   * @param error エラーメッセージ
   * @return エラーページのHTML
   */
  def errorPage(error: String): String = {
    val backUrl = Option(request.getHeader("Referer")).getOrElse("/")
    ssp(
      "util/error",
      "error" -> error,
      "backUrl" -> backUrl
    )
  }
}
