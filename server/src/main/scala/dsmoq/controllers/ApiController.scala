package dsmoq.controllers

import org.scalatra.ScalatraServlet
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport
import dsmoq.services._
import scala.util.{Success, Failure}
import org.scalatra.servlet.FileUploadSupport
import dsmoq.services.data.LoginData._
import dsmoq.services.data.DatasetData._
import dsmoq.forms._
import dsmoq.AppConf
import dsmoq.services.data.ProfileData.UpdateProfileParams

class ApiController extends ScalatraServlet
    with JacksonJsonSupport with SessionTrait with FileUploadSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
  }

  get("/*") {
    throw new Exception("err")
  }

  // JSON API
  get("/profile") {
    getUserInfoFromSession() match {
      case Success(x) => AjaxResponse("OK", x)
      case Failure(e) => AjaxResponse("NG")
    }
  }

  put("/profile") {
    val name = params("name")
    val fullname = params("fullname")
    val organization = params("organization")
    val title = params("title")
    val description = params("description")
    val icon = fileParams.get("icon")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = UpdateProfileParams(name, fullname, organization, title, description, icon)
      user <- AccountService.updateUserProfile(userInfo, facadeParams)
    } yield {
      setUserInfoToSession(user)
      AjaxResponse("OK", user)
    }
    response match {
      case Success(x) => x
      case Failure(e) => AjaxResponse("NG")
    }
  }

  post("/profile/email_change_requests") {
    val email = params.get("email")
    (for {
      userInfo <- getUserInfoFromSession()
      result <- AccountService.changeUserEmail(userInfo, email)
    } yield {
      result
    }) match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) => AjaxResponse("NG")
    }
  }

  put("/profile/password") {
    val currentPassword = params.get("current_password")
    val newPassword = params.get("new_password")
    val response = for {
      userInfo <- getUserInfoFromSession()
      result <- AccountService.changeUserPassword(userInfo, currentPassword, newPassword)
    } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) => AjaxResponse("NG")
    }
  }

  post("/signin") {
    val id = params("id")
    val facadeParams = SigninParams(id, params("password"))
    AccountService.getAuthenticatedUser(facadeParams) match {
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
      dataset <- DatasetService.create(facadeParams)
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
      datasets <- DatasetService.search(facadeParams)
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
      dataset <- DatasetService.get(facadeParams)
    } yield {
      AjaxResponse("OK", dataset)
    }
    response match {
      case Success(x) => x
      case Failure(e) => AjaxResponse("NG")
    }
  }


  put("/datasets/:datasetId/acl/:groupId") {
    setAccessControl(params("datasetId"), params("groupId"), params("accessLevel").toInt)
  }

  delete("/datasets/:datasetId/acl/:groupId") {
    setAccessControl(params("datasetId"), params("groupId"), 0)
  }
  put("/datasets/:datasetId/acl/guest") {
    setAccessControl(params("datasetId"), AppConf.guestGroupId, params("accessLevel").toInt)
  }

  delete("/datasets/:datasetId/acl/guest") {
    setAccessControl(params("datasetId"), AppConf.guestGroupId, 0)
  }

  private def setAccessControl(datasetId: String, groupId: String, accessLevel: Int) = {
    val aci = AccessControl(datasetId, groupId, accessLevel)

    (for {
      userInfo <- getUserInfoFromSession()
      result <- DatasetService.setAccessControl(userInfo, aci)
    } yield {
      result
    }) match {
      case Success(x) => AjaxResponse("OK", x)
      case Failure(e) => AjaxResponse("NG")
    }
  }
}

case class AjaxResponse[A](status: String, data: A = {})
