package dsmoq.controllers

import javax.servlet.http.HttpServletRequest

import scala.language.implicitConversions
import scala.util.{Try, Success, Failure}

import org.json4s._
import org.json4s.jackson.JsonMethods
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.servlet.{FileItem, FileUploadSupport}

import dsmoq.controllers.json._
import dsmoq.exceptions.{BadRequestException, InputValidationException, NotFoundException, NotAuthorizedException}
import dsmoq.services._

class ApiController extends ScalatraServlet
    with JacksonJsonSupport with SessionTrait with FileUploadSupport with ApiKeyAuthorizationTrait {
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
    (for {
      user <- getUser(request, true)
    } yield {
      AccountService.getUserProfile(user)
    }) |> toAjaxResponse
  }

  put("/profile") {
    val json = getJsonValue[UpdateProfileParams].getOrElse(UpdateProfileParams())
    (for {
      user <- getUser(request, false)
      userNew <- AccountService.updateUserProfile(user.id, json.name, json.fullname,json.organization,
                                                  json.title, json.description)
    } yield {
      // 直接ログインしてユーザーを更新した場合、セッション上のログイン情報を更新する
      if (!hasAuthorizationHeader(request)) {
        setSignedInUser(userNew)
      }
      userNew
    }) |> toAjaxResponse
  }

  post("/profile/image") {
    val icon = fileParams.get("icon")
    (for {
      user <- getUser(request, false)
      imageId <- AccountService.changeIcon(user.id, icon)
    } yield {
      val newUser = User(
        id = user.id,
        name = user.name,
        fullname = user.fullname,
        organization = user.organization,
        title = user.title,
        image = dsmoq.AppConf.imageDownloadRoot + "user/" + user.id + "/" + imageId,
        mailAddress = user.mailAddress,
        description = user.description,
        isGuest = user.isGuest,
        isDeleted = user.isDeleted
      )
      // 直接ログインしてユーザーを更新した場合、セッション上のログイン情報を更新する
      if (!hasAuthorizationHeader(request)) {
        setSignedInUser(newUser)
      }
      newUser
    }) |> toAjaxResponse
  }

  post("/profile/email_change_requests") {
    val json = getJsonValue[UpdateMailAddressParams].getOrElse(UpdateMailAddressParams())
    (for {
      user <- getUser(request, false)
      userNew <- AccountService.changeUserEmail(user.id, json.email)
    } yield {
      // 直接ログインしてユーザーを更新した場合、セッション上のログイン情報を更新する
      if (!hasAuthorizationHeader(request)) {
        setSignedInUser(userNew)
      }
      userNew
    }) |> toAjaxResponse
  }

  put("/profile/password") {
    val json = getJsonValue[UpdatePasswordParams].getOrElse(UpdatePasswordParams())
    (for {
      user <- getUser(request, false)
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
    val name = params.get("name")
    (for {
      user <- getUser(request, false)
      dataset <- DatasetService.create(files, saveLocal, saveS3, name, user)
    } yield dataset) |> toAjaxResponse
  }

  get("/datasets") {
    val json = params.get("d").map(JsonMethods.parse(_).extract[SearchDatasetsParams])
                               .getOrElse(SearchDatasetsParams())
    (for {
      user <- getUser(request, true)
      dataset <- DatasetService.search(json.query, json.owners, json.groups, json.attributes, json.limit, json.offset, json.orderby, user)
    } yield dataset) |> toAjaxResponse
  }

  get("/datasets/:datasetId") {
    val id = params("datasetId")
    (for {
      user <- getUser(request, true)
      dataset <- DatasetService.get(id, user)
      _ <- SystemService.writeDatasetAccessLog(dataset.id, user)
    } yield dataset) |> toAjaxResponse
  }

  post("/datasets/:datasetId/files") {
    val id = params("datasetId")
    val files = fileMultiParams("files")
    (for {
      user <- getUser(request, false)
      datasets <- DatasetService.addFiles(id, files, user)
    } yield datasets) |> toAjaxResponse
  }

  post("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val file = fileParams.get("file")

    (for {
      user <- getUser(request, false)
      file <- DatasetService.updateFile(datasetId, fileId, file, user)
    } yield file) |> toAjaxResponse
  }

  put("/datasets/:datasetId/files/:fileId/metadata") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val json = getJsonValue[UpdateDatasetFileMetadataParams].getOrElse(UpdateDatasetFileMetadataParams())
    (for {
      user <- getUser(request, false)
      file <- DatasetService.updateFileMetadata(datasetId, fileId, json.name.getOrElse(""), json.description.getOrElse(""), user)
    } yield file) |> toAjaxResponse
  }

  delete("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")

    (for {
      user <- getUser(request, false)
      file <- DatasetService.deleteDatasetFile(datasetId, fileId, user)
    } yield file) |> toAjaxResponse
  }

  put("/datasets/:datasetId/metadata") {
    val datasetId = params("datasetId")
    val json = getJsonValue[UpdateDatasetMetaParams].getOrElse(UpdateDatasetMetaParams())
    (for {
      user <- getUser(request, false)
      result <- DatasetService.modifyDatasetMeta(datasetId, json.name, json.description, json.license, json.attributes, user)
    } yield {}) |> toAjaxResponse
  }

  get("/datasets/:datasetId/images") {
    val datasetId = params("datasetId")
    val json = getJsonValue[SearchRangeParams].getOrElse(SearchRangeParams())
    (for {
      user <- getUser(request, false)
      images <- DatasetService.getImages(datasetId, json.offset, json.limit, user)
    } yield images) |> toAjaxResponse
  }

  post("/datasets/:datasetId/images") {
    val datasetId = params("datasetId")
    val images = fileMultiParams.get("images").getOrElse(Seq.empty)
    (for {
      user <- getUser(request, false)
      images <- DatasetService.addImages(datasetId, images, user)
    } yield images) |> toAjaxResponse
  }

  put("/datasets/:datasetId/images/primary") {
    val datasetId = params("datasetId")
    val json = getJsonValue[ChangePrimaryImageParams].getOrElse(ChangePrimaryImageParams())
    (for {
      user <- getUser(request, false)
      result <- DatasetService.changePrimaryImage(datasetId, json.imageId.getOrElse(""), user)
    } yield result) |> toAjaxResponse
  }

  delete("/datasets/:datasetId/images/:imageId") {
    val datasetId = params("datasetId")
    val imageId = params("imageId")

    (for {
      user <- getUser(request, false)
      primaryImage <- DatasetService.deleteImage(datasetId, imageId, user)
    } yield primaryImage) |> toAjaxResponse
  }

  get("/datasets/:datasetId/acl") {
    val datasetId = params("datasetId")
    val json = getJsonValue[SearchRangeParams].getOrElse(SearchRangeParams())
    (for {
      user <- getUser(request, false)
      result <- DatasetService.searchOwnerships(datasetId, json.offset, json.limit, user)
    } yield result) |> toAjaxResponse
  }

  post("/datasets/:datasetId/acl") {
    val datasetId = params("datasetId")
    val acl = getJsonValue[List[DataSetAccessControlItem]].getOrElse(List.empty)
    (for {
      user <- getUser(request, false)
      result <- DatasetService.setAccessControl(datasetId, acl, user)
    } yield result) |> toAjaxResponse
  }

  put("/datasets/:datasetId/guest_access") {
    val datasetId = params("datasetId")
    val json = getJsonValue[UpdateDatasetGuestAccessParams].getOrElse(UpdateDatasetGuestAccessParams())
    (for {
      user <- getUser(request, false)
      result <- DatasetService.setGuestAccessLevel(datasetId, json.accessLevel.getOrElse(0), user)
    } yield result) |> toAjaxResponse
  }

  delete("/datasets/:datasetId") {
    val datasetId = params("datasetId")
    (for {
      user <- getUser(request, false)
      result = DatasetService.deleteDataset(datasetId, user)
    } yield {}) |> toAjaxResponse
  }

  put("/datasets/:datasetId/storage") {
    val datasetId = params("datasetId")
    val json = getJsonValue[DatasetStorageParams].getOrElse(DatasetStorageParams())
    (for {
      user <- getUser(request, false)
      result <- DatasetService.modifyDatasetStorage(datasetId, json.saveLocal, json.saveS3, user)
    } yield result) |> toAjaxResponse
  }

  post("/datasets/:datasetId/copy") {
    val datasetId = params("datasetId")
    (for {
      user <- getUser(request, false)
      file <- DatasetService.copyDataset(datasetId, user)
    } yield file) |> toAjaxResponse
  }

  post("/datasets/:datasetId/attributes/import") {
    val datasetId = params("datasetId")
    val file = fileParams.get("file")

    (for {
      user <- getUser(request, false)
      _ <- DatasetService.importAttribute(datasetId, file, user)
    } yield {}) |> toAjaxResponse
  }

  get("/datasets/:datasetId/attributes/export") {
    val datasetId = params("datasetId")
    val result = for {
      user <- getUser(request, false)
      file <- DatasetService.exportAttribute(datasetId, user)
    } yield {
      file
    }
    result match {
      case Success(x) =>
          response.setHeader("Content-Disposition", "attachment; filename=" + x.getName)
          response.setHeader("Content-Type", "application/octet-stream;charset=binary")
          x
      case Failure(e) => halt(status = 403, reason = "Forbidden", body="Forbidden")
    }
  }

  put("/datasets/:datasetId/images/:imageId/featured") {
    val datasetId = params("datasetId")
    val imageId = params("imageId")

    (for {
      user <- getUser(request, false)
      _ <- DatasetService.changeFeaturedImage(datasetId, imageId, user)
    } yield {}) |> toAjaxResponse
  }

  get("/datasets/:datasetId/files") {
    val datasetId = params("datasetId")
    val json = getJsonValue[SearchRangeParams].getOrElse(SearchRangeParams(Some(dsmoq.AppConf.fileLimit), Some(0)))
    (for {
      user <- getUser(request, true)
      result <- DatasetService.getDatasetFiles(datasetId, json.limit, json.offset, user)
    } yield result) |> toAjaxResponse
  }

  get("/datasets/:datasetId/files/:fileId/zippedfiles") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val json = getJsonValue[SearchRangeParams].getOrElse(SearchRangeParams(Some(dsmoq.AppConf.fileLimit), Some(0)))
    (for {
      user <- getUser(request, true)
      result <- DatasetService.getDatasetZippedFiles(datasetId, fileId, json.limit, json.offset, user)
    } yield result) |> toAjaxResponse
  }

  // --------------------------------------------------------------------------
  // group api
  // --------------------------------------------------------------------------
  get("/groups") {
    val json = getJsonValue[SearchGroupsParams].getOrElse(SearchGroupsParams())
    (for {
      user <- getUser(request, true)
      groups <- GroupService.search(json.query, json.user, json.limit, json.offset, user)
    } yield groups) |> toAjaxResponse
  }

  get("/groups/:groupId") {
    val groupId = params("groupId")
    (for {
      user <- getUser(request, true)
      group <- GroupService.get(groupId, user)
    } yield group) |> toAjaxResponse
  }

  get("/groups/:groupId/members") {
    val groupId = params("groupId")
    val json = getJsonValue[GetGroupMembersParams].getOrElse(GetGroupMembersParams())
    (for {
      user <- getUser(request, true)
      members <- GroupService.getGroupMembers(groupId, json.limit, json.offset, user)
    } yield members) |> toAjaxResponse
  }

  post("/groups") {
    val json = getJsonValue[CreateGroupParams].getOrElse(CreateGroupParams())
    (for {
      user <- getUser(request, false)
      group <- GroupService.createGroup(json.name.getOrElse(""), json.description.getOrElse(""), user)
    } yield group) |> toAjaxResponse
  }

  put("/groups/:groupId") {
    val groupId = params("groupId")
    val json = getJsonValue[UpdateGroupParams].getOrElse(UpdateGroupParams())
    (for {
      user <- getUser(request, false)
      group <- GroupService.updateGroup(groupId, json.name.getOrElse(""), json.description.getOrElse(""), user)
    } yield group) |> toAjaxResponse
  }

  get("/groups/:groupId/images") {
    val groupId = params("groupId")
    val json = getJsonValue[SearchRangeParams].getOrElse(SearchRangeParams())
    (for {
      user <- getUser(request, false)
      images <- GroupService.getImages(groupId, json.offset, json.limit, user)
    } yield images) |> toAjaxResponse
  }

  post("/groups/:groupId/images") {
    val groupId = params("groupId")
    val images = fileMultiParams.get("images").getOrElse(Seq.empty)
    (for {
      user <- getUser(request, false)
      files <- GroupService.addImages(groupId, images, user)
    } yield files) |> toAjaxResponse
  }

  put("/groups/:groupId/images/primary") {
    val groupId = params("groupId")
    val json = getJsonValue[ChangeGroupPrimaryImageParams].getOrElse(ChangeGroupPrimaryImageParams())
    (for {
      user <- getUser(request, false)
      _ <- GroupService.changePrimaryImage(groupId, json.imageId.getOrElse(""), user)
    } yield {}) |> toAjaxResponse
  }

  delete("/groups/:groupId/images/:imageId") {
    val groupId = params("groupId")
    val imageId = params("imageId")
    (for {
      user <- getUser(request, false)
      primaryImage <- GroupService.deleteImage(groupId, imageId, user)
    } yield primaryImage) |> toAjaxResponse
  }

  post("/groups/:groupId/members") {
    val groupId = params("groupId")
    val roles = getJsonValue[List[GroupMember]].getOrElse(List.empty)
    (for {
      user <- getUser(request, false)
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
          user <- getUser(request, false)
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
      user <- getUser(request, false)
      _ <- GroupService.removeMember(groupId, userId, user)
    } yield {}) |> toAjaxResponse
  }

  delete("/groups/:groupId") {
    val groupId = params("groupId")
    (for {
      user <- getUser(request, false)
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

  get("/tags") {
    val tags = SystemService.getTags()
    AjaxResponse("OK", tags)
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
    val json = getJsonValue[UserAndGroupSuggestApiParams].getOrElse(UserAndGroupSuggestApiParams())
    val result = SystemService.getUsersAndGroups(json.query, json.limit, json.offset, json.excludeIds)
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

  get("/statistics") {
    val json = getJsonValue[StatisticsParams].getOrElse(StatisticsParams())
    (for {
     stat <- StatisticsService.getStatistics(json.from, json.to)
    } yield stat) |> toAjaxResponse
  }

  get("/message") {
    val message = SystemService.getMessage()
    AjaxResponse("OK", message)
  }
  
  private def toAjaxResponse[A](result: Try[A]) = result match {
    case Success(Unit) => AjaxResponse("OK")
    case Success(x) => AjaxResponse("OK", x)
    case Failure(e) =>
     e match {
      case e: NotAuthorizedException => AjaxResponse("Unauthorized")
      case e: NotFoundException => AjaxResponse("NotFound")
      case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
      case e: BadRequestException => AjaxResponse("BadRequest", e.getMessage)
      case _ =>
        log(e.getMessage, e)
        AjaxResponse("NG")
    }
  }

  private def getUser(request: HttpServletRequest, allowGuest: Boolean): Try[User] = {
    userFromHeader(request) match {
      case ApiUser(user) => Success(user)
      case ApiAuthorizationFailed => Failure(new NotAuthorizedException)
      case NoAuthorizationHeader => signedInUser match {
        case SignedInUser(user) => Success(user)
        case GuestUser(user) => if (allowGuest) {
          Success(user)
        } else {
          clearSession()
          Failure(new NotAuthorizedException)
        }
      }
    }
  }

  private def getFiles(key: String) = {
    try {
      fileMultiParams(key)
    } catch {
      case e :NoSuchElementException => Seq[FileItem]()
    }
  }

  private def getJsonValue[T](implicit m: Manifest[T]) = params.get("d").flatMap{ x => JsonMethods.parse(x).extractOpt[T] }
}

case class AjaxResponse[A](status: String, data: A = {})

case class Pipe[A](x: A)
{
  def |>[B](f: A => B) = f.apply(x)
}
