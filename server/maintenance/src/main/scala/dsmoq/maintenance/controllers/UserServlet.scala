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
import dsmoq.maintenance.data.user.SearchCondition
import dsmoq.maintenance.data.user.UpdateParameter
import dsmoq.maintenance.services.UserService
import dsmoq.maintenance.services.ErrorDetail

/**
 * ユーザ処理系画面のサーブレット
 */
class UserServlet extends ScalatraServlet with ScalateSupport with LazyLogging with CsrfTokenSupport {
  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("MAINTENANCE_USER_LOG")

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
    val condition = SearchCondition.fromMap(params)
    Ok(search(condition))
  }

  post("/apply") {
    val condition = SearchCondition.fromMap(params)
    val result = for {
      _ <- UserService.updateDisabled(UpdateParameter.fromMap(multiParams))
    } yield {
      SeeOther(searchUrl(condition.toMap))
    }
    resultAs(result) {
      case (error, details) =>
        errorPage(error, details)
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
    val result = UserService.search(condition)
    ssp(
      "user/index",
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
   * @param details エラーの詳細
   * @return エラーページのHTML
   */
  def errorPage(error: String, details: Seq[ErrorDetail] = Seq.empty): String = {
    val backUrl = Option(request.getHeader("Referer")).getOrElse("/")
    ssp(
      "util/error",
      "error" -> error,
      "details" -> details,
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
    url("/", params)
  }
}
