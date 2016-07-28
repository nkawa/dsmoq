package dsmoq.controllers

import java.util.ResourceBundle

import org.scalatra.ScalatraServlet
import dsmoq.services.{AuthService, GoogleAccountService}
import scala.util.{Failure, Success}

class GoogleOAuthController(resource: ResourceBundle) extends ScalatraServlet {
  
  /**
   * AuthServiceのインスタンス
   */
  val authService = new AuthService(resource, this)

  /**
   * GoogleAccountServiceのインスタンス
   */
  val googleAccountService = new GoogleAccountService(resource)

  get("/*") {
    throw new Exception("err")
  }

  get("/signin") {
    val location = params("location")
    redirect(googleAccountService.getOAuthUrl(location))
  }

  get ("/oauth2callback") {
    val userRedirectUri = params.get("state") match {
      case Some(x) => x
      case None => "/"
    }

    params.get("code") match {
      case Some(code) =>
        googleAccountService.loginWithGoogle(code) match {
          case Success(y) =>
            authService.clearSession()
            authService.updateSessionUser(y)
            redirect(userRedirectUri)
          case Failure(e) =>
            authService.clearSession()
            redirect(userRedirectUri)
        }
      case None =>
        authService.clearSession()
        redirect(userRedirectUri)
    }
  }
}
