package com.constructiveproof.example

import com.constructiveproof.example.facade._
import org.scalatra.ScalatraServlet
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport
import com.constructiveproof.example.facade.SessionParams
import com.constructiveproof.example.facade.SigninParams

class ServerApiController extends ScalatraServlet with JacksonJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  val SessionKey = "user"

  before() {
    contentType = formats("json")
  }

  get ("/*") {
    throw new Exception("err")
  }

  // JSON API
  get ("/profile") {
    val facadeParams = SessionParams(Option(servletContext.getAttribute(SessionKey)))
    val user = LoginFacade.getLoginInfo(facadeParams)

    val response = AjaxResponse("OK", user)
    response
  }

  post("/signin") {
    val id = params("id")
    val facadeParams = SigninParams(id, params("password"))

    if (LoginFacade.isAuthenticated(facadeParams)) {
      servletContext.setAttribute(SessionKey, id)
    }
    val response = AjaxResponse("OK")
    response
  }

  post("/signout") {
    Option(servletContext.getAttribute(SessionKey)).foreach {
      _ =>  servletContext.removeAttribute(SessionKey)
    }
    val response = AjaxResponse("OK")
    response
  }

  // dataset JSON API
  get("/datasets") {
    val query = params.get("query")
    val group = params.get("group")
    val attributes = multiParams.toMap
    val limit = params.get("limit")
    val offset = params.get("offset")
    val facadeParams = DatasetCatalogParams(query, group, attributes, limit, offset)

    val datasets = DatasetFacade.searchDatasets(facadeParams)
    val response = AjaxResponse("OK", datasets)
    response
  }
}

case class AjaxResponse[A](status: String, data: A = {})
