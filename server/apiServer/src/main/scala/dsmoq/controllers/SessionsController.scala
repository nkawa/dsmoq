package dsmoq.controllers

import org.scalatra._

class SessionsController extends ScalatraServlet {
  val SessionKey = "user"

  private def isAuthenticated(login: String, password: String) = 
    login == "foo" && password == "foo"

  post("/") {
    val login = params("login")
    val password = params("password")

    if (isAuthenticated(login, password)) {
      servletContext.setAttribute(SessionKey, login)
      "ok"
    }else{
      "ng"
    }
  }

  get("/") {
    Option(servletContext.getAttribute(SessionKey)).getOrElse("anonymouse")
  }
  // Never do this in a real app. State changes should never happen as a result of a GET request. However, this does
  // make it easier to illustrate the logout code.
  get("/logout") {
    Option(servletContext.getAttribute(SessionKey)) match {
      case Some(_) => 
        servletContext.removeAttribute(SessionKey)
        "ok"
      case None =>
        "not login"
    }
  }

}
