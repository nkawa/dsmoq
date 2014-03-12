package com.constructiveproof.example.facade

import scala.util.{Failure, Success}

object LoginFacade {
  def getAuthenticatedUser(params: SigninParams) = {
    // TODO db access
    if (params.id == "foo" && params.password == "foo") {
      Success(User("id", "name", "fullname", "organization", "title", "http://xxxx", false))
    } else {
      Failure(new Exception("User Not Found"))
    }
  }
}

// request
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