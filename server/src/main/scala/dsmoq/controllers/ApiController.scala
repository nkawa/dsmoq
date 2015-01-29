package dsmoq.controllers

import org.json4s._
import org.json4s.jackson.JsonMethods
import org.scalatra.ScalatraServlet
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport
import dsmoq.services._
import scala.util.{Try, Success, Failure}
import org.scalatra.servlet.{FileItem, FileUploadSupport}
import dsmoq.controllers.json._
import dsmoq.exceptions.{InputValidationException, NotFoundException, NotAuthorizedException}

class ApiController extends ScalatraServlet
    with JacksonJsonSupport with SessionTrait with FileUploadSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats
  private implicit def objectToPipe[A](x: A) = Pipe(x)

  before() {
    contentType = formats("json")
  }

  get("/*") {
    throw new Exception("err")
  }

  // --------------------------------------------------------------------------
  // auth api
  // --------------------------------------------------------------------------
  post("/signin") {
    val json = getJsonValue[SigninParams].getOrElse(SigninParams())

    AccountService.findUserByIdAndPassword(json.id.getOrElse(""), json.password.getOrElse("")) match {
      case Success(x) =>
        setSignedInUser(x)
        AjaxResponse("OK", x)
      case Failure(e) =>
        clearSession()
        e match {
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse ("NG")
        }
    }
  }

  post("/signout") {
    clearSession()
    AjaxResponse("OK", guestUser)
  }

  // --------------------------------------------------------------------------
  // profile api
  // --------------------------------------------------------------------------
  get("/profile") {
    AjaxResponse("OK", currentUser)
  }

  put("/profile") {
    val json = getJsonValue[UpdateProfileParams].getOrElse(UpdateProfileParams())
    (for {
      user <- signedInUser
      userNew <- AccountService.updateUserProfile(user.id, json.name, json.fullname,json.organization,
                                                  json.title, json.description)
    } yield {
      setSignedInUser(userNew)
      userNew
    }) |> toAjaxResponse
  }

  post("/profile/image") {
    val icon = fileParams.get("icon")
    (for {
      user <- signedInUser
      imageId <- AccountService.changeIcon(user.id, icon)
    } yield {
      setSignedInUser(User(
        id = user.id,
        name = user.name,
        fullname = user.fullname,
        organization = user.organization,
        title = user.title,
        image = dsmoq.AppConf.imageDownloadRoot + imageId,
        mailAddress = user.mailAddress,
        description = user.description,
        isGuest = user.isGuest,
        isDeleted = user.isDeleted
      ))
    }) |> toAjaxResponse
  }

  post("/profile/email_change_requests") {
    val json = getJsonValue[UpdateMailAddressParams].getOrElse(UpdateMailAddressParams())
    (for {
      user <- signedInUser
      userNew <- AccountService.changeUserEmail(user.id, json.email)
    } yield {
      setSignedInUser(userNew)
      userNew
    }) |> toAjaxResponse
  }

  put("/profile/password") {
    val json = getJsonValue[UpdatePasswordParams].getOrElse(UpdatePasswordParams())
    (for {
      user <- signedInUser
      _ <- AccountService.changeUserPassword(user.id, json.currentPassword, json.newPassword)
    } yield {}) |> toAjaxResponse
  }

  // --------------------------------------------------------------------------
  // dataset api
  // --------------------------------------------------------------------------
  post("/datasets") {
    val files = getFiles("file[]")
    val saveLocal = params.get("saveLocal") map { x => x == "true" }
    val saveS3 = params.get("saveS3") map { x => x == "true" }
    (for {
      user <- signedInUser
      dataset <- DatasetService.create(files, saveLocal, saveS3, user)
    } yield dataset) |> toAjaxResponse
  }

  get("/datasets") {
    val json = params.get("d").map(JsonMethods.parse(_).extract[SearchDatasetsParams])
                               .getOrElse(SearchDatasetsParams())
    DatasetService.search(json.query, json.owners, json.groups, json.attributes,
                          json.limit, json.offset, currentUser) |> toAjaxResponse
  }

  get("/datasets/:datasetId") {
    val id = params("datasetId")
    (for {
      dataset <- DatasetService.get(id, currentUser)
      _ <- SystemService.writeDatasetAccessLog(dataset.id, currentUser)
    } yield dataset) |> toAjaxResponse
  }

  post("/datasets/:datasetId/files") {
    val id = params("datasetId")
    val files = fileMultiParams("files")
    (for {
      user <- signedInUser
      datasets <- DatasetService.addFiles(id, files, user)
    } yield datasets) |> toAjaxResponse
  }

  post("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val file = fileParams.get("file")

    (for {
      user <- signedInUser
      file <- DatasetService.updateFile(datasetId, fileId, file, user)
    } yield file) |> toAjaxResponse
  }

  put("/datasets/:datasetId/files/:fileId/metadata") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val json = getJsonValue[UpdateDatasetFileMetadataParams].getOrElse(UpdateDatasetFileMetadataParams())
    (for {
      user <- signedInUser
      file <- DatasetService.updateFileMetadata(datasetId, fileId, json.name.getOrElse(""), json.description.getOrElse(""), user)
    } yield file) |> toAjaxResponse
  }

  delete("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")

    (for {
      user <- signedInUser
      file <- DatasetService.deleteDatasetFile(datasetId, fileId, user)
    } yield file) |> toAjaxResponse
  }

  put("/datasets/:datasetId/metadata") {
    val datasetId = params("datasetId")
    val json = getJsonValue[UpdateDatasetMetaParams].getOrElse(UpdateDatasetMetaParams())
    (for {
      user <- signedInUser
      result <- DatasetService.modifyDatasetMeta(datasetId, json.name, json.description, json.license, json.attributes, user)
    } yield {}) |> toAjaxResponse
  }

  post("/datasets/:datasetId/images") {
    val datasetId = params("datasetId")
    val images = fileMultiParams.get("images").getOrElse(Seq.empty)
    (for {
      user <- signedInUser
      images <- DatasetService.addImages(datasetId, images, user)
    } yield images) |> toAjaxResponse
  }

  put("/datasets/:datasetId/images/primary") {
    val datasetId = params("datasetId")
    val json = getJsonValue[ChangePrimaryImageParams].getOrElse(ChangePrimaryImageParams())
    (for {
      user <- signedInUser
      result <- DatasetService.changePrimaryImage(datasetId, json.imageId.getOrElse(""), user)
    } yield result) |> toAjaxResponse
  }

  delete("/datasets/:datasetId/images/:imageId") {
    val datasetId = params("datasetId")
    val imageId = params("imageId")

    (for {
      user <- signedInUser
      primaryImage <- DatasetService.deleteImage(datasetId, imageId, user)
    } yield primaryImage) |> toAjaxResponse
  }

  post("/datasets/:datasetId/acl") {
    val datasetId = params("datasetId")
    val acl = getJsonValue[List[DataSetAccessControlItem]].getOrElse(List.empty)
    (for {
      user <- signedInUser
      result <- DatasetService.setAccessControl(datasetId, acl, user)
    } yield result) |> toAjaxResponse
  }

  put("/datasets/:datasetId/guest_access") {
    val datasetId = params("datasetId")
    val json = getJsonValue[UpdateDatasetGuestAccessParams].getOrElse(UpdateDatasetGuestAccessParams())
    (for {
      user <- signedInUser
      result <- DatasetService.setGuestAccessLevel(datasetId, json.accessLevel.getOrElse(0), user)
    } yield result) |> toAjaxResponse
  }

  delete("/datasets/:datasetId") {
    val datasetId = params("datasetId")
    (for {
      user <- signedInUser
      result = DatasetService.deleteDataset(datasetId, user)
    } yield {}) |> toAjaxResponse
  }

  put("/datasets/:datasetId/storage") {
    val datasetId = params("datasetId")
    val json = getJsonValue[DatasetStorageParams].getOrElse(DatasetStorageParams())
    (for {
      user <- signedInUser
      result <- DatasetService.modifyDatasetStorage(datasetId, json.saveLocal, json.saveS3, user)
    } yield result) |> toAjaxResponse
  }

  post("/datasets/:datasetId/copy") {
    val datasetId = params("datasetId")
    (for {
      user <- signedInUser
      file <- DatasetService.copyDataset(datasetId, user)
    } yield file) |> toAjaxResponse
  }

  post("/datasets/:datasetId/attribute/import") {
    val datasetId = params("datasetId")
    val file = fileParams.get("file")

    (for {
      user <- signedInUser
      file <- DatasetService.importAttribute(datasetId, file, user)
    } yield file) |> toAjaxResponse
  }

  // --------------------------------------------------------------------------
  // group api
  // --------------------------------------------------------------------------
  get("/groups") {
    val json = getJsonValue[SearchGroupsParams].getOrElse(SearchGroupsParams())
    GroupService.search(json.query, json.user, json.limit, json.offset, currentUser) |> toAjaxResponse
  }

  get("/groups/:groupId") {
    val groupId = params("groupId")
    GroupService.get(groupId, currentUser) |> toAjaxResponse
  }

  get("/groups/:groupId/members") {
    val groupId = params("groupId")
    val json = getJsonValue[GetGroupMembersParams].getOrElse(GetGroupMembersParams())
    GroupService.getGroupMembers(groupId, json.limit, json.offset, currentUser) |> toAjaxResponse
  }

  post("/groups") {
    val json = getJsonValue[CreateGroupParams].getOrElse(CreateGroupParams())
    (for {
      user <- signedInUser
      group <- GroupService.createGroup(json.name.getOrElse(""), json.description.getOrElse(""), user)
    } yield group) |> toAjaxResponse
  }

  put("/groups/:groupId") {
    val groupId = params("groupId")
    val json = getJsonValue[UpdateGroupParams].getOrElse(UpdateGroupParams())
    (for {
      user <- signedInUser
      group <- GroupService.updateGroup(groupId, json.name.getOrElse(""), json.description.getOrElse(""), user)
    } yield group) |> toAjaxResponse
  }

  post("/groups/:groupId/images") {
    val groupId = params("groupId")
    val images = fileMultiParams.get("images").getOrElse(Seq.empty)
    (for {
      user <- signedInUser
      files <- GroupService.addImages(groupId, images, user)
    } yield files) |> toAjaxResponse
  }

  put("/groups/:groupId/images/primary") {
    val groupId = params("groupId")
    val json = getJsonValue[ChangeGroupPrimaryImageParams].getOrElse(ChangeGroupPrimaryImageParams())
    (for {
      user <- signedInUser
      _ <- GroupService.changePrimaryImage(groupId, json.imageId.getOrElse(""), user)
    } yield {}) |> toAjaxResponse
  }

  delete("/groups/:groupId/images/:imageId") {
    val groupId = params("groupId")
    val imageId = params("imageId")
    (for {
      user <- signedInUser
      primaryImage <- GroupService.deleteImage(groupId, imageId, user)
    } yield primaryImage) |> toAjaxResponse
  }

  post("/groups/:groupId/members") {
    val groupId = params("groupId")
    val roles = getJsonValue[List[GroupMember]].getOrElse(List.empty)
    (for {
      user <- signedInUser
      _ <- GroupService.addMembers(groupId, roles, user)
    } yield {}) |> toAjaxResponse
  }

  put("/groups/:groupId/members/:userId") {
    val groupId = params("groupId")
    val userId = params("userId")
    val json = getJsonValue[SetGroupMemberRoleParams].getOrElse(SetGroupMemberRoleParams())
    json.role match {
      case Some(role) =>
        (for {
          user <- signedInUser
          _ <- GroupService.updateMemberRole(groupId, userId, role, user)
        } yield {}) |> toAjaxResponse
      case None =>
        AjaxResponse("OK")
    }
  }

  delete("/groups/:groupId/members/:userId") {
    val groupId = params("groupId")
    val userId = params("userId")
    (for {
      user <- signedInUser
      _ <- GroupService.removeMember(groupId, userId, user)
    } yield {}) |> toAjaxResponse
  }

  delete("/groups/:groupId") {
    val groupId = params("groupId")
    (for {
      user <- signedInUser
      _ <- GroupService.deleteGroup(groupId, user)
    } yield {}) |> toAjaxResponse
  }

  // --------------------------------------------------------------------------
  get("/system/is_valid_email") {
    // TODO not implemented
    AjaxResponse("OK")
  }

  get("/licenses") {
    val licenses = SystemService.getLicenses()
    AjaxResponse("OK", licenses)
  }

  get("/accounts") {
    val accounts = SystemService.getAccounts()
    AjaxResponse("OK", accounts)
  }

  get("/suggests/users") {
    val json = getJsonValue[UserSuggestApiParams].getOrElse(UserSuggestApiParams())
    val users = SystemService.getUsers(json.query, json.limit, json.offset)
    AjaxResponse("OK", users)
  }

  get("/suggests/groups") {
    val query = params.get("query")
    val groups = SystemService.getGroups(query)
    AjaxResponse("OK", groups)
  }

  get("/suggests/users_and_groups") {
    val query = params.get("query")
    val result = SystemService.getUsersAndGroups(query)
    AjaxResponse("OK", result)
  }

  get("/suggests/attributes") {
    val query = params.get("query")
    val attributes = SystemService.getAttributes(query)
    AjaxResponse("OK", attributes)
  }

  get("/tasks/:taskId") {
    val taskId = params("taskId")
    TaskService.getStatus(taskId) |> toAjaxResponse
  }

  private def toAjaxResponse[A](result: Try[A]) = result match {
    case Success(Unit) => AjaxResponse("OK")
    case Success(x) => AjaxResponse("OK", x)
    case Failure(e) =>
     e match {
      case e: NotAuthorizedException => AjaxResponse("Unauthorized")
      case e: NotFoundException => AjaxResponse("NotFound")
      case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
      case _ =>
        log(e.getMessage, e)
        AjaxResponse("NG")
    }
  }

  private def getFiles(key: String) = {
    try {
      fileMultiParams("file[]")
    } catch {
      case e :NoSuchElementException => Seq[FileItem]()
    }
  }

  private def getJsonValue[T](implicit m: Manifest[T]) = params.get("d").map(x => JsonMethods.parse(x).extract[T])
}

case class AjaxResponse[A](status: String, data: A = {})

case class Pipe[A](x: A)
{
  def |>[B](f: A => B) = f.apply(x)
}