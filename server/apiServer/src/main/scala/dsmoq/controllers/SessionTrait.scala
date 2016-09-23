package dsmoq.controllers

import dsmoq.AppConf
import dsmoq.services.User
import org.scalatra.ScalatraServlet

@deprecated(message = "use AuthTrait", since = "")
sealed trait SessionUser
@deprecated(message = "use AuthTrait", since = "")
case class SignedInUser(user: User) extends SessionUser
@deprecated(message = "use AuthTrait", since = "")
case class GuestUser(user: User) extends SessionUser

@deprecated(message = "use AuthTrait", since = "")
trait SessionTrait extends ScalatraServlet {
  private val SessionKey = "user"
  private val sessionId = "JSESSIONID"

  def isValidSession(): Boolean = {
    sessionOption.isDefined || cookies.get(sessionId).isDefined
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

  def setSignedInUser(x: User): User = {
    session.setAttribute(SessionKey, x)
    x
  }

  def guestUser: User = {
    AppConf.guestUser
  }

  def clearSession(): Unit = {
    // sessionを参照すると新規sessionが作成されてしまうため、sessionOptionで存在チェック
    sessionOption match {
      case Some(_) => session.invalidate()
      case None => // do nothing
    }
    cookies.delete(sessionId)
  }
}

@deprecated(message = "use AuthTrait", since = "")
trait SessionUserInfo {
  val userInfo: User
}
