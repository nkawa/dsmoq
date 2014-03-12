package com.constructiveproof.example

import com.constructiveproof.example.facade._
import org.scalatra.ScalatraServlet
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport
import com.constructiveproof.example.facade.SigninParams
import com.constructiveproof.example.traits.SessionTrait
import scala.util.{Success, Failure}

class ServerApiController extends ScalatraServlet with JacksonJsonSupport with SessionTrait {
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
  }

  get ("/*") {
    throw new Exception("err")
  }

  // JSON API
  get ("/profile") {
    val sessionUserInfo = getUserInfoFromSession() match {
      case Success(x) => x
      case Failure(e) => throw e
    }
    AjaxResponse("OK", sessionUserInfo)
  }

  post("/signin") {
    val id = params("id")
    val facadeParams = SigninParams(id, params("password"))
    LoginFacade.getAuthenticatedUser(facadeParams) match {
      case Success(x) => setUserInfoToSession(x)
      case Failure(e) => clearSession()
    }
    AjaxResponse("OK")
  }

  post("/signout") {
    clearSession()
    AjaxResponse("OK")
  }

  // dataset JSON API
  get("/datasets") {
    val sessionUserInfo = getUserInfoFromSession() match {
      case Success(x) => x
      case Failure(e) => throw e
    }
    val query = params.get("query")
    val group = params.get("group")
    val attributes = multiParams.toMap
    val limit = params.get("limit")
    val offset = params.get("offset")
    val facadeParams = SearchDatasetsParams(query, group, attributes, limit, offset, sessionUserInfo)

    AjaxResponse("OK", DatasetFacade.searchDatasets(facadeParams))
  }

  get("/datasets/:id") {
    val sessionUserInfo = getUserInfoFromSession() match {
      case Success(x) => x
      case Failure(e) => throw e
    }
    val id = params("id")
    val facadeParams = GetDatasetParams(id, sessionUserInfo)

    AjaxResponse("OK", DatasetFacade.getDataset(facadeParams))
  }
}

case class AjaxResponse[A](status: String, data: A = {})
