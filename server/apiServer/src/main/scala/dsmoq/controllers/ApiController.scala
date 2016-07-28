package dsmoq.controllers

import java.util.ResourceBundle
import javax.servlet.http.HttpServletRequest

import scala.language.implicitConversions
import scala.util.{Try, Success, Failure}

import org.json4s._
import org.json4s.jackson.JsonMethods
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.servlet.{FileItem, FileUploadSupport}

import dsmoq.ResourceNames
import dsmoq.controllers.json._
import dsmoq.exceptions.{AccessDeniedException, BadRequestException, InputCheckException, InputValidationException, NotFoundException, NotAuthorizedException}
import dsmoq.logic.CheckUtil
import dsmoq.services.{AccountService, AuthService, DataSetAccessControlItem, DatasetService, GroupMember, GroupService, StatisticsService, SystemService, TaskService, User}

class ApiController(resource: ResourceBundle) extends ScalatraServlet
    with JacksonJsonSupport with FileUploadSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats
  private implicit def objectToPipe[A](x: A) = Pipe(x)

  /**
   * AuthServiceのインスタンス
   */
  val authService = new AuthService(resource, this)

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

  before() {
    contentType = formats("json")
  }

  get("/*") {
    AjaxResponse("NotFound")
  }

  put("/*") {
    AjaxResponse("NotFound")
  }

  post("/*") {
    AjaxResponse("NotFound")
  }

  delete("/*") {
    AjaxResponse("NotFound")
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
      _        <- authService.updateSessionUser(result)
    } yield {
      result
    }
    ret match {
      case Success(user) =>
        AjaxResponse("OK", user)
      case Failure(e) => {
        authService.clearSession()
        e match {
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case InputCheckException(name, message, false) => AjaxResponse("Illegal Argument", CheckError(name, message))
          case _ => AjaxResponse("NG")
        }
      }
    }
  }

  post("/signout") {
    authService.clearSession()
    AjaxResponse("OK", authService.GUEST_USER)
  }

  // --------------------------------------------------------------------------
  // profile api
  // --------------------------------------------------------------------------
  get("/profile") {
    val ret = for {
      user   <- authService.getUser
      result <- accountService.getUserProfile(user)
    } yield {
      result
    }
    ret |> toAjaxResponse
  }

  put("/profile") {
    val ret = for {
      d        <- getJsonValue[UpdateProfileParams]
      json     <- jsonOptToTry(d)
      name     <- checkUtil.requireForForm("d.name", json.name)
      _        <- checkUtil.nonEmptyTrimmedSpacesForForm("d.name", name)
      fullname <- checkUtil.requireForForm("d.fullname", json.fullname)
      _        <- checkUtil.nonEmptyTrimmedSpacesForForm("d.fullname", fullname)
      user     <- authService.getNotGuestUser
      result   <- accountService.updateUserProfile(user.id, json.name, json.fullname, json.organization, json.title, json.description)
      _        <- updateSessionUser(result)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/profile/image") {
    val ret = for {
      icon     <- checkUtil.requireForForm("icon", fileParams.get("icon"))
      _        <- checkUtil.checkNonZeroByteFile("icon", icon)
      user     <- authService.getNotGuestUser
      imageId  <- accountService.changeIcon(user.id, icon)
      newUser  <- Success(
        User(
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
      )
      _        <- updateSessionUser(newUser)
    } yield {
      newUser
    }
    toAjaxResponse(ret)
  }

  post("/profile/email_change_requests") {
    val ret = for {
      d        <- getJsonValue[UpdateMailAddressParams]
      json     <- jsonOptToTry(d)
      email    <- checkUtil.requireForForm("d.email", json.email)
      _        <- checkUtil.nonEmptyTrimmedSpacesForForm("d.email", email)
      user     <- authService.getNotGuestUser
      result   <- accountService.changeUserEmail(user.id, email)
      _        <- updateSessionUser(result)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  put("/profile/password") {
    val ret = for {
      d                <- getJsonValue[UpdatePasswordParams]
      json             <- jsonOptToTry(d)
      currentPassword  <- checkUtil.requireForForm("d.currentPassword", json.currentPassword)
      _                <- checkUtil.nonEmptyTrimmedSpacesForForm("d.currentPassword", currentPassword)
      newPassword      <- checkUtil.requireForForm("d.newPassword", json.newPassword)
      _                <- checkUtil.nonEmptyTrimmedSpacesForForm("d.newPassword", newPassword)
      user             <- authService.getNotGuestUser
      result           <- accountService.changeUserPassword(user.id, currentPassword, newPassword)
    } yield {}
    toAjaxResponse(ret)
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
      user      <- authService.getNotGuestUser
      result    <- datasetService.create(files, saveLocal.toBoolean, saveS3.toBoolean, name, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/datasets") {
    val ret = for {
      d      <- getJsonValue[SearchDatasetsParams]
      json   <- Success(d.getOrElse(SearchDatasetsParams()))
      _      <- checkUtil.contains("d.orderby", json.orderby, Seq("attribute"))
      _      <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _      <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      user   <- authService.getUser
      result <- datasetService.search(json.query, json.owners, json.groups, json.attributes, json.limit, json.offset, json.orderby, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/datasets/:datasetId") {
    val id = params("datasetId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("datasetId", id)
      user   <- authService.getUser
      result <- datasetService.get(id, user)
      _      <- SystemService.writeDatasetAccessLog(result.id, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/datasets/:datasetId/files") {
    val id = params("datasetId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("datasetId", id)
      files  <- checkUtil.requireForForm("files", fileMultiParams.get("files"))
      _      <- checkUtil.hasElement("files", files)
      _      <- checkUtil.invokeSeq(files) { x => checkUtil.checkNonZeroByteFile("files", x) }
      user   <- authService.getNotGuestUser
      result <- datasetService.addFiles(id, files, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _      <- checkUtil.validUuidForUrl("fileId", fileId)
      file   <- checkUtil.requireForForm("file", fileParams.get("file"))
      _      <- checkUtil.checkNonZeroByteFile("file", file)
      user   <- authService.getNotGuestUser
      result <- datasetService.updateFile(datasetId, fileId, file, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  put("/datasets/:datasetId/files/:fileId/metadata") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _      <- checkUtil.validUuidForUrl("fileId", fileId)
      d      <- getJsonValue[UpdateDatasetFileMetadataParams]
      json   <- jsonOptToTry(d)
      name   <- checkUtil.requireForForm("d.name", json.name)
      _      <- checkUtil.nonEmptyTrimmedSpacesForForm("d.name", name)
      _      <- checkUtil.requireForForm("d.description", json.description)
      user   <- authService.getNotGuestUser
      result <- datasetService.updateFileMetadata(datasetId, fileId, name, json.description.getOrElse(""), user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  delete("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _      <- checkUtil.validUuidForUrl("fileId", fileId)
      user   <- authService.getNotGuestUser
      result <- datasetService.deleteDatasetFile(datasetId, fileId, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  put("/datasets/:datasetId/metadata") {
    val datasetId = params("datasetId")
    val ret = for {
      _           <- checkUtil.validUuidForUrl("datasetId", datasetId)
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
      user        <- authService.getNotGuestUser
      result      <- datasetService.modifyDatasetMeta(datasetId, name, json.description, license, json.attributes, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/datasets/:datasetId/images") {
    val datasetId = params("datasetId")
    val ret = for {
      _       <- checkUtil.validUuidForUrl("datasetId", datasetId)
      d       <- getJsonValue[SearchRangeParams]
      json    <- Success(d.getOrElse(SearchRangeParams()))
      _       <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _       <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      user    <- authService.getNotGuestUser
      result  <- datasetService.getImages(datasetId, json.offset, json.limit, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/datasets/:datasetId/images") {
    val datasetId = params("datasetId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("datasetId", datasetId)
      images <- checkUtil.requireForForm("images", fileMultiParams.get("images")).map(_.filter(_.name.nonEmpty))
      _      <- checkUtil.hasElement("images", images)
      _      <- checkUtil.invokeSeq(images) { x => checkUtil.checkNonZeroByteFile("images", x) }
      user   <- authService.getNotGuestUser
      result <- datasetService.addImages(datasetId, images, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  put("/datasets/:datasetId/images/primary") {
    val datasetId = params("datasetId")
    val ret = for {
      _       <- checkUtil.validUuidForUrl("datasetId", datasetId)
      d       <- getJsonValue[ChangePrimaryImageParams]
      json    <- jsonOptToTry(d)
      imageId <- checkUtil.requireForForm("d.imageId", json.imageId)
      _       <- checkUtil.nonEmptyTrimmedSpacesForForm("d.imageId", imageId)
      _       <- checkUtil.validUuidForForm("d.imageId", imageId)
      user    <- authService.getNotGuestUser
      result  <- datasetService.changePrimaryImage(datasetId, imageId, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  delete("/datasets/:datasetId/images/:imageId") {
    val datasetId = params("datasetId")
    val imageId = params("imageId")
    val ret = for {
      _       <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _       <- checkUtil.validUuidForUrl("imageId", imageId)
      user    <- authService.getNotGuestUser
      result  <- datasetService.deleteImage(datasetId, imageId, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/datasets/:datasetId/acl") {
    val datasetId = params("datasetId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("datasetId", datasetId)
      d      <- getJsonValue[SearchRangeParams]
      json   <- Success(d.getOrElse(SearchRangeParams()))
      _      <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _      <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      user   <- authService.getNotGuestUser
      result <- datasetService.searchOwnerships(datasetId, json.offset, json.limit, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/datasets/:datasetId/acl") {
    val datasetId = params("datasetId")
    val ret = for {
      _       <- checkUtil.validUuidForUrl("datasetId", datasetId)
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
      user    <- authService.getNotGuestUser
      result  <- datasetService.setAccessControl(datasetId, json, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  put("/datasets/:datasetId/guest_access") {
    val datasetId = params("datasetId")
    val ret = for {
      _           <- checkUtil.validUuidForUrl("datasetId", datasetId)
      d           <- getJsonValue[UpdateDatasetGuestAccessParams]
      json        <- jsonOptToTry(d)
      accessLevel <- checkUtil.requireForForm("d.accessLevel", json.accessLevel)
      _           <- checkUtil.contains("d.accessLevel", accessLevel, Seq(0, 1, 2))
      user        <- authService.getNotGuestUser
      result      <- datasetService.setGuestAccessLevel(datasetId, accessLevel, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  delete("/datasets/:datasetId") {
    val datasetId = params("datasetId")
    val ret = for {
      _           <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user        <- authService.getNotGuestUser
      _           <- datasetService.deleteDataset(datasetId, user)
    } yield {}
    toAjaxResponse(ret)
  }

  put("/datasets/:datasetId/storage") {
    val datasetId = params("datasetId")
    val ret = for {
      _         <- checkUtil.validUuidForUrl("datasetId", datasetId)
      d         <- getJsonValue[DatasetStorageParams]
      json      <- jsonOptToTry(d)
      saveLocal <- checkUtil.requireForForm("d.saveLocal", json.saveLocal)
      saveS3    <- checkUtil.requireForForm("d.saveS3", json.saveS3)
      _         <- checkUtil.invoke("saveLocal, saveS3", saveLocal || saveS3, resource.getString(ResourceNames.CHECK_S3_OR_LOCAL))
      user      <- authService.getNotGuestUser
      result    <- datasetService.modifyDatasetStorage(datasetId, saveLocal, saveS3, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/datasets/:datasetId/copy") {
    val datasetId = params("datasetId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user   <- authService.getNotGuestUser
      result <- datasetService.copyDataset(datasetId, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/datasets/:datasetId/attributes/import") {
    val datasetId = params("datasetId")
    val ret = for {
      _    <- checkUtil.validUuidForUrl("datasetId", datasetId)
      file <- checkUtil.requireForForm("file", fileParams.get("file"))
      _    <- checkUtil.checkNonZeroByteFile("file", file)
      user <- authService.getNotGuestUser
      _    <- datasetService.importAttribute(datasetId, file, user)
    } yield {}
    toAjaxResponse(ret)
  }

  get("/datasets/:datasetId/attributes/export") {
    val datasetId = params("datasetId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user   <- authService.getNotGuestUser
      result <- datasetService.exportAttribute(datasetId, user)
    } yield {
      result
    }
    ret match {
      case Success(x) =>
          response.setHeader("Content-Disposition", "attachment; filename=" + x.getName)
          response.setHeader("Content-Type", "application/octet-stream;charset=binary")
          x
      case Failure(_) => toAjaxResponse(ret)
    }
  }

  put("/datasets/:datasetId/images/featured") {
    val datasetId = params("datasetId")
    val ret = for {
      _       <- checkUtil.validUuidForUrl("datasetId", datasetId)
      d       <- getJsonValue[ChangePrimaryImageParams]
      json    <- jsonOptToTry(d)
      imageId <- checkUtil.requireForForm("d.imageId", json.imageId)
      _       <- checkUtil.nonEmptyTrimmedSpacesForForm("d.imageId", imageId)
      _       <- checkUtil.validUuidForForm("d.imageId", imageId)
      user    <- authService.getNotGuestUser
      _       <- datasetService.changeFeaturedImage(datasetId, imageId, user)
    } yield {}
    toAjaxResponse(ret)
  }

  get("/datasets/:datasetId/files") {
    val datasetId = params("datasetId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("datasetId", datasetId)
      d      <- getJsonValue[SearchRangeParams]
      json   <- Success(d.getOrElse(SearchRangeParams(Some(dsmoq.AppConf.fileLimit), Some(0))))
      _      <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _      <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      user   <- authService.getUser
      result <- datasetService.getDatasetFiles(datasetId, json.limit, json.offset, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/datasets/:datasetId/files/:fileId/zippedfiles") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _      <- checkUtil.validUuidForUrl("fileId", fileId)
      d      <- getJsonValue[SearchRangeParams]
      json   <- Success(d.getOrElse(SearchRangeParams(Some(dsmoq.AppConf.fileLimit), Some(0))))
      _      <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _      <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      user   <- authService.getUser
      result <- datasetService.getDatasetZippedFiles(datasetId, fileId, json.limit, json.offset, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
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
      user   <- authService.getUser
      result <- groupService.search(json.query, json.user, json.limit, json.offset, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/groups/:groupId") {
    val groupId = params("groupId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("groupId", groupId)
      user   <- authService.getUser
      result <- groupService.get(groupId, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/groups/:groupId/members") {
    val groupId = params("groupId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("groupId", groupId)
      d      <- getJsonValue[GetGroupMembersParams]
      json   <- Success(d.getOrElse(GetGroupMembersParams()))
      _      <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _      <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      user   <- authService.getUser
      result <- groupService.getGroupMembers(groupId, json.limit, json.offset, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/groups") {
    val ret = for {
      d           <- getJsonValue[CreateGroupParams]
      json        <- jsonOptToTry(d)
      name        <- checkUtil.requireForForm("d.name", json.name)
      _           <- checkUtil.nonEmptyTrimmedSpacesForForm("d.name", name)
      description <- checkUtil.requireForForm("d.description", json.description)
      user        <- authService.getNotGuestUser
      result      <- groupService.createGroup(name, description, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  put("/groups/:groupId") {
    val groupId = params("groupId")
    val ret = for {
      _           <- checkUtil.validUuidForUrl("groupId", groupId)
      d           <- getJsonValue[UpdateGroupParams]
      json        <- jsonOptToTry(d)
      name        <- checkUtil.requireForForm("d.name", json.name)
      _           <- checkUtil.nonEmptyTrimmedSpacesForForm("d.name", name)
      description <- checkUtil.requireForForm("d.description", json.description)
      user        <- authService.getNotGuestUser
      result      <- groupService.updateGroup(groupId, name, description, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/groups/:groupId/images") {
    val groupId = params("groupId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("groupId", groupId)
      d      <- getJsonValue[SearchRangeParams]
      json   <- Success(d.getOrElse(SearchRangeParams()))
      _      <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _      <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      user   <- authService.getNotGuestUser
      result <- groupService.getImages(groupId, json.offset, json.limit, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/groups/:groupId/images") {
    val groupId = params("groupId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("groupId", groupId)
      images <- checkUtil.requireForForm("images", fileMultiParams.get("images")).map(_.filter(_.name.nonEmpty))
      _      <- checkUtil.hasElement("images", images)
      _      <- checkUtil.invokeSeq(images) { x => checkUtil.checkNonZeroByteFile("images", x) }
      user   <- authService.getNotGuestUser
      result <- groupService.addImages(groupId, images, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  put("/groups/:groupId/images/primary") {
    val groupId = params("groupId")
    val ret = for {
      _       <- checkUtil.validUuidForUrl("groupId", groupId)
      d       <- getJsonValue[ChangeGroupPrimaryImageParams]
      json    <- jsonOptToTry(d)
      imageId <- checkUtil.requireForForm("d.imageId", json.imageId)
      _       <- checkUtil.nonEmptyTrimmedSpacesForForm("d.imageId", imageId)
      _       <- checkUtil.validUuidForForm("d.imageId", imageId)
      user    <- authService.getNotGuestUser
      _       <- groupService.changePrimaryImage(groupId, imageId, user)
    } yield {}
    toAjaxResponse(ret)
  }

  delete("/groups/:groupId/images/:imageId") {
    val groupId = params("groupId")
    val imageId = params("imageId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("groupId", groupId)
      _      <- checkUtil.validUuidForUrl("imageId", imageId)
      user   <- authService.getNotGuestUser
      result <- groupService.deleteImage(groupId, imageId, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/groups/:groupId/members") {
    val groupId = params("groupId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("groupId", groupId)
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
      user    <- authService.getNotGuestUser
      result  <- groupService.addMembers(groupId, json, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  put("/groups/:groupId/members/:userId") {
    val groupId = params("groupId")
    val userId = params("userId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("groupId", groupId)
      _      <- checkUtil.validUuidForUrl("userId", userId)
      d      <- getJsonValue[SetGroupMemberRoleParams]
      json   <- jsonOptToTry(d)
      role   <- checkUtil.requireForForm("d.role", json.role)
      _      <- checkUtil.contains("d.role", role, Seq(0, 1, 2))
      user   <- authService.getNotGuestUser
      result <- groupService.updateMemberRole(groupId, userId, role, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  delete("/groups/:groupId/members/:userId") {
    val groupId = params("groupId")
    val userId = params("userId")
    val ret = for {
      _    <- checkUtil.validUuidForUrl("groupId", groupId)
      _    <- checkUtil.validUuidForUrl("userId", userId)
      user <- authService.getNotGuestUser
      _    <- groupService.removeMember(groupId, userId, user)
    } yield {}
    toAjaxResponse(ret)
  }

  delete("/groups/:groupId") {
    val groupId = params("groupId")
    val ret = for {
      _    <- checkUtil.validUuidForUrl("groupId", groupId)
      user <- authService.getNotGuestUser
      _    <- groupService.deleteGroup(groupId, user)
    } yield {}
    toAjaxResponse(ret)
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
    toAjaxResponse(ret)
  }

  get("/accounts") {
    val ret = for {
      result <- SystemService.getAccounts()
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/tags") {
    val ret = for {
      result <- SystemService.getTags()
    } yield {
      result
    }
    toAjaxResponse(ret)
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
    toAjaxResponse(ret)
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
    toAjaxResponse(ret)
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
    toAjaxResponse(ret)
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
    toAjaxResponse(ret)
  }

  get("/tasks/:taskId") {
    val taskId = params("taskId")
    val ret = for {
      _      <- checkUtil.validUuidForUrl("taskId", taskId)
      result <- TaskService.getStatus(taskId)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/statistics") {
    val ret = for {
      d      <- getJsonValue[StatisticsParams]
      json   <- Success(d.getOrElse(StatisticsParams()))
      result <- StatisticsService.getStatistics(json.from, json.to)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/message") {
    val ret = for {
      result <- SystemService.getMessage()
    } yield {
      result
    }
    toAjaxResponse(ret)
  }
  
  /**
   * セッション上のログイン情報を更新する。
   *
   * @param user 更新するユーザ情報
   * @return
   *        Success(Unit) 更新が成功した場合
   *        Failure(NullPointerException) 更新するユーザ情報がnullの場合
   */
  private def updateSessionUser(user: User): Try[Unit] = {
    // 直接ログインしてユーザーを更新した場合、セッション上のログイン情報を更新する
    if (!authService.hasAuthorizationHeader()) {
      authService.updateSessionUser(user)
    } else {
      Success(Unit)
    }
  }

  private def toAjaxResponse[A](result: Try[A]) = result match {
    case Success(Unit) => AjaxResponse("OK")
    case Success(x) => AjaxResponse("OK", x)
    case Failure(e) =>
     e match {
      case e: AccessDeniedException => AjaxResponse("Unauthorized")
      case e: NotAuthorizedException => AjaxResponse("Unauthorized")
      case e: NotFoundException => AjaxResponse("NotFound")
      case InputCheckException(name, message, false) => AjaxResponse("Illegal Argument", CheckError(name, message))
      case InputCheckException(name, message, true) => AjaxResponse("Illegal Argument", CheckError(name, message))
      case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
      case e: BadRequestException => AjaxResponse("BadRequest", e.getMessage)
      case _ =>
        log(e.getMessage, e)
        AjaxResponse("NG")
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

case class Pipe[A](x: A)
{
  def |>[B](f: A => B) = f.apply(x)
}
