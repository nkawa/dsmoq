package dsmoq.controllers

import org.scalatra.ScalatraServlet
import dsmoq.facade.data.User
import scala.util.{Try, Failure, Success}

trait SessionTrait extends ScalatraServlet {
  private val SessionKey = "user"

  def getUserInfoFromSession(): Try[User] = {
    sessionOption match {
      case Some(_) => session.get(SessionKey) match {
        case Some(x) => Success(x.asInstanceOf[User])
        case None =>
          clearSession()
          Success(User("", "", "", "", "", "http://xxxx", true))
      }
      case None => Success(User("", "", "", "", "", "http://xxxx", true))
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
}

trait SessionUserInfo {
  val userInfo: User
}