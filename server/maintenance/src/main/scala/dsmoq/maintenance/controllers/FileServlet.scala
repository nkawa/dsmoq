package dsmoq.maintenance.controllers

import scala.util.Success

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
import dsmoq.maintenance.data.file.SearchCondition
import dsmoq.maintenance.services.FileService

/**
 * ファイル処理系画面のサーブレット
 */
class FileServlet extends ScalatraServlet with ScalateSupport with LazyLogging with CsrfTokenSupport {
  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("MAINTENANCE_FILE_LOG")

  before() {
    contentType = "text/html"
  }

  /**
   * CSRFが検出された場合のActionを定義する。
   */
  override def handleForgery() {
    contentType = "text/html"
    val message = "無効なトークンが指定されました。"
    val backUrl = Option(request.getHeader("Referer")).getOrElse("/")
    logger.error(LOG_MARKER, message)
    halt(
      Forbidden(errorPage(message))
    )
  }

  get("/") {
    val condition = SearchCondition.fromMap(params)
    Ok(search(condition))
  }

  post("/apply") {
    val result = for {
      _ <- FileService.applyChange(params, multiParams)
    } yield {
      SeeOther(searchUrl(params))
    }
    resultAs(result) { error =>
      errorPage(error)
    }
  }

  /**
   * 検索画面を作成する。
   *
   * @param condition 検索条件
   * @param error エラー文言
   * @return 検索画面のHTML
   */
  def search(condition: SearchCondition): String = {
    val result = FileService.search(condition)
    ssp(
      "file/index",
      "condition" -> condition,
      "result" -> result,
      "url" -> searchUrl _,
      "csrfKey" -> csrfKey,
      "csrfToken" -> csrfToken
    )
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

  /**
   * 検索画面のURLを作成する。
   *
   * @param params クエリパラメータ
   * @return 検索画面のURL
   */
  def searchUrl(params: Map[String, String]): String = {
    val condition = SearchCondition.fromMap(params)
    url("/", condition.toMap)
  }
}
