package com.constructiveproof.example

import com.constructiveproof.example.facade.LoginFacade
import org.scalatra.ScalatraServlet
import org.json4s._
import org.scalatra.json.JacksonJsonSupport

class ApiController extends ScalatraServlet with JacksonJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
  }

  get("/*") {
    throw new Exception("err")
  }

  get("/login") {
    // FIXME
    val data = LoginFacade.getLoginInfo
    data
  }
}
