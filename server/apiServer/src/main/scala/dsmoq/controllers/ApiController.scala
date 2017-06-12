package dsmoq.controllers

import java.util.ResourceBundle

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.json4s.DefaultFormats
import org.json4s.Formats
import org.json4s.jackson.JsonMethods
import org.json4s.jvalue2extractable
import org.json4s.string2JsonInput
import org.scalatra.ActionResult
import org.scalatra.BadRequest
import org.scalatra.InternalServerError
import org.scalatra.NotFound
import org.scalatra.Ok
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.servlet.FileUploadSupport
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.AppConf
import dsmoq.ResourceNames
import dsmoq.controllers.AjaxResponse.toActionResult
import dsmoq.controllers.json.ChangeGroupPrimaryImageParams
import dsmoq.controllers.json.ChangePrimaryAppParams
import dsmoq.controllers.json.ChangePrimaryImageParams
import dsmoq.controllers.json.CreateDatasetQueryParams
import dsmoq.controllers.json.CreateGroupParams
import dsmoq.controllers.json.DatasetStorageParams
import dsmoq.controllers.json.GetGroupMembersParams
import dsmoq.controllers.json.SearchAppsParams
import dsmoq.controllers.json.SearchDatasetParams
import dsmoq.controllers.json.SearchDatasetParamsSerializer
import dsmoq.controllers.json.SearchGroupsParams
import dsmoq.controllers.json.SearchRangeParams
import dsmoq.controllers.json.SetGroupMemberRoleParams
import dsmoq.controllers.json.SigninParams
import dsmoq.controllers.json.StatisticsParams
import dsmoq.controllers.json.SuggestApiParams
import dsmoq.controllers.json.UpdateDatasetFileMetadataParams
import dsmoq.controllers.json.UpdateDatasetGuestAccessParams
import dsmoq.controllers.json.UpdateDatasetMetaParams
import dsmoq.controllers.json.UpdateGroupParams
import dsmoq.controllers.json.UpdateMailAddressParams
import dsmoq.controllers.json.UpdatePasswordParams
import dsmoq.controllers.json.UpdateProfileParams
import dsmoq.controllers.json.UserAndGroupSuggestApiParams
import dsmoq.exceptions.AccessDeniedException
import dsmoq.exceptions.BadRequestException
import dsmoq.exceptions.InputCheckException
import dsmoq.exceptions.InputValidationException
import dsmoq.exceptions.NotAuthorizedException
import dsmoq.exceptions.NotFoundException
import dsmoq.logic.CheckUtil
import dsmoq.services.AccountService
import dsmoq.services.DataSetAccessControlItem
import dsmoq.services.DatasetService
import dsmoq.services.GroupMember
import dsmoq.services.GroupService
import dsmoq.services.QueryService
import dsmoq.services.StatisticsService
import dsmoq.services.SystemService
import dsmoq.services.TaskService
import dsmoq.services.User
import dsmoq.services.json.DatasetData
import dsmoq.services.json.RangeSlice
import dsmoq.services.json.SearchDatasetCondition
import dsmoq.services.json.SearchDatasetConditionSerializer

/**
 * /apiにマッピングされるサーブレットクラス。
 * WebAPIを提供する。
 *
 * @param resource リソースバンドル
 */
