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

  before() {
    contentType = formats("json")
  }

  get ("/*") {
    throw new Exception("err")
  }

  // JSON API
  get ("/profile") {
    val session = Option(servletContext.getAttribute(SessionKey))

    val data = LoginFacade.getLoginInfo(session)
    data
  }

  post("/signin") {
    val id = params("id")
    val password = params("password")

    if (isAuthenticated(id, password)) {
      servletContext.setAttribute(SessionKey, id)
    }
    val response = AjaxResponse("OK", {})
    response
  }

  post("/signout") {
    Option(servletContext.getAttribute(SessionKey)).foreach {
      _ =>  servletContext.removeAttribute(SessionKey)
    }
    val response = AjaxResponse("OK", {})
    response
  }
}

case class AjaxResponse[A](status: String, data: A)
