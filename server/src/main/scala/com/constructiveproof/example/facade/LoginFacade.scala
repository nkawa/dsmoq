package com.constructiveproof.example.facade

import com.constructiveproof.example.AjaxResponse

object LoginFacade {
  def isAuthenticated(params: SigninParams) =
    params.id == "foo" && params.password == "foo"

  def getLoginInfo(x: SessionParams): AjaxResponse[User] = {
    val user = x.session match {
      case Some(_) =>
        User("id", "name", "fullname", "organization", "title", "http://xxxx", false)
      case None =>
        User("id", "name", "fullname", "organization", "title", "http://xxxx", true)
    }
    AjaxResponse("OK", user)
  }
}

// request
case class SessionParams(session: Object)
case class SigninParams(id: String, password: String)

// response
case class User(
  id: String,
  name: String,
  fullname: String,
  organization: String,
  title: String,
  image: String,
  isGuest: Boolean
)
//case class Profile(user: Option[User])