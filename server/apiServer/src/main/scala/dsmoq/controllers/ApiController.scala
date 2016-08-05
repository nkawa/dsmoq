package dsmoq.controllers

import java.util.ResourceBundle
import javax.servlet.http.HttpServletRequest

import scala.language.implicitConversions
import scala.util.{Try, Success, Failure}

import com.typesafe.scalalogging.LazyLogging
import org.json4s._
import org.json4s.jackson.JsonMethods
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{ActionResult, ScalatraServlet, BadRequest, Forbidden, InternalServerError, NotFound, Ok}
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.servlet.{FileItem, FileUploadSupport}
import org.slf4j.MarkerFactory

import dsmoq.controllers.json.{
  ChangeGroupPrimaryImageParams,
  ChangePrimaryImageParams,
  CreateGroupParams,
  DatasetStorageParams,
  GetGroupMembersParams,
  SearchDatasetsParams,
  SearchGroupsParams,
  SearchRangeParams,
  SetGroupMemberRoleParams,
  SigninParams,
  StatisticsParams,
  SuggestApiParams,
  UpdateDatasetFileMetadataParams,
  UpdateDatasetGuestAccessParams,
  UpdateDatasetMetaParams,
  UpdateGroupParams,
  UpdateMailAddressParams,
  UpdatePasswordParams,
  UpdateProfileParams,
  UserAndGroupSuggestApiParams
}
import dsmoq.ResourceNames
import dsmoq.exceptions.{AccessDeniedException, BadRequestException, InputCheckException, InputValidationException, NotFoundException, NotAuthorizedException}
import dsmoq.logic.CheckUtil
import dsmoq.services.{AccountService, DataSetAccessControlItem, DatasetService, GroupMember, GroupService, StatisticsService, SystemService, TaskService, User}

