package dsmoq.controllers

import org.scalatra.ScalatraServlet
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport
import dsmoq.facade._
import scala.util.{Success, Failure}
import org.scalatra.servlet.{MultipartConfig, FileUploadSupport}
import dsmoq.facade.data.LoginData._
import dsmoq.facade.data.DatasetData._
import dsmoq.forms._

class ApiController extends ScalatraServlet
    with JacksonJsonSupport with SessionTrait with FileUploadSupport {
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
      case Success(x) =>
        x match {
          case Some(y) =>
            setUserInfoToSession(y)
            AjaxResponse("OK")
          case None =>
            clearSession()
            AjaxResponse("NG")
        }
      case Failure(e) =>
        clearSession()
        AjaxResponse("NG")
    }
  }

  post("/signout") {
    clearSession()
    AjaxResponse("OK")
  }

  // dataset JSON API
  post("/datasets") {
    val files = fileMultiParams.get("file[]")
    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = CreateDatasetParams(userInfo, files)
      dataset <- DatasetFacade.create(facadeParams)
    } yield {
      AjaxResponse("OK", dataset)
    }
    response match {
      case Success(x) => x
      case Failure(e) => AjaxResponse("NG")
    }
  }

  get("/datasets") {
    val query = params.get("query")
    val group = params.get("group")
    val attributes = multiParams.toMap
    val limit = params.get("limit")
    val offset = params.get("offset")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = SearchDatasetsParams(query, group, attributes, limit, offset, userInfo)
      datasets <- DatasetFacade.search(facadeParams)
    } yield {
      AjaxResponse("OK", datasets)
    }
    response match {
      case Success(x) => x
      case Failure(e) => AjaxResponse("NG")
    }
  }

  get("/datasets/:id") {
    val id = params("id")
    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = GetDatasetParams(id, userInfo)
      dataset <- DatasetFacade.get(facadeParams)
    } yield {
      AjaxResponse("OK", dataset)
    }
    response match {
      case Success(x) => x
      case Failure(e) => AjaxResponse("NG")
    }
  }

  put("/datasets/:datasetId/acl/:groupId") {
    val aci = AccessControl(params("datasetId"), params("groupId"), params("accessLevel").toInt)

    (for {
      userInfo <- getUserInfoFromSession()
      result <- DatasetFacade.setAccessContorl(userInfo, aci)
    } yield {
      result
    }) match {
      case Success(x) => AjaxResponse("OK", x)
      case Failure(e) => AjaxResponse("NG")
    }
  }

  delete("/datasets/:datasetId/acl/:groupId") {
    val aci = AccessControl(params("datasetId"), params("groupId"), 0)

    (for {
      userInfo <- getUserInfoFromSession()
      result <- DatasetFacade.setAccessContorl(userInfo, aci)
    } yield {
      Unit
    }) match {
      case Success(x) => AjaxResponse("OK", x)
      case Failure(e) => AjaxResponse("NG")
    }
  }
}

case class AjaxResponse[A](status: String, data: A = {})
