package dsmoq.controllers

import dsmoq.exceptions.NotAuthorizedException
import dsmoq.services.User
import org.scalatra.ScalatraServlet
import scala.util.{Try, Failure, Success}

trait SessionTrait extends ScalatraServlet {
  private val SessionKey = "user"
  private val sessionId = "JSESSIONID"

  def isValidSession() = {
    sessionOption.map(_ => true)
      .getOrElse(cookies.get(sessionId).map(_ => true).getOrElse(false))
  }

  def currentUser: User = {
    signedInUser match {
      case Success(x) => x
      case Failure(_) => guestUser
    }
  }

  def signedInUser: Try[User] = {
    sessionOption match {
      case Some(_) => session.get(SessionKey) match {
        case Some(x) =>
          Success(x.asInstanceOf[User])
        case None =>
          clearSession()
          Failure(new NotAuthorizedException())
      }
      case None =>
        clearSession()
        Failure(new NotAuthorizedException())
    }
  }

  def setSignedInUser(x: User) = {
    session.setAttribute(SessionKey, x)
  }

  def guestUser = {
    User("", "", "", "", "", "http://xxxx", "", "", true, false)
  }

  def clearSession() {
    // sessionを参照すると新規sessionが作成されてしまうため、sessionOptionで存在チェック
    sessionOption match {
      case Some(_) => session.invalidate()
      case None => // do nothing
    }
    cookies.delete(sessionId)
  }
}

trait SessionUserInfo {
  val userInfo: User
}