class ApiController(val resource: ResourceBundle) extends ScalatraServlet
    with JacksonJsonSupport with FileUploadSupport with LazyLogging with AuthTrait {
  protected implicit val jsonFormats: Formats = DefaultFormats
  private implicit def objectToPipe[A](x: A) = Pipe(x)

  /**
   * AccountServiceのインスタンス
   */
  val accountService = new AccountService(resource)

  /**
   * DatasetServiceのインスタンス
   */
  val datasetService = new DatasetService(resource)

  /**
   * GroupServiceのインスタンス
   */
  val groupService = new GroupService(resource)

  /**
   * CheckUtilのインスタンス
   */
  val checkUtil = new CheckUtil(resource)

  /**
    * ログマーカー
    */
  val LOG_MARKER = MarkerFactory.getMarker("API_LOG")

  before() {
    contentType = formats("json")
  }

  after() {
    if (!hasAuthorizationHeader) {
      response.setHeader("isGuest", getUserFromSession.isGuest.toString)
    }
  }

  get("/*") {
    NotFound(AjaxResponse("NotFound")) // 404
  }

  put("/*") {
    NotFound(AjaxResponse("NotFound")) // 404
  }

  post("/*") {
    NotFound(AjaxResponse("NotFound")) // 404
  }

  delete("/*") {
    NotFound(AjaxResponse("NotFound")) // 404
  }

  // --------------------------------------------------------------------------
  // auth api
  // --------------------------------------------------------------------------
  post("/signin") {
    val ret = for {
      d        <- getJsonValue[SigninParams]
      json     <- jsonOptToTry(d)
      id       <- checkUtil.requireForForm("d.id", json.id)
      _        <- checkUtil.nonEmptyTrimmedSpacesForForm("d.id", id)
      password <- checkUtil.requireForForm("d.password", json.password)
      _        <- checkUtil.nonEmptyTrimmedSpacesForForm("d.password", password)
      result   <- accountService.findUserByIdAndPassword(id, password)
      _        <- updateSessionUser(result)
    } yield {
      result
    }
    ret match {
      case Success(user) =>
        AjaxResponse("OK", user)
      case Failure(e) => {
        log(e.getMessage, e)
        clearSession()
        e match {
          case e: BadRequestException => BadRequest(AjaxResponse("BadRequest", e.getMessage())) // 400
          case InputCheckException(name, message, false) => BadRequest(AjaxResponse("Illegal Argument", CheckError(name, message))) // 400
          case _ => InternalServerError(AjaxResponse("NG")) // 500
        }
      }
    }
  }

  post("/signout") {
    clearSession()
    AjaxResponse("OK", AuthTrait.GUEST_USER)
  }

  // --------------------------------------------------------------------------
  // profile api
  // --------------------------------------------------------------------------
  get("/profile") {
    val ret = for {
      user   <- getUser(allowGuest = true)
      result <- accountService.getUserProfile(user)
    } yield {
      result
    }
    ret |> toActionResult
  }

  put("/profile") {
    val ret = for {
      d        <- getJsonValue[UpdateProfileParams]
      json     <- jsonOptToTry(d)
      name     <- checkUtil.requireForForm("d.name", json.name)
      _        <- checkUtil.nonEmptyTrimmedSpacesForForm("d.name", name)
      fullname <- checkUtil.requireForForm("d.fullname", json.fullname)
      _        <- checkUtil.nonEmptyTrimmedSpacesForForm("d.fullname", fullname)
      user     <- getUser(allowGuest = false)
      result   <- accountService.updateUserProfile(user.id, json.name, json.fullname, json.organization, json.title, json.description)
      _        <- updateSessionUser(result)
    } yield {
      result
    }
    toActionResult(ret)
  }

  post("/profile/image") {
    val ret = for {
      icon     <- checkUtil.requireForForm("icon", fileParams.get("icon"))
      _        <- checkUtil.checkNonZeroByteFile("icon", icon)
      user     <- getUser(allowGuest = false)
      imageId  <- accountService.changeIcon(user.id, icon)
      newUser  = user.copy(image = dsmoq.AppConf.imageDownloadRoot + "user/" + user.id + "/" + imageId)
      _        <- updateSessionUser(newUser)
    } yield {
      newUser
    }
    toActionResult(ret)
  }

  post("/profile/email_change_requests") {
    val ret = for {
      d        <- getJsonValue[UpdateMailAddressParams]
      json     <- jsonOptToTry(d)
      email    <- checkUtil.requireForForm("d.email", json.email)
      _        <- checkUtil.nonEmptyTrimmedSpacesForForm("d.email", email)
      user     <- getUser(allowGuest = false)
      result   <- accountService.changeUserEmail(user.id, email)
      _        <- updateSessionUser(result)
    } yield {
      result
    }
    toActionResult(ret)
  }

  put("/profile/password") {
    val ret = for {
      d                <- getJsonValue[UpdatePasswordParams]
      json             <- jsonOptToTry(d)
      currentPassword  <- checkUtil.requireForForm("d.currentPassword", json.currentPassword)
      _                <- checkUtil.nonEmptyTrimmedSpacesForForm("d.currentPassword", currentPassword)
      newPassword      <- checkUtil.requireForForm("d.newPassword", json.newPassword)
      _                <- checkUtil.nonEmptyTrimmedSpacesForForm("d.newPassword", newPassword)
      user             <- getUser(allowGuest = false)
      result           <- accountService.changeUserPassword(user.id, currentPassword, newPassword)
    } yield {}
    toActionResult(ret)
  }

  // --------------------------------------------------------------------------
  // dataset api
  // --------------------------------------------------------------------------
  post("/datasets") {
    val files = fileMultiParams.get("file[]").getOrElse(Seq.empty).filter(_.name.nonEmpty)
    val ret = for {
      saveLocal <- checkUtil.requireForForm("saveLocal", params.get("saveLocal"))
      saveS3    <- checkUtil.requireForForm("saveS3", params.get("saveS3"))
      name      <- checkUtil.requireForForm("name", params.get("name"))
      _         <- checkUtil.nonEmptyTrimmedSpacesForForm("name", name)
      _         <- checkUtil.contains("saveLocal", saveLocal, Seq("true", "false"))
      _         <- checkUtil.contains("saveS3", saveS3, Seq("true", "false"))
      _         <- checkUtil.invokeSeq(files) { x => checkUtil.checkNonZeroByteFile("file[]", x) }
      _         <- checkUtil.invoke("saveLocal, saveS3", saveLocal.toBoolean || saveS3.toBoolean, resource.getString(ResourceNames.CHECK_S3_OR_LOCAL))
      user      <- getUser(allowGuest = false)
      result    <- datasetService.create(files, saveLocal.toBoolean, saveS3.toBoolean, name, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  get("/datasets") {
    val ret = for {
      d      <- getJsonValue[SearchDatasetsParams]
      json   <- Success(d.getOrElse(SearchDatasetsParams()))
      _      <- checkUtil.contains("d.orderby", json.orderby, Seq("attribute"))
      _      <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _      <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      user   <- getUser(allowGuest = true)
      result <- datasetService.search(json.query, json.owners, json.groups, json.attributes, json.limit, json.offset, json.orderby, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  get("/datasets/:datasetId") {
    val id = params("datasetId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("datasetId", id)
      user   <- getUser(allowGuest = true)
      result <- datasetService.get(id, user)
      _      <- SystemService.writeDatasetAccessLog(result.id, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  post("/datasets/:datasetId/files") {
    val id = params("datasetId")
    val ret = for {
      files  <- checkUtil.requireForForm("files", fileMultiParams.get("files"))
      _      <- checkUtil.hasElement("files", files)
      _      <- checkUtil.invokeSeq(files) { x => checkUtil.checkNonZeroByteFile("files", x) }
      _      <- checkUtil.validUuidForUrl("datasetId", id)
      user   <- getUser(allowGuest = false)
      result <- datasetService.addFiles(id, files, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  post("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val ret = for {
      file   <- checkUtil.requireForForm("file", fileParams.get("file"))
      _      <- checkUtil.checkNonZeroByteFile("file", file)
      _      <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _      <- checkUtil.validUuidForUrl("fileId", fileId)
      user   <- getUser(allowGuest = false)
      result <- datasetService.updateFile(datasetId, fileId, file, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  put("/datasets/:datasetId/files/:fileId/metadata") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val ret = for {
      d      <- getJsonValue[UpdateDatasetFileMetadataParams]
      json   <- jsonOptToTry(d)
      name   <- checkUtil.requireForForm("d.name", json.name)
      _      <- checkUtil.nonEmptyTrimmedSpacesForForm("d.name", name)
      _      <- checkUtil.requireForForm("d.description", json.description)
      _      <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _      <- checkUtil.validUuidForUrl("fileId", fileId)
      user   <- getUser(allowGuest = false)
      result <- datasetService.updateFileMetadata(datasetId, fileId, name, json.description.getOrElse(""), user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  delete("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _      <- checkUtil.validUuidForUrl("fileId", fileId)
      user   <- getUser(allowGuest = false)
      result <- datasetService.deleteDatasetFile(datasetId, fileId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  put("/datasets/:datasetId/metadata") {
    val datasetId = params("datasetId")
    val ret = for {
      d           <- getJsonValue[UpdateDatasetMetaParams]
      json        <- jsonOptToTry(d)
      name        <- checkUtil.requireForForm("d.name", json.name)
      _           <- checkUtil.nonEmptyTrimmedSpacesForForm("d.name", name)
      license     <- checkUtil.requireForForm("d.license", json.license)
      _           <- checkUtil.nonEmptyTrimmedSpacesForForm("d.license", license)
      _           <- checkUtil.validUuidForForm("d.license", license)
      description <- checkUtil.requireForForm("d.description", json.description)
      _           <- checkUtil.invokeSeq(json.attributes) { x =>
        for {
          _  <- checkUtil.nonEmptyTrimmedSpacesForForm("d.attributes.name", x.name)
          _  <- checkUtil.nonEmptyTrimmedSpacesForForm("d.attributes.value", x.value)
        } yield {}
      }
      _           <- checkUtil.invoke("d.attribute", json.attributes.filter(_.name == "featured").length < 2, resource.getString(ResourceNames.FEATURE_ATTRIBUTE_IS_ONLY_ONE))
      _           <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user        <- getUser(allowGuest = false)
      result      <- datasetService.modifyDatasetMeta(datasetId, name, json.description, license, json.attributes, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  get("/datasets/:datasetId/images") {
    val datasetId = params("datasetId")
    val ret = for {
      d       <- getJsonValue[SearchRangeParams]
      json    <- Success(d.getOrElse(SearchRangeParams()))
      _       <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _       <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      _       <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user    <- getUser(allowGuest = true)
      result  <- datasetService.getImages(datasetId, json.offset, json.limit, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  post("/datasets/:datasetId/images") {
    val datasetId = params("datasetId")
    val ret = for {
      images <- checkUtil.requireForForm("images", fileMultiParams.get("images")).map(_.filter(_.name.nonEmpty))
      _      <- checkUtil.hasElement("images", images)
      _      <- checkUtil.invokeSeq(images) { x => checkUtil.checkNonZeroByteFile("images", x) }
      _      <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user   <- getUser(allowGuest = false)
      result <- datasetService.addImages(datasetId, images, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  put("/datasets/:datasetId/images/primary") {
    val datasetId = params("datasetId")
    val ret = for {
      d       <- getJsonValue[ChangePrimaryImageParams]
      json    <- jsonOptToTry(d)
      imageId <- checkUtil.requireForForm("d.imageId", json.imageId)
      _       <- checkUtil.nonEmptyTrimmedSpacesForForm("d.imageId", imageId)
      _       <- checkUtil.validUuidForForm("d.imageId", imageId)
      _       <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user    <- getUser(allowGuest = false)
      result  <- datasetService.changePrimaryImage(datasetId, imageId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  delete("/datasets/:datasetId/images/:imageId") {
    val datasetId = params("datasetId")
    val imageId = params("imageId")
    val ret = for {
      _       <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _       <- checkUtil.validUuidForUrl("imageId", imageId)
      user    <- getUser(allowGuest = false)
      result  <- datasetService.deleteImage(datasetId, imageId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  get("/datasets/:datasetId/acl") {
    val datasetId = params("datasetId")
    val ret = for {
      d      <- getJsonValue[SearchRangeParams]
      json   <- Success(d.getOrElse(SearchRangeParams()))
      _      <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _      <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      _      <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user   <- getUser(allowGuest = true)
      result <- datasetService.searchOwnerships(datasetId, json.offset, json.limit, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  post("/datasets/:datasetId/acl") {
    val datasetId = params("datasetId")
    val ret = for {
      d       <- getJsonValue[List[DataSetAccessControlItem]]
      json    <- jsonOptToTry(d)
      _       <- checkUtil.hasElement("d", json)
      _       <- checkUtil.invokeSeq(json) { x =>
        for {
          _  <- checkUtil.nonEmptyTrimmedSpacesForForm("d.id", x.id)
          _  <- checkUtil.validUuidForForm("d.id", x.id)
          _  <- checkUtil.contains("d.ownerType", x.ownerType, Seq(1, 2))
          _  <- checkUtil.contains("d.accessLevel", x.accessLevel, Seq(0, 1, 2, 3))
        } yield {}
      }
      _       <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user    <- getUser(allowGuest = false)
      result  <- datasetService.setAccessControl(datasetId, json, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  put("/datasets/:datasetId/guest_access") {
    val datasetId = params("datasetId")
    val ret = for {
      d           <- getJsonValue[UpdateDatasetGuestAccessParams]
      json        <- jsonOptToTry(d)
      accessLevel <- checkUtil.requireForForm("d.accessLevel", json.accessLevel)
      _           <- checkUtil.contains("d.accessLevel", accessLevel, Seq(0, 1, 2))
      _           <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user        <- getUser(allowGuest = false)
      result      <- datasetService.setGuestAccessLevel(datasetId, accessLevel, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  delete("/datasets/:datasetId") {
    val datasetId = params("datasetId")
    val ret = for {
      _           <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user        <- getUser(allowGuest = false)
      _           <- datasetService.deleteDataset(datasetId, user)
    } yield {}
    toActionResult(ret)
  }

  put("/datasets/:datasetId/storage") {
    val datasetId = params("datasetId")
    val ret = for {
      d         <- getJsonValue[DatasetStorageParams]
      json      <- jsonOptToTry(d)
      saveLocal <- checkUtil.requireForForm("d.saveLocal", json.saveLocal)
      saveS3    <- checkUtil.requireForForm("d.saveS3", json.saveS3)
      _         <- checkUtil.invoke("saveLocal, saveS3", saveLocal || saveS3, resource.getString(ResourceNames.CHECK_S3_OR_LOCAL))
      _         <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user      <- getUser(allowGuest = false)
      result    <- datasetService.modifyDatasetStorage(datasetId, saveLocal, saveS3, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  post("/datasets/:datasetId/copy") {
    val datasetId = params("datasetId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user   <- getUser(allowGuest = false)
      result <- datasetService.copyDataset(datasetId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  post("/datasets/:datasetId/attributes/import") {
    val datasetId = params("datasetId")
    val ret = for {
      file <- checkUtil.requireForForm("file", fileParams.get("file"))
      _    <- checkUtil.checkNonZeroByteFile("file", file)
      _    <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user <- getUser(allowGuest = false)
      _    <- datasetService.importAttribute(datasetId, file, user)
    } yield {}
    toActionResult(ret)
  }

  get("/datasets/:datasetId/attributes/export") {
    val datasetId = params("datasetId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user   <- getUser(allowGuest = true)
      result <- datasetService.exportAttribute(datasetId, user)
    } yield {
      result
    }
    ret match {
      case Success(x) =>
        response.setHeader("Content-Disposition", "attachment; filename=" + x.getName)
        response.setHeader("Content-Type", "application/octet-stream;charset=binary")
        x
      case Failure(_) =>
        toActionResult(ret)
    }
  }

  put("/datasets/:datasetId/images/featured") {
    val datasetId = params("datasetId")
    val ret = for {
      d       <- getJsonValue[ChangePrimaryImageParams]
      json    <- jsonOptToTry(d)
      imageId <- checkUtil.requireForForm("d.imageId", json.imageId)
      _       <- checkUtil.nonEmptyTrimmedSpacesForForm("d.imageId", imageId)
      _       <- checkUtil.validUuidForForm("d.imageId", imageId)
      _       <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user    <- getUser(allowGuest = false)
      result  <- datasetService.changeFeaturedImage(datasetId, imageId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  get("/datasets/:datasetId/files") {
    val datasetId = params("datasetId")
    val ret = for {
      d      <- getJsonValue[SearchRangeParams]
      json   <- Success(d.getOrElse(SearchRangeParams(Some(dsmoq.AppConf.fileLimit), Some(0))))
      _      <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _      <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      _      <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user   <- getUser(allowGuest = true)
      result <- datasetService.getDatasetFiles(datasetId, json.limit, json.offset, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  get("/datasets/:datasetId/files/:fileId/zippedfiles") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val ret = for {
      d      <- getJsonValue[SearchRangeParams]
      json   <- Success(d.getOrElse(SearchRangeParams(Some(dsmoq.AppConf.fileLimit), Some(0))))
      _      <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _      <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      _      <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _      <- checkUtil.validUuidForUrl("fileId", fileId)
      user   <- getUser(allowGuest = true)
      result <- datasetService.getDatasetZippedFiles(datasetId, fileId, json.limit, json.offset, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // --------------------------------------------------------------------------
  // group api
  // --------------------------------------------------------------------------
  get("/groups") {
    val ret = for {
      d      <- getJsonValue[SearchGroupsParams]
      json   <- Success(d.getOrElse(SearchGroupsParams()))
      _      <- checkUtil.validUuidForForm("d.user", json.user)
      _      <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _      <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      user   <- getUser(allowGuest = true)
      result <- groupService.search(json.query, json.user, json.limit, json.offset, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  get("/groups/:groupId") {
    val groupId = params("groupId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("groupId", groupId)
      user   <- getUser(allowGuest = true)
      result <- groupService.get(groupId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  get("/groups/:groupId/members") {
    val groupId = params("groupId")
    val ret = for {
      d      <- getJsonValue[GetGroupMembersParams]
      json   <- Success(d.getOrElse(GetGroupMembersParams()))
      _      <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _      <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      _      <- checkUtil.validUuidForUrl("groupId", groupId)
      user   <- getUser(allowGuest = true)
      result <- groupService.getGroupMembers(groupId, json.limit, json.offset, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  post("/groups") {
    val ret = for {
      d           <- getJsonValue[CreateGroupParams]
      json        <- jsonOptToTry(d)
      name        <- checkUtil.requireForForm("d.name", json.name)
      _           <- checkUtil.nonEmptyTrimmedSpacesForForm("d.name", name)
      description <- checkUtil.requireForForm("d.description", json.description)
      user        <- getUser(allowGuest = false)
      result      <- groupService.createGroup(name, description, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  put("/groups/:groupId") {
    val groupId = params("groupId")
    val ret = for {
      d           <- getJsonValue[UpdateGroupParams]
      json        <- jsonOptToTry(d)
      name        <- checkUtil.requireForForm("d.name", json.name)
      _           <- checkUtil.nonEmptyTrimmedSpacesForForm("d.name", name)
      description <- checkUtil.requireForForm("d.description", json.description)
      _           <- checkUtil.validUuidForUrl("groupId", groupId)
      user        <- getUser(allowGuest = false)
      result      <- groupService.updateGroup(groupId, name, description, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  get("/groups/:groupId/images") {
    val groupId = params("groupId")
    val ret = for {
      d      <- getJsonValue[SearchRangeParams]
      json   <- Success(d.getOrElse(SearchRangeParams()))
      _      <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _      <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      _      <- checkUtil.validUuidForUrl("groupId", groupId)
      user   <- getUser(allowGuest = true)
      result <- groupService.getImages(groupId, json.offset, json.limit, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  post("/groups/:groupId/images") {
    val groupId = params("groupId")
    val ret = for {
      images <- checkUtil.requireForForm("images", fileMultiParams.get("images")).map(_.filter(_.name.nonEmpty))
      _      <- checkUtil.hasElement("images", images)
      _      <- checkUtil.invokeSeq(images) { x => checkUtil.checkNonZeroByteFile("images", x) }
      _      <- checkUtil.validUuidForUrl("groupId", groupId)
      user   <- getUser(allowGuest = false)
      result <- groupService.addImages(groupId, images, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  put("/groups/:groupId/images/primary") {
    val groupId = params("groupId")
    val ret = for {
      d       <- getJsonValue[ChangeGroupPrimaryImageParams]
      json    <- jsonOptToTry(d)
      imageId <- checkUtil.requireForForm("d.imageId", json.imageId)
      _       <- checkUtil.nonEmptyTrimmedSpacesForForm("d.imageId", imageId)
      _       <- checkUtil.validUuidForForm("d.imageId", imageId)
      _       <- checkUtil.validUuidForUrl("groupId", groupId)
      user    <- getUser(allowGuest = false)
      result  <- groupService.changePrimaryImage(groupId, imageId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  delete("/groups/:groupId/images/:imageId") {
    val groupId = params("groupId")
    val imageId = params("imageId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("groupId", groupId)
      _      <- checkUtil.validUuidForUrl("imageId", imageId)
      user   <- getUser(allowGuest = false)
      result <- groupService.deleteImage(groupId, imageId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  post("/groups/:groupId/members") {
    val groupId = params("groupId")
    val ret = for {
      d       <- getJsonValue[List[GroupMember]]
      json    <- jsonOptToTry(d)
      _       <- checkUtil.hasElement("d", json)
      _       <- checkUtil.invokeSeq(json) { x =>
        for {
          _      <- checkUtil.nonEmptyTrimmedSpacesForForm("d.userId", x.userId)
          _      <- checkUtil.validUuidForForm("d.userId", x.userId)
          _      <- checkUtil.contains("d.role", x.role, Seq(0, 1, 2))
        } yield {}
      }
      _       <- checkUtil.validUuidForUrl("groupId", groupId)
      user    <- getUser(allowGuest = false)
      result  <- groupService.addMembers(groupId, json, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  put("/groups/:groupId/members/:userId") {
    val groupId = params("groupId")
    val userId = params("userId")
    val ret = for {
      d      <- getJsonValue[SetGroupMemberRoleParams]
      json   <- jsonOptToTry(d)
      role   <- checkUtil.requireForForm("d.role", json.role)
      _      <- checkUtil.contains("d.role", role, Seq(0, 1, 2))
      _      <- checkUtil.validUuidForUrl("groupId", groupId)
      _      <- checkUtil.validUuidForUrl("userId", userId)
      user   <- getUser(allowGuest = false)
      result <- groupService.updateMemberRole(groupId, userId, role, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  delete("/groups/:groupId/members/:userId") {
    val groupId = params("groupId")
    val userId = params("userId")
    val ret = for {
      _    <- checkUtil.validUuidForUrl("groupId", groupId)
      _    <- checkUtil.validUuidForUrl("userId", userId)
      user <- getUser(allowGuest = false)
      _    <- groupService.removeMember(groupId, userId, user)
    } yield {}
    toActionResult(ret)
  }

  delete("/groups/:groupId") {
    val groupId = params("groupId")
    val ret = for {
      _    <- checkUtil.validUuidForUrl("groupId", groupId)
      user <- getUser(allowGuest = false)
      _    <- groupService.deleteGroup(groupId, user)
    } yield {}
    toActionResult(ret)
  }

  // --------------------------------------------------------------------------
  get("/system/is_valid_email") {
    // TODO not implemented
    AjaxResponse("OK")
  }

  get("/licenses") {
    val ret = for {
      result <- SystemService.getLicenses()
    } yield {
      result
    }
    toActionResult(ret)
  }

  get("/accounts") {
    val ret = for {
      result <- SystemService.getAccounts()
    } yield {
      result
    }
    toActionResult(ret)
  }

  get("/tags") {
    val ret = for {
      result <- SystemService.getTags()
    } yield {
      result
    }
    toActionResult(ret)
  }

  get("/suggests/users") {
    val ret = for {
      d      <- getJsonValue[SuggestApiParams]
      json   <- Success(d.getOrElse(SuggestApiParams()))
      _      <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _      <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      result <- SystemService.getUsers(json.query, json.limit, json.offset)
    } yield {
      result
    }
    toActionResult(ret)
  }

  get("/suggests/groups") {
    val ret = for {
      d      <- getJsonValue[SuggestApiParams]
      json   <- Success(d.getOrElse(SuggestApiParams()))
      _      <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _      <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      result <- SystemService.getGroups(json.query, json.limit, json.offset)
    } yield {
      result
    }
    toActionResult(ret)
  }

  get("/suggests/users_and_groups") {
    val ret = for {
      d      <- getJsonValue[UserAndGroupSuggestApiParams]
      json   <- Success(d.getOrElse(UserAndGroupSuggestApiParams()))
      _      <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _      <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      _      <- checkUtil.invokeSeq(json.excludeIds) { x => checkUtil.validUuidForForm("d.excludeId", x) }
      result <- SystemService.getUsersAndGroups(json.query, json.limit, json.offset, json.excludeIds)
    } yield {
      result
    }
    toActionResult(ret)
  }

  get("/suggests/attributes") {
    val ret = for {
      d      <- getJsonValue[SuggestApiParams]
      json   <- Success(d.getOrElse(SuggestApiParams()))
      _      <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _      <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      result <- SystemService.getAttributes(json.query, json.limit, json.offset)
    } yield {
      result
    }
    toActionResult(ret)
  }

  get("/tasks/:taskId") {
    val taskId = params("taskId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("taskId", taskId)
      result <- TaskService.getStatus(taskId)
    } yield {
      result
    }
    toActionResult(ret)
  }

  get("/statistics") {
    val ret = for {
      d      <- getJsonValue[StatisticsParams]
      json   <- Success(d.getOrElse(StatisticsParams()))
      result <- StatisticsService.getStatistics(json.from, json.to)
    } yield {
      result
    }
    toActionResult(ret)
  }

  get("/message") {
    val ret = for {
      result <- SystemService.getMessage()
    } yield {
      result
    }
    toActionResult(ret)
  }

  /**
   * 処理結果をActionResultに変換します。
   * 
   * @param result 処理結果
   * @return 処理結果のActionResult表現
   */
  private def toActionResult(result: Try[_]): ActionResult = {
    result match {
      case Success(()) => Ok(AjaxResponse("OK"))
      case Success(x) => Ok(AjaxResponse("OK", x))
      case Failure(e) =>
      log(e.getMessage, e)
       e match {
        case e: NotAuthorizedException => Forbidden(AjaxResponse("Unauthorized", e.getMessage)) // 403
        case AccessDeniedException(message) => Forbidden(AjaxResponse("AccessDenied", message)) // 403
        case e: NotFoundException => NotFound(AjaxResponse("NotFound")) // 404
        case InputCheckException(name, message, false) => BadRequest(AjaxResponse("Illegal Argument", CheckError(name, message))) // 400
        case InputCheckException(name, message, true) => NotFound(AjaxResponse("Illegal Argument", CheckError(name, message))) // 404
        case e: InputValidationException => BadRequest(AjaxResponse("BadRequest", e.getErrorMessage())) // 400
        case e: BadRequestException => BadRequest(AjaxResponse("BadRequest", e.getMessage)) // 400
        case _ => InternalServerError(AjaxResponse("NG")) // 500
      }
    }
  }

  private def jsonOptToTry[T](obj: Option[T]): Try[T] = {
    obj match {
      case None => Failure(new InputCheckException("d", resource.getString(ResourceNames.JSON_PARAMETER_REQUIRED), false))
      case Some(x) => Success(x)
    }
  }

  private def getJsonValue[T](implicit m: Manifest[T]): Try[Option[T]] = {
    params.get("d") match {
      case None => Success(None)
      case Some(x) => {
        try {
          JsonMethods.parse(x).extractOpt[T] match {
            case None => Failure(new InputCheckException("d", resource.getString(ResourceNames.INVALID_JSON_FORMAT), false))
            case Some(obj) => Success(Some(obj))
          }
        } catch {
          case e: Exception => Failure(new InputCheckException("d", resource.getString(ResourceNames.INVALID_JSON_FORMAT), false))
        }
      }
    }
  }
}

case class AjaxResponse[A](status: String, data: A = {})

case class CheckError(key: String, value: String)

case class Pipe[A](x: A) {
  def |>[B](f: A => B) = f.apply(x)
}
