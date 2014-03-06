package com.constructiveproof.example.facade

object LoginFacade {
  def getLoginInfo: AjaxResponse[LoginInfo] = {
    val user = User("test01", "Test User 01")
    AjaxResponse("OK", LoginInfo(Some(user)))
    // Not Login
//    AjaxResponse("OK", LoginInfo(None))
  }
}

case class User(userId: String, userName: String)

case class LoginInfo(user: Option[User])
case class AjaxResponse[A](status: String, data: A)