class ApiController(
  val resource: ResourceBundle
) extends ScalatraServlet with JacksonJsonSupport with FileUploadSupport with LazyLogging with AuthTrait {

  protected implicit val jsonFormats: Formats = DefaultFormats + DateTimeSerializer +
    SearchDatasetConditionSerializer + SearchDatasetParamsSerializer

  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("API_LOG")

  /**
   * AccountServiceのインスタンス
   */
  val accountService = new AccountService(resource)

  /**
   * CheckUtilのインスタンス
   */
  val checkUtil = new CheckUtil(resource)

  /**
   * DatasetServiceのインスタンス
   */
  val datasetService = new DatasetService(resource)

  /**
   * GroupServiceのインスタンス
   */
  val groupService = new GroupService(resource)

  /**
   * QueryServiceのインスタンス
   */
  val queryService = new QueryService(resource)

  /** HTTPレスポンスのGuestヘッダ名 */
  val GUEST_HEADER_NAME = "X-Dsmoq-Guest"

  before() {
    contentType = formats("json")
  }

  // 各ハンドラの後処理として、共通レスポンスヘッダの設定を行う
  after() {
    if (!hasAuthorizationHeader) {
      // APIキーでの認証でない(セッションでの認証)なら、isGuestヘッダを付与する
      try {
        response.setHeader(GUEST_HEADER_NAME, getUserFromSession.isGuest.toString)
      } catch {
        case e: Exception => {
          // エラー時はログにのみ残し、レスポンスには反映しない
          logger.error(LOG_MARKER, "error occurred during set guest header.", e)
        }
      }
    }
  }

  // いずれにもマッチしないGETリクエスト
  get("/*") {
    NotFound(AjaxResponse("NotFound")) // 404
  }

  // いずれにもマッチしないPUTリクエスト
  put("/*") {
    NotFound(AjaxResponse("NotFound")) // 404
  }

  // いずれにもマッチしないPOSTリクエスト
  post("/*") {
    NotFound(AjaxResponse("NotFound")) // 404
  }

  // いずれにもマッチしないDELETEリクエスト
  delete("/*") {
    NotFound(AjaxResponse("NotFound")) // 404
  }

  // --------------------------------------------------------------------------
  // auth api
  // --------------------------------------------------------------------------
  // サインイン
  post("/signin") {
    val ret = for {
      d <- getJsonValueFromParams[SigninParams]
      json <- jsonOptToTry(d)
      id <- checkUtil.requireForForm("d.id", json.id)
      _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.id", id)
      password <- checkUtil.requireForForm("d.password", json.password)
      _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.password", password)
      result <- accountService.findUserByIdAndPassword(id, password)
      _ <- updateSessionUser(result)
    } yield {
      result
    }
    ret match {
      case Success(user) =>
        AjaxResponse("OK", user)
      case Failure(e) => {
        logger.error(LOG_MARKER, e.getMessage, e)
        clearSession()
        e match {
          case e: BadRequestException => BadRequest(AjaxResponse("BadRequest", e.getMessage())) // 400
          case e: InputCheckException if !e.isUrlParam => {
            BadRequest(AjaxResponse("Illegal Argument", CheckError(e.target, e.message))) // 400
          }
          case _ => InternalServerError(AjaxResponse("NG")) // 500
        }
      }
    }
  }

  // サインアウト
  post("/signout") {
    clearSession()
    AjaxResponse("OK", AppConf.guestUser)
  }

  // --------------------------------------------------------------------------
  // profile api
  // --------------------------------------------------------------------------
  // プロフィール取得
  get("/profile") {
    val ret = for {
      user <- getUser(allowGuest = true)
      result <- accountService.getUserProfile(user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // プロフィール更新
  put("/profile") {
    val ret = for {
      d <- getJsonValueFromParams[UpdateProfileParams]
      json <- jsonOptToTry(d)
      name <- checkUtil.requireForForm("d.name", json.name)
      _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.name", name)
      fullname <- checkUtil.requireForForm("d.fullname", json.fullname)
      _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.fullname", fullname)
      user <- getUser(allowGuest = false)
      result <- accountService.updateUserProfile(
        id = user.id,
        name = json.name,
        fullname = json.fullname,
        organization = json.organization,
        title = json.title,
        description = json.description
      )
      _ <- updateSessionUser(result)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // プロフィール画像更新
  post("/profile/image") {
    val ret = for {
      icon <- checkUtil.requireForForm("icon", fileParams.get("icon"))
      _ <- checkUtil.checkNonZeroByteFile("icon", icon)
      user <- getUser(allowGuest = false)
      imageId <- accountService.changeIcon(user.id, icon)
      newUser = user.copy(image = dsmoq.AppConf.imageDownloadRoot + "user/" + user.id + "/" + imageId)
      _ <- updateSessionUser(newUser)
    } yield {
      newUser
    }
    toActionResult(ret)
  }

  // メールアドレス更新
  post("/profile/email_change_requests") {
    val ret = for {
      d <- getJsonValueFromParams[UpdateMailAddressParams]
      json <- jsonOptToTry(d)
      email <- checkUtil.requireForForm("d.email", json.email)
      _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.email", email)
      user <- getUser(allowGuest = false)
      result <- accountService.changeUserEmail(user.id, email)
      _ <- updateSessionUser(result)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // パスワード変更
  put("/profile/password") {
    val ret = for {
      d <- getJsonValueFromParams[UpdatePasswordParams]
      json <- jsonOptToTry(d)
      currentPassword <- checkUtil.requireForForm("d.currentPassword", json.currentPassword)
      _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.currentPassword", currentPassword)
      newPassword <- checkUtil.requireForForm("d.newPassword", json.newPassword)
      _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.newPassword", newPassword)
      user <- getUser(allowGuest = false)
      result <- accountService.changeUserPassword(user.id, currentPassword, newPassword)
    } yield {}
    toActionResult(ret)
  }

  // --------------------------------------------------------------------------
  // dataset api
  // --------------------------------------------------------------------------
  // データセット作成
  post("/datasets") {
    val files = fileMultiParams.get("file[]").getOrElse(Seq.empty).filter(_.name.nonEmpty)
    val ret = for {
      saveLocal <- checkUtil.requireForForm("saveLocal", params.get("saveLocal"))
      saveS3 <- checkUtil.requireForForm("saveS3", params.get("saveS3"))
      name <- checkUtil.requireForForm("name", params.get("name"))
      _ <- checkUtil.nonEmptyTrimmedSpacesForForm("name", name)
      _ <- checkUtil.contains("saveLocal", saveLocal, Seq("true", "false"))
      _ <- checkUtil.contains("saveS3", saveS3, Seq("true", "false"))
      _ <- checkUtil.invokeSeq(files) { x => checkUtil.checkNonZeroByteFile("file[]", x) }
      _ <- checkUtil.invoke(
        "saveLocal, saveS3",
        saveLocal.toBoolean || saveS3.toBoolean,
        resource.getString(ResourceNames.CHECK_S3_OR_LOCAL)
      )
      user <- getUser(allowGuest = false)
      result <- datasetService.create(files, saveLocal.toBoolean, saveS3.toBoolean, name, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセット一覧取得
  get("/datasets") {
    val ret = for {
      d <- getJsonValueFromParams[SearchDatasetParams]
      result <- getDatasets(d)
    } yield {
      result
    }
    toActionResult(ret)
  }

  /**
   * データセットを検索する。
   *
   * @param d 検索条件
   * @return 検索結果
   */
  def getDatasets(d: Option[SearchDatasetParams]): Try[RangeSlice[DatasetData.DatasetsSummary]] = {
    d.getOrElse(SearchDatasetParams()) match {
      case x: SearchDatasetParams.Condition => getDatasetsWithCondition(x)
      case x: SearchDatasetParams.Params => getDatasetsWithParams(x)
    }
  }

  /**
   * SearchCondition形式の検索条件を用いてデータセットを検索する。
   *
   * @param json 検索条件
   * @return 検索結果
   */
  def getDatasetsWithCondition(
    json: SearchDatasetParams.Condition
  ): Try[RangeSlice[DatasetData.DatasetsSummary]] = {
    for {
      _ <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _ <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      user <- getUser(allowGuest = true)
      result <- datasetService.search(
        query = json.query,
        limit = json.limit,
        offset = json.offset,
        user = user
      )
    } yield {
      result
    }
  }

  /**
   * query/owners/groups/attirbutes形式の検索条件を用いてデータセットを検索する。
   *
   * @param json 検索条件
   * @return 検索結果
   */
  def getDatasetsWithParams(
    json: SearchDatasetParams.Params
  ): Try[RangeSlice[DatasetData.DatasetsSummary]] = {
    for {
      _ <- checkUtil.contains("d.orderby", json.orderby, Seq("attribute"))
      _ <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _ <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      user <- getUser(allowGuest = true)
      result <- datasetService.search(
        query = json.query,
        owners = json.owners,
        groups = json.groups,
        attributes = json.attributes,
        limit = json.limit,
        offset = json.offset,
        orderby = json.orderby,
        user = user
      )
    } yield {
      result
    }
  }

  // データセット詳細取得
  get("/datasets/:datasetId") {
    val id = params("datasetId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("datasetId", id)
      user <- getUser(allowGuest = true)
      result <- datasetService.get(id, user)
      _ <- SystemService.writeDatasetAccessLog(result.id, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセット ファイル追加
  post("/datasets/:datasetId/files") {
    val id = params("datasetId")
    val ret = for {
      files <- checkUtil.requireForForm("files", fileMultiParams.get("files"))
      _ <- checkUtil.hasElement("files", files)
      _ <- checkUtil.invokeSeq(files) { x => checkUtil.checkNonZeroByteFile("files", x) }
      _ <- checkUtil.validUuidForUrl("datasetId", id)
      user <- getUser(allowGuest = false)
      result <- datasetService.addFiles(id, files, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセット ファイル変更(更新)
  post("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val ret = for {
      file <- checkUtil.requireForForm("file", fileParams.get("file"))
      _ <- checkUtil.checkNonZeroByteFile("file", file)
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _ <- checkUtil.validUuidForUrl("fileId", fileId)
      user <- getUser(allowGuest = false)
      result <- datasetService.updateFile(datasetId, fileId, file, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセット ファイルメタデータ変更
  put("/datasets/:datasetId/files/:fileId/metadata") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val ret = for {
      d <- getJsonValueFromParams[UpdateDatasetFileMetadataParams]
      json <- jsonOptToTry(d)
      name <- checkUtil.requireForForm("d.name", json.name)
      _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.name", name)
      _ <- checkUtil.requireForForm("d.description", json.description)
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _ <- checkUtil.validUuidForUrl("fileId", fileId)
      user <- getUser(allowGuest = false)
      result <- datasetService.updateFileMetadata(datasetId, fileId, name, json.description.getOrElse(""), user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセット ファイル削除
  delete("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _ <- checkUtil.validUuidForUrl("fileId", fileId)
      user <- getUser(allowGuest = false)
      result <- datasetService.deleteDatasetFile(datasetId, fileId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセット メタデータ編集
  put("/datasets/:datasetId/metadata") {
    val datasetId = params("datasetId")
    val ret = for {
      d <- getJsonValueFromParams[UpdateDatasetMetaParams]
      json <- jsonOptToTry(d)
      name <- checkUtil.requireForForm("d.name", json.name)
      _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.name", name)
      license <- checkUtil.requireForForm("d.license", json.license)
      _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.license", license)
      _ <- checkUtil.validUuidForForm("d.license", license)
      description <- checkUtil.requireForForm("d.description", json.description)
      _ <- checkUtil.invokeSeq(json.attributes) { x =>
        for {
          _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.attributes.name", x.name)
          _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.attributes.value", x.value)
        } yield {}
      }
      _ <- checkUtil.invoke(
        "d.attribute",
        json.attributes.filter(_.name == "featured").length < 2,
        resource.getString(ResourceNames.FEATURE_ATTRIBUTE_IS_ONLY_ONE)
      )
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user <- getUser(allowGuest = false)
      result <- datasetService.modifyDatasetMeta(datasetId, name, json.description, license, json.attributes, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセット画像一覧取得
  get("/datasets/:datasetId/images") {
    val datasetId = params("datasetId")
    val ret = for {
      d <- getJsonValueFromParams[SearchRangeParams]
      json <- Success(d.getOrElse(SearchRangeParams()))
      _ <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _ <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user <- getUser(allowGuest = true)
      result <- datasetService.getImages(datasetId, json.offset, json.limit, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセット 画像追加
  post("/datasets/:datasetId/images") {
    val datasetId = params("datasetId")
    val ret = for {
      images <- checkUtil.requireForForm("images", fileMultiParams.get("images")).map(_.filter(_.name.nonEmpty))
      _ <- checkUtil.hasElement("images", images)
      _ <- checkUtil.invokeSeq(images) { x => checkUtil.checkNonZeroByteFile("images", x) }
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user <- getUser(allowGuest = false)
      result <- datasetService.addImages(datasetId, images, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセット メイン画像設定
  put("/datasets/:datasetId/images/primary") {
    val datasetId = params("datasetId")
    val ret = for {
      d <- getJsonValueFromParams[ChangePrimaryImageParams]
      json <- jsonOptToTry(d)
      imageId <- checkUtil.requireForForm("d.imageId", json.imageId)
      _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.imageId", imageId)
      _ <- checkUtil.validUuidForForm("d.imageId", imageId)
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user <- getUser(allowGuest = false)
      result <- datasetService.changePrimaryImage(datasetId, imageId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセット 画像削除
  delete("/datasets/:datasetId/images/:imageId") {
    val datasetId = params("datasetId")
    val imageId = params("imageId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _ <- checkUtil.validUuidForUrl("imageId", imageId)
      user <- getUser(allowGuest = false)
      result <- datasetService.deleteImage(datasetId, imageId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセットアクセスレベル一覧取得
  get("/datasets/:datasetId/acl") {
    val datasetId = params("datasetId")
    val ret = for {
      d <- getJsonValueFromParams[SearchRangeParams]
      json <- Success(d.getOrElse(SearchRangeParams()))
      _ <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _ <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user <- getUser(allowGuest = true)
      result <- datasetService.searchOwnerships(datasetId, json.offset, json.limit, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセット ACLアイテム アクセスレベル設定
  post("/datasets/:datasetId/acl") {
    val datasetId = params("datasetId")
    val ret = for {
      d <- getJsonValueFromParams[List[DataSetAccessControlItem]]
      json <- jsonOptToTry(d)
      _ <- checkUtil.hasElement("d", json)
      _ <- checkUtil.invokeSeq(json) { x =>
        for {
          _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.id", x.id)
          _ <- checkUtil.validUuidForForm("d.id", x.id)
          _ <- checkUtil.contains("d.ownerType", x.ownerType, Seq(1, 2))
          _ <- checkUtil.contains("d.accessLevel", x.accessLevel, Seq(0, 1, 2, 3))
        } yield {}
      }
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user <- getUser(allowGuest = false)
      result <- datasetService.setAccessControl(datasetId, json, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセット ACL ゲストアクセスレベル設定
  put("/datasets/:datasetId/guest_access") {
    val datasetId = params("datasetId")
    val ret = for {
      d <- getJsonValueFromParams[UpdateDatasetGuestAccessParams]
      json <- jsonOptToTry(d)
      accessLevel <- checkUtil.requireForForm("d.accessLevel", json.accessLevel)
      _ <- checkUtil.contains("d.accessLevel", accessLevel, Seq(0, 1, 2))
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user <- getUser(allowGuest = false)
      result <- datasetService.setGuestAccessLevel(datasetId, accessLevel, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセット削除
  delete("/datasets/:datasetId") {
    val datasetId = params("datasetId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user <- getUser(allowGuest = false)
      _ <- datasetService.deleteDataset(datasetId, user)
    } yield {}
    toActionResult(ret)
  }

  // データセット保存先変更
  put("/datasets/:datasetId/storage") {
    val datasetId = params("datasetId")
    val ret = for {
      d <- getJsonValueFromParams[DatasetStorageParams]
      json <- jsonOptToTry(d)
      saveLocal <- checkUtil.requireForForm("d.saveLocal", json.saveLocal)
      saveS3 <- checkUtil.requireForForm("d.saveS3", json.saveS3)
      _ <- checkUtil.invoke(
        "saveLocal, saveS3",
        saveLocal || saveS3,
        resource.getString(ResourceNames.CHECK_S3_OR_LOCAL)
      )
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user <- getUser(allowGuest = false)
      result <- datasetService.modifyDatasetStorage(datasetId, saveLocal, saveS3, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセットコピー
  post("/datasets/:datasetId/copy") {
    val datasetId = params("datasetId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user <- getUser(allowGuest = false)
      result <- datasetService.copyDataset(datasetId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセット属性インポート
  post("/datasets/:datasetId/attributes/import") {
    val datasetId = params("datasetId")
    val ret = for {
      file <- checkUtil.requireForForm("file", fileParams.get("file"))
      _ <- checkUtil.checkNonZeroByteFile("file", file)
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user <- getUser(allowGuest = false)
      _ <- datasetService.importAttribute(datasetId, file, user)
    } yield {}
    toActionResult(ret)
  }

  // データセット属性エクスポート
  get("/datasets/:datasetId/attributes/export") {
    val datasetId = params("datasetId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user <- getUser(allowGuest = true)
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

  // データセットFeatured画像設定
  put("/datasets/:datasetId/images/featured") {
    val datasetId = params("datasetId")
    val ret = for {
      d <- getJsonValueFromParams[ChangePrimaryImageParams]
      json <- jsonOptToTry(d)
      imageId <- checkUtil.requireForForm("d.imageId", json.imageId)
      _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.imageId", imageId)
      _ <- checkUtil.validUuidForForm("d.imageId", imageId)
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user <- getUser(allowGuest = false)
      result <- datasetService.changeFeaturedImage(datasetId, imageId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセットファイル一覧取得
  get("/datasets/:datasetId/files") {
    val datasetId = params("datasetId")
    val ret = for {
      d <- getJsonValueFromParams[SearchRangeParams]
      json <- Success(d.getOrElse(SearchRangeParams(Some(dsmoq.AppConf.fileLimit), Some(0))))
      _ <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _ <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user <- getUser(allowGuest = true)
      result <- datasetService.getDatasetFiles(datasetId, json.limit, json.offset, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセットZIP内ファイル一覧取得
  get("/datasets/:datasetId/files/:fileId/zippedfiles") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val ret = for {
      d <- getJsonValueFromParams[SearchRangeParams]
      json <- Success(d.getOrElse(SearchRangeParams(Some(dsmoq.AppConf.fileLimit), Some(0))))
      _ <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _ <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      _ <- checkUtil.validUuidForUrl("fileId", fileId)
      user <- getUser(allowGuest = true)
      result <- datasetService.getDatasetZippedFiles(datasetId, fileId, json.limit, json.offset, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // アプリ取得
  get("/datasets/:datasetId/app") {
    val datasetId = params("datasetId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user <- getUser(allowGuest = false)
      result <- datasetService.getApp(datasetId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // アプリ追加・更新
  post("/datasets/:datasetId/app") {
    val datasetId = params("datasetId")
    val appId = params.get("appId")
    val fileItem = fileParams.get("file")
    val description = params.getOrElse("description", "")
    val ret = if (appId.isEmpty) {
      // アプリ追加
      for {
        file <- checkUtil.requireForForm("file", fileItem)
        _ <- checkUtil.checkJarFile("file", file)
        _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
        user <- getUser(allowGuest = false)
        result <- datasetService.addApp(datasetId, description, file, user)
      } yield {
        result
      }
    } else {
      // アプリ更新
      for {
        _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
        user <- getUser(allowGuest = false)
        result <- datasetService.updateApp(datasetId, appId.get, description, fileItem, user)
      } yield {
        result
      }
    }
    toActionResult(ret)
  }

  // アプリ物理削除
  delete("/datasets/:datasetId/app") {
    val datasetId = params("datasetId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("datasetId", datasetId)
      user <- getUser(allowGuest = false)
      result <- datasetService.deleteApp(datasetId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // --------------------------------------------------------------------------
  // dataset query api
  // --------------------------------------------------------------------------
  // データセット検索条件一覧取得
  get("/dataset_queries") {
    val ret = for {
      user <- getUser(allowGuest = true)
      result <- queryService.getDatasetQueries(user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセット検索条件保存
  post("/dataset_queries") {
    val ret = for {
      d <- getJsonValueFromParams[CreateDatasetQueryParams]
      json <- jsonOptToTry(d)
      name <- checkUtil.requireForForm("d.name", json.name)
      _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.name", name)
      user <- getUser(allowGuest = false)
      result <- queryService.createDatasetQuery(name, json.query, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセット検索条件取得
  get("/dataset_queries/:queryId") {
    val queryId = params("queryId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("queryId", queryId)
      user <- getUser(allowGuest = false)
      result <- queryService.getDatasetQuery(queryId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // データセット検索条件削除
  delete("/dataset_queries/:queryId") {
    val queryId = params("queryId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("queryId", queryId)
      user <- getUser(allowGuest = false)
      result <- queryService.deleteDatasetQuery(queryId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // --------------------------------------------------------------------------
  // group api
  // --------------------------------------------------------------------------
  // グループ一覧取得
  get("/groups") {
    val ret = for {
      d <- getJsonValueFromParams[SearchGroupsParams]
      json <- Success(d.getOrElse(SearchGroupsParams()))
      _ <- checkUtil.validUuidForForm("d.user", json.user)
      _ <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _ <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      user <- getUser(allowGuest = true)
      result <- groupService.search(json.query, json.user, json.limit, json.offset, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // グループ詳細取得
  get("/groups/:groupId") {
    val groupId = params("groupId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("groupId", groupId)
      user <- getUser(allowGuest = true)
      result <- groupService.get(groupId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // グループメンバー一覧取得
  get("/groups/:groupId/members") {
    val groupId = params("groupId")
    val ret = for {
      d <- getJsonValueFromParams[GetGroupMembersParams]
      json <- Success(d.getOrElse(GetGroupMembersParams()))
      _ <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _ <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      _ <- checkUtil.validUuidForUrl("groupId", groupId)
      user <- getUser(allowGuest = true)
      result <- groupService.getGroupMembers(groupId, json.limit, json.offset, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // グループ作成
  post("/groups") {
    val ret = for {
      d <- getJsonValueFromParams[CreateGroupParams]
      json <- jsonOptToTry(d)
      name <- checkUtil.requireForForm("d.name", json.name)
      _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.name", name)
      description <- checkUtil.requireForForm("d.description", json.description)
      user <- getUser(allowGuest = false)
      result <- groupService.createGroup(name, description, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // グループ基本情報編集
  put("/groups/:groupId") {
    val groupId = params("groupId")
    val ret = for {
      d <- getJsonValueFromParams[UpdateGroupParams]
      json <- jsonOptToTry(d)
      name <- checkUtil.requireForForm("d.name", json.name)
      _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.name", name)
      description <- checkUtil.requireForForm("d.description", json.description)
      _ <- checkUtil.validUuidForUrl("groupId", groupId)
      user <- getUser(allowGuest = false)
      result <- groupService.updateGroup(groupId, name, description, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // グループ画像一覧取得
  get("/groups/:groupId/images") {
    val groupId = params("groupId")
    val ret = for {
      d <- getJsonValueFromParams[SearchRangeParams]
      json <- Success(d.getOrElse(SearchRangeParams()))
      _ <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _ <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      _ <- checkUtil.validUuidForUrl("groupId", groupId)
      user <- getUser(allowGuest = true)
      result <- groupService.getImages(groupId, json.offset, json.limit, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // グループ画像追加
  post("/groups/:groupId/images") {
    val groupId = params("groupId")
    val ret = for {
      images <- checkUtil.requireForForm("images", fileMultiParams.get("images")).map(_.filter(_.name.nonEmpty))
      _ <- checkUtil.hasElement("images", images)
      _ <- checkUtil.invokeSeq(images) { x => checkUtil.checkNonZeroByteFile("images", x) }
      _ <- checkUtil.validUuidForUrl("groupId", groupId)
      user <- getUser(allowGuest = false)
      result <- groupService.addImages(groupId, images, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // グループメイン画像設定
  put("/groups/:groupId/images/primary") {
    val groupId = params("groupId")
    val ret = for {
      d <- getJsonValueFromParams[ChangeGroupPrimaryImageParams]
      json <- jsonOptToTry(d)
      imageId <- checkUtil.requireForForm("d.imageId", json.imageId)
      _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.imageId", imageId)
      _ <- checkUtil.validUuidForForm("d.imageId", imageId)
      _ <- checkUtil.validUuidForUrl("groupId", groupId)
      user <- getUser(allowGuest = false)
      result <- groupService.changePrimaryImage(groupId, imageId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // グループ画像削除
  delete("/groups/:groupId/images/:imageId") {
    val groupId = params("groupId")
    val imageId = params("imageId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("groupId", groupId)
      _ <- checkUtil.validUuidForUrl("imageId", imageId)
      user <- getUser(allowGuest = false)
      result <- groupService.deleteImage(groupId, imageId, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // グループメンバー追加
  post("/groups/:groupId/members") {
    val groupId = params("groupId")
    val ret = for {
      d <- getJsonValueFromParams[List[GroupMember]]
      json <- jsonOptToTry(d)
      _ <- checkUtil.hasElement("d", json)
      _ <- checkUtil.invokeSeq(json) { x =>
        for {
          _ <- checkUtil.nonEmptyTrimmedSpacesForForm("d.userId", x.userId)
          _ <- checkUtil.validUuidForForm("d.userId", x.userId)
          _ <- checkUtil.contains("d.role", x.role, Seq(0, 1, 2))
        } yield {}
      }
      _ <- checkUtil.validUuidForUrl("groupId", groupId)
      user <- getUser(allowGuest = false)
      result <- groupService.addMembers(groupId, json, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // グループメンバーロール変更
  put("/groups/:groupId/members/:userId") {
    val groupId = params("groupId")
    val userId = params("userId")
    val ret = for {
      d <- getJsonValueFromParams[SetGroupMemberRoleParams]
      json <- jsonOptToTry(d)
      role <- checkUtil.requireForForm("d.role", json.role)
      _ <- checkUtil.contains("d.role", role, Seq(0, 1, 2))
      _ <- checkUtil.validUuidForUrl("groupId", groupId)
      _ <- checkUtil.validUuidForUrl("userId", userId)
      user <- getUser(allowGuest = false)
      result <- groupService.updateMemberRole(groupId, userId, role, user)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // グループメンバー削除
  delete("/groups/:groupId/members/:userId") {
    val groupId = params("groupId")
    val userId = params("userId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("groupId", groupId)
      _ <- checkUtil.validUuidForUrl("userId", userId)
      user <- getUser(allowGuest = false)
      _ <- groupService.removeMember(groupId, userId, user)
    } yield {}
    toActionResult(ret)
  }

  // グループ削除
  delete("/groups/:groupId") {
    val groupId = params("groupId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("groupId", groupId)
      user <- getUser(allowGuest = false)
      _ <- groupService.deleteGroup(groupId, user)
    } yield {}
    toActionResult(ret)
  }

  // --------------------------------------------------------------------------
  // メールアドレス存在チェック
  get("/system/is_valid_email") {
    // TODO not implemented
    AjaxResponse("OK")
  }

  // ライセンス一覧取得
  get("/licenses") {
    val ret = for {
      result <- SystemService.getLicenses()
    } yield {
      result
    }
    toActionResult(ret)
  }

  // ユーザ一覧取得
  get("/accounts") {
    val ret = for {
      result <- SystemService.getAccounts()
    } yield {
      result
    }
    toActionResult(ret)
  }

  // タグ一覧取得
  get("/tags") {
    val ret = for {
      result <- SystemService.getTags()
    } yield {
      result
    }
    toActionResult(ret)
  }

  // タグ色一覧取得
  get("/tags/color") {
    val ret = for {
      result <- SystemService.getTagColors()
    } yield {
      result
    }
    toActionResult(ret)
  }

  // ユーザ候補一覧取得
  get("/suggests/users") {
    val ret = for {
      d <- getJsonValueFromParams[SuggestApiParams]
      json <- Success(d.getOrElse(SuggestApiParams()))
      _ <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _ <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      result <- SystemService.getUsers(json.query, json.limit, json.offset)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // グループ候補一覧取得
  get("/suggests/groups") {
    val ret = for {
      d <- getJsonValueFromParams[SuggestApiParams]
      json <- Success(d.getOrElse(SuggestApiParams()))
      _ <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _ <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      result <- SystemService.getGroups(json.query, json.limit, json.offset)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // ユーザ＋グループ候補一覧取得
  get("/suggests/users_and_groups") {
    val ret = for {
      d <- getJsonValueFromParams[UserAndGroupSuggestApiParams]
      json <- Success(d.getOrElse(UserAndGroupSuggestApiParams()))
      _ <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _ <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      _ <- checkUtil.invokeSeq(json.excludeIds) { x => checkUtil.validUuidForForm("d.excludeId", x) }
      result <- SystemService.getUsersAndGroups(json.query, json.limit, json.offset, json.excludeIds)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // 属性候補一覧取得
  get("/suggests/attributes") {
    val ret = for {
      d <- getJsonValueFromParams[SuggestApiParams]
      json <- Success(d.getOrElse(SuggestApiParams()))
      _ <- checkUtil.checkNonMinusNumber("d.limit", json.limit)
      _ <- checkUtil.checkNonMinusNumber("d.offset", json.offset)
      result <- SystemService.getAttributes(json.query, json.limit, json.offset)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // タスク状態取得
  get("/tasks/:taskId") {
    val taskId = params("taskId")
    val ret = for {
      _ <- checkUtil.validUuidForUrl("taskId", taskId)
      result <- TaskService.getStatus(taskId)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // 統計情報一覧取得
  get("/statistics") {
    val ret = for {
      d <- getJsonValueFromParams[StatisticsParams]
      json <- Success(d.getOrElse(StatisticsParams()))
      result <- StatisticsService.getStatistics(json.from, json.to)
    } yield {
      result
    }
    toActionResult(ret)
  }

  // トップページメッセージ取得
  get("/message") {
    val ret = for {
      result <- SystemService.getMessage()
    } yield {
      result
    }
    toActionResult(ret)
  }

  /**
   * Option値をTry値へ変換する。
   *
   * Noneの場合はFailure(InputCheckException)となる。
   * @tparam T
   * @param obj 変換するOption値
   * @return Try値
   */
  private def jsonOptToTry[T](obj: Option[T]): Try[T] = {
    obj match {
      case None => {
        Failure(new InputCheckException("d", resource.getString(ResourceNames.JSON_PARAMETER_REQUIRED), false))
      }
      case Some(x) => Success(x)
    }
  }

  /**
   * params(d)から指定された型の値を取得する。
   *
   * @tparam T 取得する型
   * @return 取得結果
   */
  private def getJsonValueFromParams[T](implicit m: Manifest[T]): Try[Option[T]] = {
    params.get("d") match {
      case None => Success(None)
      case Some(x) => getJsonValue(x, "d").map(Some.apply)
    }
  }

  /**
   * 指定されたJSON文字列を指定された型の値に変換する。
   *
   * @tparam T 変換先の型
   * @param str JSON文字列
   * @param position 文字列のパラメータ位置
   * @return 変換結果
   */
  def getJsonValue[T](str: String, position: String)(implicit m: Manifest[T]): Try[T] = {
    Try {
      JsonMethods.parse(str).extractOpt[T].get
    }.recoverWith {
      case e: Exception =>
        Failure(new InputCheckException(position, resource.getString(ResourceNames.INVALID_JSON_FORMAT), false))
    }
  }
}
