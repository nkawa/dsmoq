package dsmoq.controllers

import org.scalatra.ScalatraServlet
import dsmoq.services.data.User
import scala.util.{Try, Failure, Success}

trait SessionTrait extends ScalatraServlet {
  private val SessionKey = "user"
  val sessionId = "JSESSIONID"

  def getUserInfoFromSession(): Try[User] = {
    sessionOption match {
      case Some(_) => session.get(SessionKey) match {
        case Some(x) => Success(x.asInstanceOf[User])
        case None =>
          clearSession()
          clearSessionCookie()
          Success(User("", "", "", "", "", "http://xxxx", "", "", true, false))
      }
      case None =>
        clearSessionCookie()
        Success(User("", "", "", "", "", "http://xxxx", "", "", true, false))
    }
  }

  def setUserInfoToSession(user: User) {
    session.setAttribute(SessionKey, user)
  }

  def clearSession() {
    // sessionを参照すると新規sessionが作成されてしまうため、sessionOptionで存在チェック
    sessionOption match {
      case Some(_) => session.invalidate()
      case None => // do nothing
    }
  }

  def isValidSession() = {
    sessionOption match {
      case Some(_) => true
      case None => false
    }
  }

  def clearSessionCookie() {
    cookies.delete(sessionId)
  }
}

trait SessionUserInfo {
  val userInfo: User
}