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
      case Failure(e) => AjaxResponse("NG")
    }
  }

  post("/profile/email_change_requests") {
    val email = params.get("email")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

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

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

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
            AjaxResponse("OK", getUserInfoFromSession())
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
    AjaxResponse("OK", getUserInfoFromSession())
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

  post("/datasets/:datasetId/files") {
    val files = fileMultiParams.get("file[]")
    val datasetId = params("datasetId")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = AddFilesToDatasetParams(userInfo, datasetId, files)
      files <- DatasetService.addFiles(facadeParams)
    } yield {
      AjaxResponse("OK", files)
    }
    response match {
      case Success(x) => x
      case Failure(e) => AjaxResponse("NG")
    }
  }

  post("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val file = fileParams.get("file")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = UpdateFileParams(userInfo, datasetId, fileId, file)
      file <- DatasetService.updateFile(facadeParams)
    } yield {
      AjaxResponse("OK", file)
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) => AjaxResponse("NG")
    }
  }

  put("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val filename = params("name")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = ModifyDatasetFilenameParams(userInfo, datasetId, fileId, filename)
      result <- DatasetService.modifyFilename(facadeParams)
    } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) => AjaxResponse("NG")
    }
  }

  delete("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = DeleteDatasetFileParams(userInfo, datasetId, fileId)
      result <- DatasetService.deleteDatasetFile(facadeParams)
    } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) => AjaxResponse("NG")
    }
  }

  put("/datasets/:datasetId/meta") {
    val datasetId = params("datasetId")
    val name = params("name")
    val description = params("description")
    val license = params("license")
    val attributes = multiParams("attributes[][name]").zip(multiParams("attributes[][value]"))

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = ModifyDatasetMetaParams(userInfo, datasetId, name, description, license, attributes)
      result <- DatasetService.modifyDatasetMeta(facadeParams)
    } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) => AjaxResponse("NG")
    }
  }

  post("/datasets/:datasetId/images") {
    val images = fileMultiParams.get("image")
    val datasetId = params("datasetId")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = AddImagesToDatasetParams(userInfo, datasetId, images)
      files <- DatasetService.addImages(facadeParams)
    } yield {
      AjaxResponse("OK", files)
    }
    response match {
      case Success(x) => x
      case Failure(e) => AjaxResponse("NG")
    }
  }

  put("/datasets/:datasetId/images/primary") {
    val datasetId = params("datasetId")
    val id = params("id")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = ChangePrimaryImageParams(userInfo, id, datasetId)
      result <- DatasetService.changePrimaryImage(facadeParams)
    } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) => AjaxResponse("NG")
    }
  }

  delete("/datasets/:datasetId/images/:imageId") {
    val datasetId = params("datasetId")
    val imageId = params("imageId")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = DeleteImageParams(userInfo, imageId, datasetId)
      primaryImage <- DatasetService.deleteImage(facadeParams)
    } yield {
      AjaxResponse("OK", primaryImage)
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

  get("/groups") {
    val query = params.get("query")
    val limit = params.get("limit")
    val offset = params.get("offset")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = SearchGroupsParams(userInfo, query, limit, offset)
      groups <- GroupService.search(facadeParams)
    } yield {
      AjaxResponse("OK", groups)
    }
    response match {
      case Success(x) => x
      case Failure(e) => AjaxResponse("NG")
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
      case Failure(e) => AjaxResponse("NG")
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
      case Failure(e) => AjaxResponse("NG")
    }
  }

  get("/groups/:groupId/datasets") {
    val groupId = params("groupId")
    val limit = params.get("limit")
    val offset = params.get("offset")

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = GetGroupDatasetsParams(userInfo, groupId, limit, offset)
      dataset <- GroupService.getGroupDatasets(facadeParams)
    } yield {
      AjaxResponse("OK", dataset)
    }
    response match {
      case Success(x) => x
      case Failure(e) => AjaxResponse("NG")
    }
  }

  post("/groups") {
    val name = params("name")
    val description = params("description")

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
      case Failure(e) => AjaxResponse("NG")
    }
  }

  put("/groups/:groupId") {
    val groupId = params("groupId")
    val name = params("name")
    val description = params("description")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = ModifyGroupParams(userInfo, groupId, name, description)
      group <- GroupService.modifyGroup(facadeParams)
    } yield {
      AjaxResponse("OK", group)
    }
    response match {
      case Success(x) => x
      case Failure(e) => AjaxResponse("NG")
    }
  }

  post("/groups/:groupId/images") {
    val groupId = params("groupId")
    val images = fileMultiParams.get("image")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = AddImagesToGroupParams(userInfo, groupId, images)
      files <- GroupService.addImages(facadeParams)
    } yield {
      AjaxResponse("OK", files)
    }
    response match {
      case Success(x) => x
      case Failure(e) => AjaxResponse("NG")
    }
  }

  put("/groups/:groupId/images/primary") {
    val groupId = params("groupId")
    val id = params("id")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = ChangeGroupPrimaryImageParams(userInfo, id, groupId)
      result <- GroupService.changePrimaryImage(facadeParams)
    } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) => AjaxResponse("NG")
    }
  }

  delete("/groups/:groupId/images/:imageId") {
    val groupId = params("groupId")
    val imageId = params("imageId")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = DeleteGroupImageParams(userInfo, imageId, groupId)
      primaryImage <- GroupService.deleteImage(facadeParams)
    } yield {
      AjaxResponse("OK", primaryImage)
    }
    response match {
      case Success(x) => x
      case Failure(e) => AjaxResponse("NG")
    }
  }

  post("/groups/:groupId/members") {
    val groupId = params("groupId")
    val userId = params("id")
    val role = params("role").toInt

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = AddUserToGroupParams(userInfo, groupId, userId, role)
      user <- GroupService.addUser(facadeParams)
    } yield {
      AjaxResponse("OK", user)
    }
    response match {
      case Success(x) => x
      case Failure(e) => AjaxResponse("NG")
    }
  }

  put("/groups/:groupId/members/:memberId/role") {
    val groupId = params("groupId")
    val memberId = params("memberId")
    val role = params("role").toInt

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = ModifyMemberRoleParams(userInfo, groupId, memberId, role)
      result <- GroupService.modifyMemberRole(facadeParams)
    } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) => AjaxResponse("NG")
    }
  }

  delete("/groups/:groupId/members/:memberId") {
    val groupId = params("groupId")
    val memberId = params("memberId")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = DeleteMemberParams(userInfo, groupId, memberId)
      result <- GroupService.deleteMember(facadeParams)
    } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) => AjaxResponse("NG")
    }
  }

  delete("/groups/:groupId") {
    val groupId = params("groupId")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      userInfo <- getUserInfoFromSession()
      facadeParams = DeleteGroupParams(userInfo, groupId)
      result <- GroupService.deleteGroup(facadeParams)
    } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) => AjaxResponse("NG")
    }
  }

  get("/system/is_valid_email") {
    val value = params.get("value")
    val response = for {
      userInfo <- getUserInfoFromSession()
      result <- AccountService.isValidEmail(userInfo, value)
    } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) => AjaxResponse("NG")
    }
  }

  private def setAccessControl(datasetId: String, groupId: String, accessLevel: Int) = {
    val aci = AccessControl(datasetId, groupId, accessLevel)

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

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
