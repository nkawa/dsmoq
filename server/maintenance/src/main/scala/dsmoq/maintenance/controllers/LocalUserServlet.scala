package dsmoq.maintenance.controllers

import com.typesafe.scalalogging.LazyLogging
import dsmoq.maintenance.controllers.ResponseUtil.resultAs
import dsmoq.maintenance.data.localuser.CreateParameter
import dsmoq.maintenance.services.{ ErrorDetail, LocalUserService }
import org.scalatra._
import org.scalatra.scalate.ScalateSupport
import org.scalatra.servlet.FileUploadSupport
import org.slf4j.MarkerFactory

/**
 * ローカルユーザー作成画面のサーブレット
 */
class LocalUserServlet
  extends ScalatraServlet with ScalateSupport with LazyLogging with CsrfTokenSupport with FileUploadSupport {

  /**
   * ログマーカー
   */
  private val LOG_MARKER = MarkerFactory.getMarker("MAINTENANCE_LOCALUSER_LOG")

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
    Ok(ssp(
      "/localuser/index",
      "csrfKey" -> csrfKey,
      "csrfToken" -> csrfToken
    ))
  }

  post("/apply") {
    val result = for {
      _ <- LocalUserService.create(CreateParameter.fromMap(params))
    } yield {
      SeeOther(url("/"))
    }
    resultAs(result) {
      case (error, details) =>
        errorPage(error, details)
    }
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

}
