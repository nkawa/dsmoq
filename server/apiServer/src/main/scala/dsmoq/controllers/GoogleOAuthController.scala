package dsmoq.controllers

import java.util.ResourceBundle

import scala.util.Failure
import scala.util.Success

import org.scalatra.Found
import org.scalatra.ScalatraServlet
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.exceptions.AccessDeniedException
import dsmoq.services.GoogleAccountService

/**
 * /google_oauthにマッピングされるサーブレットクラス。
 * Google OAuthでのログイン、リダイレクト機能を提供する。
 *
 * @param resource リソースバンドル
 */
class GoogleOAuthController(val resource: ResourceBundle) extends ScalatraServlet with LazyLogging with AuthTrait {
  /**
   * GoogleAccountServiceのインスタンス
   */
  val googleAccountService = new GoogleAccountService(resource)

  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("GOOGLE_OAUTH_LOG")

  // いずれにもマッチしないGETリクエスト
  get("/*") {
    throw new Exception("err")
  }

  // Google Oauthログインリクエスト起点
  get("/signin") {
    val location = params("location")
    logger.info(LOG_MARKER, "Receive signin request, location={}", location)
    Found(googleAccountService.getOAuthUrl(location))
  }

  // Google Oauthからのコールバック
  get("/oauth2callback") {
    logger.info(LOG_MARKER, "Receive oauth2callback request")

    val userRedirectUri = params.get("state") match {
      case Some(x) => x
      case None => "/"
    }

    params.get("code") match {
      case Some(code) =>
        googleAccountService.loginWithGoogle(code) match {
          case Success(y) =>
            logger.info(LOG_MARKER, "login succeeded")
            clearSession()
            updateSessionUser(y)
            Found(userRedirectUri)
          case Failure(e) =>
            logger.error(LOG_MARKER, "login failed", e)
            clearSession()
            e match {
              case e: AccessDeniedException if e.user.isDefined => {
                cookies.set("user.disabled", "true")
              }
              case _ =>
            }
            Found("/")
        }
      case None =>
        logger.error(LOG_MARKER, "login failed - no code param")
        clearSession()
        Found(userRedirectUri)
    }
  }
}
