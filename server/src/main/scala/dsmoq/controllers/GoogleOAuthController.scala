package dsmoq.controllers

import org.scalatra.ScalatraServlet
import dsmoq.services.GoogleAccountService
import scala.util.{Failure, Success}

class GoogleOAuthController extends ScalatraServlet with SessionTrait {
  get("/*") {
    throw new Exception("err")
  }

  get("/signin") {
    val location = params("location")
    redirect(GoogleAccountService.getOAuthUrl(location))
  }

  get ("/callback") {
    val userRedirectUri = params.get("state") match {
      case Some(x) => x
      case None => "/"
    }

    params.get("code") match {
      case Some(code) =>
        GoogleAccountService.loginWithGoogle(code) match {
          case Success(y) =>
            clearSession()
            setSignedInUser(y)
            redirect(userRedirectUri)
          case Failure(e) =>
            clearSession()
            redirect(userRedirectUri)
        }
      case None =>
        clearSession()
        redirect(userRedirectUri)
    }
  }
}
