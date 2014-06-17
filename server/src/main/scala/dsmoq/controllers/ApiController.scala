package dsmoq.controllers

import org.scalatra.ScalatraServlet
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport
import dsmoq.services._
import scala.util.{Success, Failure}
import org.scalatra.servlet.FileUploadSupport
import dsmoq.services.data.LoginData._
import dsmoq.services.data.DatasetData._
import dsmoq.services.data.GroupData._
import dsmoq.forms._
import dsmoq.AppConf
import dsmoq.services.data.ProfileData.UpdateProfileParams
import dsmoq.exceptions.{InputValidationException, NotFoundException, NotAuthorizedException}
import com.sun.corba.se.spi.orbutil.fsm.Input

class ApiController extends ScalatraServlet
    with JacksonJsonSupport with SessionTrait with FileUploadSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
  }

  before("/*") {
    if (!isValidSession()) {
      if (!((request.getRequestURI == "/api/profile" && request.getMethod == "GET") ||
          (request.getRequestURI == "/api/licenses" && request.getMethod == "GET") ||
          (request.getRequestURI == "/api/accounts" && request.getMethod == "GET") ||
          (request.getRequestURI == "/api/suggests/attributes" && request.getMethod == "GET"))) {
        cookies.get(sessionId) match {
          case Some(x) =>
            clearSessionCookie()
            halt(body = AjaxResponse("Unauthorized"))
          case None => // do nothing
        }
      }
    }
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

  post("/profile") {
    val name = params.get("name")
    val fullname = params.get("fullname")
    val organization = params.get("organization")
    val title = params.get("title")
    val description = params.get("description")
    val image = fileParams.get("image")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = UpdateProfileParams(name, fullname, organization, title, description, image)
      user <- AccountService.updateUserProfile(userInfo, facadeParams)
    } yield {
      setUserInfoToSession(user)
      AjaxResponse("OK", user)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  post("/profile/email_change_requests") {
    val email = params.get("email")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    (for {
      userInfo <- getUserInfoFromSession()
      user <- AccountService.changeUserEmail(userInfo, email)
    } yield {
      setUserInfoToSession(user)
    }) match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) =>
        e match {
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  put("/profile/password") {
    val currentPassword = params.get("current_password")
    val newPassword = params.get("new_password")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      result <- AccountService.changeUserPassword(userInfo, currentPassword, newPassword)
    } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) =>
        e match {
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  post("/signin") {
    val id = params.get("id")
    val password = params.get("password")
    val facadeParams = SigninParams(id, password)
    AccountService.getAuthenticatedUser(facadeParams) match {
      case Success(x) =>
        setUserInfoToSession(x)
        AjaxResponse("OK", getUserInfoFromSession().get)
      case Failure(e) =>
        clearSession()
        clearSessionCookie()
        e match {
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse ("NG")
        }
    }
  }

  post("/signout") {
    clearSession()
    clearSessionCookie()
    AjaxResponse("OK", getUserInfoFromSession().get)
  }

  // dataset JSON API
  post("/datasets") {
    val files = fileMultiParams.get("file[]")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = CreateDatasetParams(userInfo, files)
      dataset <- DatasetService.create(facadeParams)
    } yield {
      AjaxResponse("OK", dataset)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse ("NG")
        }
    }
  }

  get("/datasets") {
    val query = params.get("query")
    val group = params.get("group")
    val attributes = multiParams.toMap
    val owner = params.get("owner")
    val limit = params.get("limit")
    val offset = params.get("offset")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = SearchDatasetsParams(query, group, attributes, owner, limit, offset, userInfo)
      datasets <- DatasetService.search(facadeParams)
    } yield {
      AjaxResponse("OK", datasets)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
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
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException=> AjaxResponse("NotFound")
          case _ => AjaxResponse("NG")
        }
    }
  }

  post("/datasets/:datasetId/files") {
    val files = fileMultiParams.get("files[]")
    val datasetId = params("datasetId")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = AddFilesToDatasetParams(userInfo, datasetId, files)
      files <- DatasetService.addFiles(facadeParams)
    } yield {
      AjaxResponse("OK", files)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  post("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val file = fileParams.get("file")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = UpdateFileParams(userInfo, datasetId, fileId, file)
      file <- DatasetService.updateFile(facadeParams)
    } yield {
      AjaxResponse("OK", file)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  put("/datasets/:datasetId/files/:fileId/metadata") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val filename = params.get("name")
    val description = params.get("description")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = ModifyDatasetMetadataParams(userInfo, datasetId, fileId, filename, description)
      files <- DatasetService.modifyFileMetadata(facadeParams)
    } yield {
      AjaxResponse("OK", files)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  delete("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = DeleteDatasetFileParams(userInfo, datasetId, fileId)
      result <- DatasetService.deleteDatasetFile(facadeParams)
    } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case _ => AjaxResponse("NG")
        }
    }
  }

  put("/datasets/:datasetId/metadata") {
    val datasetId = params("datasetId")
    val name = params.get("name")
    val description = params.get("description")
    val license = params.get("license")
    val attributes = multiParams("attributes[][name]").zip(multiParams("attributes[][value]"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = ModifyDatasetMetaParams(userInfo, datasetId, name, description, license, attributes)
      result <- DatasetService.modifyDatasetMeta(facadeParams)
    } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) =>
        println(e)
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  post("/datasets/:datasetId/images") {
    val images = fileMultiParams.get("images")
    val datasetId = params("datasetId")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = AddImagesToDatasetParams(userInfo, datasetId, images)
      files <- DatasetService.addImages(facadeParams)
    } yield {
      AjaxResponse("OK", files)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  put("/datasets/:datasetId/images/primary") {
    val datasetId = params("datasetId")
    val id = params.get("id")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = ChangePrimaryImageParams(userInfo, id, datasetId)
      result <- DatasetService.changePrimaryImage(facadeParams)
    } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  delete("/datasets/:datasetId/images/:imageId") {
    val datasetId = params("datasetId")
    val imageId = params("imageId")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = DeleteImageParams(userInfo, imageId, datasetId)
      primaryImage <- DatasetService.deleteImage(facadeParams)
    } yield {
      AjaxResponse("OK", primaryImage)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case _ => AjaxResponse("NG")
        }
    }
  }

  post("/datasets/:datasetId/acl") {
    val datasetId = params("datasetId")
    val ids = multiParams("id[]")
    val types = multiParams("type[]")
    val accessLevels = multiParams("accessLevel[]")

    (for {
      userInfo <- getUserInfoFromSession()
      facadeParams = AccessControlParams(datasetId, userInfo, ids, types, accessLevels)
      result <- DatasetService.setAccessControl(facadeParams)
    } yield {
      result
    }) match {
      case Success(x) => AjaxResponse("OK", x)
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  put("/datasets/:datasetId/guest_access") {
    setGuestAccessControl(params("datasetId"), params.get("accessLevel"))
  }

  delete("/datasets/:datasetId") {
    val datasetId = params("datasetId")

    val response = for {
      userInfo <- getUserInfoFromSession()
      result = DatasetService.deleteDataset(userInfo, datasetId)
     } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case _ => AjaxResponse("NG")
        }
    }
  }

  get("/groups") {
    val query = params.get("query")
    val user = params.get("user")
    val limit = params.get("limit")
    val offset = params.get("offset")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = SearchGroupsParams(userInfo, query, user, limit, offset)
      groups <- GroupService.search(facadeParams)
    } yield {
      AjaxResponse("OK", groups)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  get("/groups/:groupId") {
    val groupId = params("groupId")
    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = GetGroupParams(userInfo, groupId)
      dataset <- GroupService.get(facadeParams)
    } yield {
      AjaxResponse("OK", dataset)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: NotFoundException => AjaxResponse("NotFound")
          case _ => AjaxResponse("NG")
        }
    }
  }

  get("/groups/:groupId/members") {
    val groupId = params("groupId")
    val limit = params.get("limit")
    val offset = params.get("offset")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = GetGroupMembersParams(userInfo, groupId, limit, offset)
      dataset <- GroupService.getGroupMembers(facadeParams)
    } yield {
      AjaxResponse("OK", dataset)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  post("/groups") {
    val name = params.get("name")
    val description = params.get("description")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = CreateGroupParams(userInfo, name, description)
      group <- GroupService.createGroup(facadeParams)
    } yield {
      AjaxResponse("OK", group)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  put("/groups/:groupId") {
    val groupId = params("groupId")
    val name = params.get("name")
    val description = params.get("description")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = ModifyGroupParams(userInfo, groupId, name, description)
      group <- GroupService.modifyGroup(facadeParams)
    } yield {
      AjaxResponse("OK", group)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  post("/groups/:groupId/images") {
    val groupId = params("groupId")
    val images = fileMultiParams.get("images")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = AddImagesToGroupParams(userInfo, groupId, images)
      files <- GroupService.addImages(facadeParams)
    } yield {
      AjaxResponse("OK", files)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  put("/groups/:groupId/images/primary") {
    val groupId = params("groupId")
    val id = params.get("id")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = ChangeGroupPrimaryImageParams(userInfo, id, groupId)
      result <- GroupService.changePrimaryImage(facadeParams)
    } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  delete("/groups/:groupId/images/:imageId") {
    val groupId = params("groupId")
    val imageId = params("imageId")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = DeleteGroupImageParams(userInfo, imageId, groupId)
      primaryImage <- GroupService.deleteImage(facadeParams)
    } yield {
      AjaxResponse("OK", primaryImage)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case _ => AjaxResponse("NG")
        }
    }
  }

  post("/groups/:groupId/members") {
    val groupId = params("groupId")
    val userIds = multiParams.get("id[]")
    val roles = multiParams.get("role[]")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = SetUserRoleParams(userInfo, groupId, userIds, roles)
      userIds <- GroupService.setUserRole(facadeParams)
    } yield {
      userIds
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  delete("/groups/:groupId") {
    val groupId = params("groupId")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = DeleteGroupParams(userInfo, groupId)
      result <- GroupService.deleteGroup(facadeParams)
    } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case _ => AjaxResponse("NG")
        }
    }
  }

  get("/system/is_valid_email") {
    val value = params.get("value")
    val response = for {
      userInfo <- getUserInfoFromSession()
      result <- AccountService.isValidEmail(userInfo, value)
    } yield {
      AjaxResponse("OK", result)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse ("NG")
        }
    }
  }

  get("/licenses") {
    val licenses = AccountService.getLicenses()
    AjaxResponse("OK", licenses)
  }

  get("/accounts") {
    val accounts = AccountService.getAccounts()
    AjaxResponse("OK", accounts)
  }

  get("/suggests/users_and_groups") {
    val query = params.get("query")
    val result = AccountService.getUsersAndGroups(query)
    AjaxResponse("OK", result)
  }

  get("/suggests/attributes") {
    val query = params.get("query")
    val attributes = AccountService.getAttributes(query)
    AjaxResponse("OK", attributes)
  }

  get("/suggests/groups") {
    val query = params.get("query")
    val groups = AccountService.getGroups(query)
    AjaxResponse("OK", groups)
  }

  private def setGuestAccessControl(datasetId: String, accessLevel: Option[String]) = {
    val aci = AccessControl(datasetId, AppConf.guestGroupId, accessLevel)

    (for {
      userInfo <- getUserInfoFromSession()
      result <- DatasetService.setGroupAccessControl(userInfo, aci)
    } yield {
      result
    }) match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }
}

case class AjaxResponse[A](status: String, data: A = {})
