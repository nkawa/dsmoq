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
import dsmoq.exceptions.{BadRequestException, InputCheckException, InputValidationException, NotFoundException, NotAuthorizedException}
import dsmoq.logic.CheckUtil
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
    val ret = for {
      d        <- getJsonValue[SigninParams]
      json     <- jsonOptToTry(d)
      id       <- CheckUtil.require("d.id", json.id, false)
      _        <- CheckUtil.nonEmpty("d.id", id, false)
      password <- CheckUtil.require("d.password", json.password, false)
      _        <- CheckUtil.nonEmpty("d.password", password, false)
      result   <- AccountService.findUserByIdAndPassword(id, password)
    } yield {
      result
    }
    ret match {
      case Success(x) =>
        setSignedInUser(x)
        AjaxResponse("OK", x)
      case Failure(e) => {
        clearSession()
        e match {
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case InputCheckException(name, message, false) => AjaxResponse("Illegal Argument", CheckError(name, message))
          case _ => AjaxResponse("NG")
        }
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
    val ret = for {
      user   <- getUser(request, true)
      result <- AccountService.getUserProfile(user)
    } yield {
      result
    }
    ret |> toAjaxResponse
  }

  put("/profile") {
    val ret = for {
      d        <- getJsonValue[UpdateProfileParams]
      json     <- jsonOptToTry(d)
      name     <- CheckUtil.require("d.name", json.name, false)
      fullname <- CheckUtil.require("d.fullname", json.fullname, false)
      user     <- getUser(request, false)
      _        <- CheckUtil.googleUserChangeName("d.name", user.id)
      _        <- CheckUtil.existsSameName("d.name", user.id, name)
      result   <- AccountService.updateUserProfile(user.id, name, fullname, json.organization, json.title, json.description)
    } yield {
      // 直接ログインしてユーザーを更新した場合、セッション上のログイン情報を更新する
      if (!hasAuthorizationHeader(request)) {
        setSignedInUser(result)
      }
      result
    }
    toAjaxResponse(ret)
  }

  post("/profile/image") {
    val ret = for {
      icon     <- CheckUtil.require("icon", fileParams.get("icon"), false)
      user     <- getUser(request, false)
      imageId  <- AccountService.changeIcon(user.id, icon)
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
    }
    toAjaxResponse(ret)
  }

  post("/profile/email_change_requests") {
    val ret = for {
      d        <- getJsonValue[UpdateMailAddressParams]
      json     <- jsonOptToTry(d)
      email    <- CheckUtil.require("d.email", json.email, false)
      _        <- CheckUtil.nonEmpty("d.email", email, false)
      user     <- getUser(request, false)
      _        <- CheckUtil.googleUser("user", user.id, "GoogleユーザーアカウントのEmailアドレスは変更できません")
      _        <- CheckUtil.existsSameEmail("d.email", user.id, email)
      result   <- AccountService.changeUserEmail(user.id, email)
    } yield {
      // 直接ログインしてユーザーを更新した場合、セッション上のログイン情報を更新する
      if (!hasAuthorizationHeader(request)) {
        setSignedInUser(result)
      }
      result
    }
    toAjaxResponse(ret)
  }

  put("/profile/password") {
    val ret = for {
      d                <- getJsonValue[UpdatePasswordParams]
      json             <- jsonOptToTry(d)
      currentPassword  <- CheckUtil.require("d.currentPassword", json.currentPassword, false)
      _                <- CheckUtil.nonEmpty("d.currentPassword", currentPassword, false)
      newPassword      <- CheckUtil.require("d.newPassword", json.newPassword, false)
      _                <- CheckUtil.nonEmpty("d.newPassword", newPassword, false)
      user             <- getUser(request, false)
      _                <- CheckUtil.googleUser("user", user.id, "Googleユーザーアカウントのパスワードは変更できません")
      p                <- CheckUtil.passwordCheck(user.id, currentPassword)
      result           <- AccountService.changeUserPassword(user.id, p, newPassword)
    } yield {}
    toAjaxResponse(ret)
  }

  // --------------------------------------------------------------------------
  // dataset api
  // --------------------------------------------------------------------------
  post("/datasets") {
    val ret = for {
      files     <- CheckUtil.require("file[]", fileMultiParams.get("file[]"), false).map(_.filter(_.name.nonEmpty))
      _         <- CheckUtil.hasElement("file[]", files)
      saveLocal <- CheckUtil.require("saveLocal", params.get("saveLocal"), false)
      saveS3    <- CheckUtil.require("saveS3", params.get("saveS3"), false)
      name      <- CheckUtil.require("name", params.get("name"), false)
      _         <- CheckUtil.nonEmpty("name", name, false)
      _         <- CheckUtil.range("saveLocal", saveLocal, Seq("true", "false"))
      _         <- CheckUtil.range("saveS3", saveS3, Seq("true", "false"))
      _         <- CheckUtil.check("saveLocal, saveS3", saveLocal.toBoolean || saveS3.toBoolean, "ローカルかS3のいずれかの保存先にチェックを付けてください")
      user      <- getUser(request, false)
      result    <- DatasetService.create(files, saveLocal.toBoolean, saveS3.toBoolean, name, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/datasets") {
    val ret = for {
      d      <- getJsonValue[SearchDatasetsParams]
      json   <- Success(d.getOrElse(SearchDatasetsParams()))
      _      <- CheckUtil.range("d.orderby", json.orderby, Seq("attribute"))
      user   <- getUser(request, true)
      result <- DatasetService.search(json.query, json.owners, json.groups, json.attributes, json.limit, json.offset, json.orderby, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/datasets/:datasetId") {
    val id = params("datasetId")
    val ret = for {
      _      <- CheckUtil.uuid("datasetId", id, true)
      user   <- getUser(request, true)
      result <- DatasetService.get(id, user)
      _      <- SystemService.writeDatasetAccessLog(result.id, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/datasets/:datasetId/files") {
    val id = params("datasetId")
    val ret = for {
      _      <- CheckUtil.uuid("datasetId", id, true)
      files  <- CheckUtil.require("files", fileMultiParams.get("files"), false)
      _      <- CheckUtil.hasElement("files", files)
      user   <- getUser(request, false)
      result <- DatasetService.addFiles(id, files, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val ret = for {
      _      <- CheckUtil.uuid("datasetId", datasetId, true)
      _      <- CheckUtil.uuid("fileId", fileId, true)
      file   <- CheckUtil.require("file", fileParams.get("file"), false)
      _      <- CheckUtil.check("file", file.getSize > 0, "空ファイルが指定されました")
      user   <- getUser(request, false)
      result <- DatasetService.updateFile(datasetId, fileId, file, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  put("/datasets/:datasetId/files/:fileId/metadata") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val ret = for {
      _      <- CheckUtil.uuid("datasetId", datasetId, true)
      _      <- CheckUtil.uuid("fileId", fileId, true)
      d      <- getJsonValue[UpdateDatasetFileMetadataParams]
      json   <- jsonOptToTry(d)
      name   <- CheckUtil.require("d.name", json.name, false)
      _      <- CheckUtil.nonEmpty("d.name", name, false)
      user   <- getUser(request, false)
      result <- DatasetService.updateFileMetadata(datasetId, fileId, name, json.description.getOrElse(""), user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  delete("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val ret = for {
      _      <- CheckUtil.uuid("datasetId", datasetId, true)
      _      <- CheckUtil.uuid("fileId", fileId, true)
      user   <- getUser(request, false)
      result <- DatasetService.deleteDatasetFile(datasetId, fileId, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  put("/datasets/:datasetId/metadata") {
    val datasetId = params("datasetId")
    val ret = for {
      _       <- CheckUtil.uuid("datasetId", datasetId, true)
      d       <- getJsonValue[UpdateDatasetMetaParams]
      json    <- jsonOptToTry(d)
      name    <- CheckUtil.require("d.name", json.name, false)
      _       <- CheckUtil.nonEmpty("d.name", name, false)
      license <- CheckUtil.require("d.license", json.license, false)
      _       <- CheckUtil.nonEmpty("d.license", license, false)
      _       <- CheckUtil.uuid("d.license", license, false)
      _       <- CheckUtil.check("d.attribute", json.attributes.filter(_.name == "featured").length < 2, "featured 属性は一つまでにしてください")
      user    <- getUser(request, false)
      result  <- DatasetService.modifyDatasetMeta(datasetId, name, json.description, license, json.attributes, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/datasets/:datasetId/images") {
    val datasetId = params("datasetId")
    val ret = for {
      _       <- CheckUtil.uuid("datasetId", datasetId, true)
      d       <- getJsonValue[SearchRangeParams]
      json    <- Success(d.getOrElse(SearchRangeParams()))
      user    <- getUser(request, false)
      result  <- DatasetService.getImages(datasetId, json.offset, json.limit, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/datasets/:datasetId/images") {
    val datasetId = params("datasetId")
    val ret = for {
      _      <- CheckUtil.uuid("datasetId", datasetId, true)
      images <- CheckUtil.require("images", fileMultiParams.get("images"), false).map(_.filter(_.name.nonEmpty))
      _      <- CheckUtil.hasElement("images", images)
      user   <- getUser(request, false)
      result <- DatasetService.addImages(datasetId, images, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  put("/datasets/:datasetId/images/primary") {
    val datasetId = params("datasetId")
    val ret = for {
      _       <- CheckUtil.uuid("datasetId", datasetId, true)
      d       <- getJsonValue[ChangePrimaryImageParams]
      json    <- jsonOptToTry(d)
      imageId <- CheckUtil.require("d.imageId", json.imageId, false)
      _       <- CheckUtil.nonEmpty("d.imageId", imageId, false)
      _       <- CheckUtil.uuid("d.imageId", imageId, false)
      user    <- getUser(request, false)
      result  <- DatasetService.changePrimaryImage(datasetId, imageId, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  delete("/datasets/:datasetId/images/:imageId") {
    val datasetId = params("datasetId")
    val imageId = params("imageId")
    val ret = for {
      _       <- CheckUtil.uuid("datasetId", datasetId, true)
      _       <- CheckUtil.uuid("imageId", imageId, true)
      user    <- getUser(request, false)
      result  <- DatasetService.deleteImage(datasetId, imageId, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/datasets/:datasetId/acl") {
    val datasetId = params("datasetId")
    val ret = for {
      _      <- CheckUtil.uuid("datasetId", datasetId, true)
      d      <- getJsonValue[SearchRangeParams]
      json   <- Success(d.getOrElse(SearchRangeParams()))
      user   <- getUser(request, false)
      result <- DatasetService.searchOwnerships(datasetId, json.offset, json.limit, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/datasets/:datasetId/acl") {
    val datasetId = params("datasetId")
    val ret = for {
      _       <- CheckUtil.uuid("datasetId", datasetId, true)
      d       <- getJsonValue[List[DataSetAccessControlItem]]
      json    <- jsonOptToTry(d)
      _       <- CheckUtil.seqCheck(json) { x =>
        for {
          _  <- CheckUtil.nonEmpty("d.id", x.id, false)
          _  <- CheckUtil.uuid("d.id", x.id, false)
          _  <- CheckUtil.range("d.ownerType", x.ownerType, Seq(1, 2))
          _  <- CheckUtil.range("d.accessLevel", x.accessLevel, Seq(0, 1, 2, 3))
        } yield {}
      }
      user    <- getUser(request, false)
      result  <- DatasetService.setAccessControl(datasetId, json, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  put("/datasets/:datasetId/guest_access") {
    val datasetId = params("datasetId")
    val ret = for {
      _           <- CheckUtil.uuid("datasetId", datasetId, true)
      d           <- getJsonValue[UpdateDatasetGuestAccessParams]
      json        <- jsonOptToTry(d)
      accessLevel <- CheckUtil.require("d.accessLevel", json.accessLevel, false)
      _           <- CheckUtil.range("d.accessLevel", accessLevel, Seq(0, 1, 2))
      user        <- getUser(request, false)
      result      <- DatasetService.setGuestAccessLevel(datasetId, accessLevel, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  delete("/datasets/:datasetId") {
    val datasetId = params("datasetId")
    val ret = for {
      _           <- CheckUtil.uuid("datasetId", datasetId, true)
      user        <- getUser(request, false)
      _           <- DatasetService.deleteDataset(datasetId, user)
    } yield {}
    toAjaxResponse(ret)
  }

  put("/datasets/:datasetId/storage") {
    val datasetId = params("datasetId")
    val ret = for {
      _         <- CheckUtil.uuid("datasetId", datasetId, true)
      d         <- getJsonValue[DatasetStorageParams]
      json      <- jsonOptToTry(d)
      saveLocal <- CheckUtil.require("d.saveLocal", json.saveLocal, false)
      saveS3    <- CheckUtil.require("d.saveS3", json.saveS3, false)
      _         <- CheckUtil.check("saveLocal, saveS3", saveLocal || saveS3, "ローカルかS3のいずれかの保存先にチェックを付けてください")
      user      <- getUser(request, false)
      result    <- DatasetService.modifyDatasetStorage(datasetId, saveLocal, saveS3, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/datasets/:datasetId/copy") {
    val datasetId = params("datasetId")
    val ret = for {
      _      <- CheckUtil.uuid("datasetId", datasetId, true)
      user   <- getUser(request, false)
      result <- DatasetService.copyDataset(datasetId, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/datasets/:datasetId/attributes/import") {
    val datasetId = params("datasetId")
    val ret = for {
      _    <- CheckUtil.uuid("datasetId", datasetId, true)
      file <- CheckUtil.require("file", fileParams.get("file"), false)
      _    <- CheckUtil.check("file", file.getSize > 0, "空ファイルが指定されました")
      user <- getUser(request, false)
      _    <- DatasetService.importAttribute(datasetId, file, user)
    } yield {}
    toAjaxResponse(ret)
  }

  get("/datasets/:datasetId/attributes/export") {
    val datasetId = params("datasetId")
    val ret = for {
      _      <- CheckUtil.uuid("datasetId", datasetId, true)
      user   <- getUser(request, false)
      result <- DatasetService.exportAttribute(datasetId, user)
    } yield {
      result
    }
    ret match {
      case Success(x) =>
          response.setHeader("Content-Disposition", "attachment; filename=" + x.getName)
          response.setHeader("Content-Type", "application/octet-stream;charset=binary")
          x
      case Failure(e) => halt(status = 403, reason = "Forbidden", body="Forbidden")
    }
  }

  put("/datasets/:datasetId/images/featured") {
    val datasetId = params("datasetId")
    val ret = for {
      _       <- CheckUtil.uuid("datasetId", datasetId, true)
      d       <- getJsonValue[ChangePrimaryImageParams]
      json    <- jsonOptToTry(d)
      imageId <- CheckUtil.require("d.imageId", json.imageId, false)
      _       <- CheckUtil.nonEmpty("d.imageId", imageId, false)
      _       <- CheckUtil.uuid("d.imageId", imageId, false)
      user    <- getUser(request, false)
      _       <- DatasetService.changeFeaturedImage(datasetId, imageId, user)
    } yield {}
    toAjaxResponse(ret)
  }

  get("/datasets/:datasetId/files") {
    val datasetId = params("datasetId")
    val ret = for {
      _      <- CheckUtil.uuid("datasetId", datasetId, true)
      d      <- getJsonValue[SearchRangeParams]
      json   <- Success(d.getOrElse(SearchRangeParams(Some(dsmoq.AppConf.fileLimit), Some(0))))
      user   <- getUser(request, true)
      result <- DatasetService.getDatasetFiles(datasetId, json.limit, json.offset, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/datasets/:datasetId/files/:fileId/zippedfiles") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val ret = for {
      _      <- CheckUtil.uuid("datasetId", datasetId, true)
      _      <- CheckUtil.uuid("fileId", fileId, true)
      d      <- getJsonValue[SearchRangeParams]
      json   <- Success(d.getOrElse(SearchRangeParams(Some(dsmoq.AppConf.fileLimit), Some(0))))
      user   <- getUser(request, true)
      result <- DatasetService.getDatasetZippedFiles(datasetId, fileId, json.limit, json.offset, user)
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
      _      <- CheckUtil.uuid("d.user", json.user, false)
      user   <- getUser(request, true)
      result <- GroupService.search(json.query, json.user, json.limit, json.offset, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/groups/:groupId") {
    val groupId = params("groupId")
    val ret = for {
      _      <- CheckUtil.uuid("groupId", groupId, true)
      user   <- getUser(request, true)
      result <- GroupService.get(groupId, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/groups/:groupId/members") {
    val groupId = params("groupId")
    val ret = for {
      _      <- CheckUtil.uuid("groupId", groupId, true)
      d      <- getJsonValue[GetGroupMembersParams]
      json   <- Success(d.getOrElse(GetGroupMembersParams()))
      user   <- getUser(request, true)
      result <- GroupService.getGroupMembers(groupId, json.limit, json.offset, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/groups") {
    val ret = for {
      d           <- getJsonValue[CreateGroupParams]
      json        <- jsonOptToTry(d)
      name        <- CheckUtil.require("d.name", json.name, false)
      _           <- CheckUtil.nonEmpty("d.name", name, false)
      description <- CheckUtil.require("d.description", json.description, false)
      user        <- getUser(request, false)
      _           <- CheckUtil.existsSameNameGroup("d.name", name)
      result      <- GroupService.createGroup(name, description, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  put("/groups/:groupId") {
    val groupId = params("groupId")
    val ret = for {
      _           <- CheckUtil.uuid("groupId", groupId, true)
      d           <- getJsonValue[UpdateGroupParams]
      json        <- jsonOptToTry(d)
      name        <- CheckUtil.require("d.name", json.name, false)
      _           <- CheckUtil.nonEmpty("d.name", name, false)
      description <- CheckUtil.require("d.description", json.description, false)
      user        <- getUser(request, false)
      _           <- CheckUtil.existsSameNameGroup("d.name", name)
      result      <- GroupService.updateGroup(groupId, name, description, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/groups/:groupId/images") {
    val groupId = params("groupId")
    val ret = for {
      _      <- CheckUtil.uuid("groupId", groupId, true)
      d      <- getJsonValue[SearchRangeParams]
      json   <- Success(d.getOrElse(SearchRangeParams()))
      user   <- getUser(request, false)
      result <- GroupService.getImages(groupId, json.offset, json.limit, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/groups/:groupId/images") {
    val groupId = params("groupId")
    val ret = for {
      _      <- CheckUtil.uuid("groupId", groupId, true)
      images <- CheckUtil.require("images", fileMultiParams.get("images"), false).map(_.filter(_.name.nonEmpty))
      _      <- CheckUtil.hasElement("images", images)
      user   <- getUser(request, false)
      result <- GroupService.addImages(groupId, images, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  put("/groups/:groupId/images/primary") {
    val groupId = params("groupId")
    val ret = for {
      _       <- CheckUtil.uuid("groupId", groupId, true)
      d       <- getJsonValue[ChangeGroupPrimaryImageParams]
      json    <- jsonOptToTry(d)
      imageId <- CheckUtil.require("d.imageId", json.imageId, false)
      _       <- CheckUtil.nonEmpty("d.imageId", imageId, false)
      _       <- CheckUtil.uuid("d.imageId", imageId, false)
      user    <- getUser(request, false)
      _       <- GroupService.changePrimaryImage(groupId, imageId, user)
    } yield {}
    toAjaxResponse(ret)
  }

  delete("/groups/:groupId/images/:imageId") {
    val groupId = params("groupId")
    val imageId = params("imageId")
    val ret = for {
      _      <- CheckUtil.uuid("groupId", groupId, true)
      _      <- CheckUtil.uuid("imageId", imageId, true)
      user   <- getUser(request, false)
      result <- GroupService.deleteImage(groupId, imageId, user)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  post("/groups/:groupId/members") {
    val groupId = params("groupId")
    val ret = for {
      _      <- CheckUtil.uuid("groupId", groupId, true)
      d       <- getJsonValue[List[GroupMember]]
      json    <- jsonOptToTry(d)
      _       <- CheckUtil.seqCheck(json) { x =>
        for {
          _      <- CheckUtil.nonEmpty("d.userId", x.userId, false)
          _      <- CheckUtil.uuid("d.userId", x.userId, false)
          _      <- CheckUtil.range("d.role", x.role, Seq(0, 1, 2))
        } yield {}
      }
      user    <- getUser(request, false)
      _       <- GroupService.addMembers(groupId, json, user)
    } yield {}
    toAjaxResponse(ret)
  }

  put("/groups/:groupId/members/:userId") {
    val groupId = params("groupId")
    val userId = params("userId")
    val ret = for {
      _    <- CheckUtil.uuid("groupId", groupId, true)
      _    <- CheckUtil.uuid("userId", userId, true)
      d    <- getJsonValue[SetGroupMemberRoleParams]
      json <- jsonOptToTry(d)
      role <- CheckUtil.require("d.role", json.role, false)
      _    <- CheckUtil.range("d.role", role, Seq(0, 1, 2))
      user <- getUser(request, false)
      _    <- GroupService.updateMemberRole(groupId, userId, role, user)
    } yield {}
    toAjaxResponse(ret)
  }

  delete("/groups/:groupId/members/:userId") {
    val groupId = params("groupId")
    val userId = params("userId")
    val ret = for {
      _    <- CheckUtil.uuid("groupId", groupId, true)
      _    <- CheckUtil.uuid("userId", userId, true)
      user <- getUser(request, false)
      _    <- GroupService.removeMember(groupId, userId, user)
    } yield {}
    toAjaxResponse(ret)
  }

  delete("/groups/:groupId") {
    val groupId = params("groupId")
    val ret = for {
      _    <- CheckUtil.uuid("groupId", groupId, true)
      user <- getUser(request, false)
      _    <- GroupService.deleteGroup(groupId, user)
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
      _      <- CheckUtil.seqCheck(json.excludeIds) { x => CheckUtil.uuid("d.excludeId", x, false) }
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
      result <- SystemService.getAttributes(json.query, json.limit, json.offset)
    } yield {
      result
    }
    toAjaxResponse(ret)
  }

  get("/tasks/:taskId") {
    val taskId = params("taskId")
    val ret = for {
      _      <- CheckUtil.uuid("taskId", taskId, true)
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
  
  private def toAjaxResponse[A](result: Try[A]) = result match {
    case Success(Unit) => AjaxResponse("OK")
    case Success(x) => AjaxResponse("OK", x)
    case Failure(e) =>
     e match {
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

  private def jsonOptToTry[T](obj: Option[T]): Try[T] = {
    obj match {
      case None => Failure(new InputCheckException("d", "JSONパラメータは必須です", false))
      case Some(x) => Success(x)
    }
  }

  private def getJsonValue[T](implicit m: Manifest[T]): Try[Option[T]] = {
    params.get("d") match {
      case None => Success(None)
      case Some(x) => JsonMethods.parse(x).extractOpt[T] match {
        case None => Failure(new InputCheckException("d", "JSONの形式が不正です", false))
        case Some(obj) => Success(Some(obj))
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
