package dsmoq.controllers

import dsmoq.AppConf
import dsmoq.services.User
import org.scalatra.ScalatraServlet

sealed trait SessionUser
case class SignedInUser(user: User) extends SessionUser
case class GuestUser(user: User) extends SessionUser

trait SessionTrait extends ScalatraServlet {
  private val SessionKey = "user"
  private val sessionId = "JSESSIONID"

  def isValidSession() = {
    sessionOption.map(_ => true)
      .getOrElse(cookies.get(sessionId).map(_ => true).getOrElse(false))
  }

  def signedInUser: SessionUser = {
    sessionOption match {
      case Some(_) => session.get(SessionKey) match {
        case Some(x) => SignedInUser(x.asInstanceOf[User])
        case None => GuestUser(guestUser)
      }
      case None => GuestUser(guestUser)
    }
  }

  def setSignedInUser(x: User) = {
    session.setAttribute(SessionKey, x)
    x
  }

  def guestUser = {
    User(AppConf.guestUserId, "", "", "", "", "http://xxxx", "", "", true, false)
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
