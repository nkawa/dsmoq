package com.constructiveproof.example

import com.constructiveproof.example.facade.LoginFacade
import org.scalatra.ScalatraServlet
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport

class ServerApiController extends ScalatraServlet with JacksonJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  val SessionKey = "user"

  private def isAuthenticated(id: String, password: String) =
    id == "foo" && password == "foo"

  get ("/*") {
    throw new Exception("err")
  }

  // JSON API
  get ("/profile") {
    val session = Option(servletContext.getAttribute(SessionKey))

    contentType = formats("json")
    val data = LoginFacade.getLoginInfo(session)
    data
  }

  post("/signin") {
    val id = params("id")
    val password = params("password")

    if (isAuthenticated(id, password)) {
      servletContext.setAttribute(SessionKey, id)
    }
    redirect("/")
  }

  post("/signout") {
    Option(servletContext.getAttribute(SessionKey)) match {
      case Some(_) =>
        servletContext.removeAttribute(SessionKey)
      case None =>
        // Do nothing
    }
    redirect("/")
  }
}
