package com.constructiveproof.example.facade

object LoginFacade {
  def getAuthenticatedUser(params: SigninParams) = {
    if (params.id == "foo" && params.password == "foo") {
      Option(User("id", "name", "fullname", "organization", "title", "http://xxxx", false))
    } else {
      None
    }
  }

  def getLoginInfo(params: SessionParams): User = {
    val user = params.session match {
      case Some(x) => x
      case None => User("", "", "", "", "", "http://xxxx", true)
    }
    user
  }
}

// request
case class SessionParams(session: Option[User])
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