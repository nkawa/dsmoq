package dsmoq.controllers

import org.scalatra.ScalatraServlet
import dsmoq.services.OAuthService
import scala.util.{Failure, Success}

class OAuthController extends ScalatraServlet with SessionTrait {
  get("/*") {
    throw new Exception("err")
  }

  get("/signin_google") {
    val location = params("location")
    redirect(OAuthService.getAuthenticationUrl(location))
  }

  get ("/callback") {
    val userRedirectUri = params.get("state") match {
      case Some(x) => x
      case None => "/"
    }

    params.get("code") match {
      case Some(x) =>
        val authenticationCode = x

        (for {
          result <- OAuthService.loginWithGoogle(authenticationCode)
        } yield {
          result
        }) match {
          case Success(y) =>
            clearSession()
            setSignedInUser(y)
            redirect(userRedirectUri)
          case Failure(e) =>
            // 処理エラー時
            clearSession()
            redirect(userRedirectUri)
        }
      case None =>
        // 連携拒否時処理
        clearSession()
        redirect(userRedirectUri)
    }
  }
}
