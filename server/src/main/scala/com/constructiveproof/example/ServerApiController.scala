package com.constructiveproof.example

import com.constructiveproof.example.facade.LoginFacade
import org.scalatra.ScalatraServlet
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport

class ServerApiController extends ScalatraServlet with JacksonJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  get ("/*") {
    throw new Exception("err")
  }

  // JSON API
  get ("/profile") {
    contentType = formats("json")
    val data = LoginFacade.getLoginInfo
    data
  }

  post("/signin") {
    // FIXME parameter check & Authentication
    val id = params("id")
    val password = params("password")
    redirect("/")
  }

  post("/signout") {
    redirect("/")
  }
}
