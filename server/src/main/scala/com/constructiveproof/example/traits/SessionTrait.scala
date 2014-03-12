package com.constructiveproof.example.traits

import org.scalatra.ScalatraServlet
import com.constructiveproof.example.facade.User
import scala.util.{Failure, Success}

trait SessionTrait extends ScalatraServlet {
  private val SessionKey = "user"

  def getUserInfoFromSession() = {
    sessionOption match {
      case Some(_) => session.get(SessionKey) match {
        case Some(_) => session.getAttribute(SessionKey) match {
          case x: User => Success(session.getAttribute(SessionKey).asInstanceOf[User])
          case _ => Failure(new ClassCastException("data error"))
        }
        case None => Failure(new Exception("session attributes is not found"))
      }
      case None => Success(User("", "", "", "", "", "http://xxxx", true))
    }
  }

  def setUserInfoToSession(user: User) {
    if (!user.isGuest) {
      session.setAttribute(SessionKey, user)
    }
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