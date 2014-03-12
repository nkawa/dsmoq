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
    getUserInfoFromSession() match {
      case Success(x) => AjaxResponse("OK", x)
      case Failure(e) => AjaxResponse("NG")
    }
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
    getUserInfoFromSession() match {
      case Success(x) =>
        val query = params.get("query")
        val group = params.get("group")
        val attributes = multiParams.toMap
        val limit = params.get("limit")
        val offset = params.get("offset")
        val facadeParams = SearchDatasetsParams(query, group, attributes, limit, offset, x)

        DatasetFacade.searchDatasets(facadeParams) match {
          case Success(x) => AjaxResponse("OK", x)
          case Failure(e) => AjaxResponse("NG")
        }
      case Failure(e) => AjaxResponse("NG")
    }
  }

  get("/datasets/:id") {
    getUserInfoFromSession() match {
      case Success(x) =>
        val id = params("id")
        val facadeParams = GetDatasetParams(id, x)

        DatasetFacade.getDataset(facadeParams) match {
          case Success(x) => AjaxResponse("OK", x)
          case Failure(e) => AjaxResponse("NG")
        }
      case Failure(e) => AjaxResponse("NG")
    }
  }
}

case class AjaxResponse[A](status: String, data: A = {})
