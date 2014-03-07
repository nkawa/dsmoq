package com.constructiveproof.example.facade

object LoginFacade {
  def getLoginInfo[A](x: Option[A]): AjaxResponse[Profile] = {
    val user = x match {
      case Some(_) =>
        User("id", "name", "fullname", "organization", "title", "http://xxxx", false)
      case None =>
        User("id", "name", "fullname", "organization", "title", "http://xxxx", true)
    }
    AjaxResponse("OK", Profile(Some(user)))
  }
}

case class User(
  id: String,
  name: String,
  fullname: String,
  organization: String,
  title: String,
  icon: String,
  isGuest: Boolean
)

case class Profile(user: Option[User])
case class AjaxResponse[A](status: String, data: A)