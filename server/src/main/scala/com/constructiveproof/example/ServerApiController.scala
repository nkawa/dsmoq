package com.constructiveproof.example

import com.constructiveproof.example.facade._
import org.scalatra.ScalatraServlet
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport
import com.constructiveproof.example.facade.SessionParams
import com.constructiveproof.example.facade.SigninParams
import com.constructiveproof.example.traits.SessionTrait

class ServerApiController extends ScalatraServlet with JacksonJsonSupport with SessionTrait {
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
    val sessionUserInfo = getSessionParameter(SessionKey)
    val facadeParams = SessionParams(sessionUserInfo)
    val user = LoginFacade.getLoginInfo(facadeParams)

    val response = AjaxResponse("OK", user)
    response
  }

  post("/signin") {
    val id = params("id")
    val facadeParams = SigninParams(id, params("password"))

    val user = LoginFacade.getAuthenticatedUser(facadeParams)
    user match {
      case Some(x) => sessionOption match {
        case None => session.setAttribute(SessionKey, x)
        case Some(_) => // do nothing
        }
      case None => // Do nothing
    }

    val response = AjaxResponse("OK")
    response
  }

  post("/signout") {
    // sessionを参照すると新規sessionが作成されてしまうため、sessionOptionで存在チェック
    sessionOption match {
      case Some(_) => session.invalidate()
      case None => // do nothing
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
