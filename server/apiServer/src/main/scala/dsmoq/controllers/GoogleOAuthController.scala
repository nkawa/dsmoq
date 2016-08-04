package dsmoq.controllers

import java.util.ResourceBundle

import com.typesafe.scalalogging.LazyLogging
import org.scalatra.ScalatraServlet
import org.slf4j.MarkerFactory
import scala.util.{Failure, Success}

import dsmoq.services.GoogleAccountService

class GoogleOAuthController(val resource: ResourceBundle) extends ScalatraServlet with LazyLogging with AuthTrait {
  /**
   * GoogleAccountServiceのインスタンス
   */
  val googleAccountService = new GoogleAccountService(resource)

  /**
    * ログマーカー
    */
  val LOG_MARKER = MarkerFactory.getMarker("GOOGLE_OAUTH_LOG")

  get("/*") {
    throw new Exception("err")
  }

  get("/signin") {
    val location = params("location")
    logger.info(LOG_MARKER, "Receive signin request, location={}", location)
    redirect(googleAccountService.getOAuthUrl(location))
  }

  get ("/oauth2callback") {
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
            redirect(userRedirectUri)
          case Failure(e) =>
            logger.error(LOG_MARKER, "login failed", e)
            clearSession()
            redirect(userRedirectUri)
        }
      case None =>
        logger.error(LOG_MARKER, "login failed - no code param")
        clearSession()
        redirect(userRedirectUri)
    }
  }
}
