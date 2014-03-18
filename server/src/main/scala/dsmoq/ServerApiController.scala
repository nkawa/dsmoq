package dsmoq

import org.scalatra.ScalatraServlet
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport
import dsmoq.facade._
import dsmoq.traits.SessionTrait
import scala.util.{Success, Failure}
import org.scalatra.servlet.{MultipartConfig, FileUploadSupport}

class ServerApiController extends ScalatraServlet with JacksonJsonSupport with SessionTrait with FileUploadSupport {
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
        setUserInfoToSession(x)
        AjaxResponse("OK")
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
  get("/datasets") {
    val query = params.get("query")
    val group = params.get("group")
    val attributes = multiParams.toMap
    val limit = params.get("limit")
    val offset = params.get("offset")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = SearchDatasetsParams(query, group, attributes, limit, offset, userInfo)
      datasets <- DatasetFacade.searchDatasets(facadeParams)
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
      dataset <- DatasetFacade.getDataset(facadeParams)
    } yield {
      AjaxResponse("OK", dataset)
    }
    response match {
      case Success(x) => x
      case Failure(e) => AjaxResponse("NG")
    }
  }

  post("/datasets") {
    val files = fileMultiParams.get("file[]")
    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = CreateDatasetParams(userInfo, files)
      dataset <- DatasetFacade.createDataset(facadeParams)
    } yield {
      AjaxResponse("OK", dataset)
    }
    response match {
      case Success(x) => x
      case Failure(e) => AjaxResponse("NG")
    }
  }
}

case class AjaxResponse[A](status: String, data: A = {})
