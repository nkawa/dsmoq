package api

import java.io.File
import java.util.UUID

import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._

import common.DsmoqSpec
import dsmoq.AppConf
import dsmoq.controllers.AjaxResponse
import dsmoq.persistence._
import dsmoq.persistence.{ OwnerType, UserAccessLevel, GroupAccessLevel }
import dsmoq.services.json.DatasetData.Dataset
import dsmoq.services.json.DatasetData.DatasetAddFiles
import dsmoq.services.json.DatasetData.DatasetAddImages
import dsmoq.services.json.DatasetData.DatasetDeleteImage
import dsmoq.services.json.DatasetData._
import dsmoq.services.json.GroupData.Group
import dsmoq.services.json.RangeSlice

class DatasetReadPermissionSpec extends DsmoqSpec {
  private val dummyFile = new File("../README.md")
  private val dummyImage = new File("../../client/www/dummy/images/nagoya.jpg")
  private val dummyZipFile = new File("../testdata/test1.zip")
  private val testUserName = "dummy1"
  private val dummyUserName = "dummy4"
  private val testUserId = "023bfa40-e897-4dad-96db-9fd3cf001e79" // dummy1
  private val dummyUserId = "eb7a596d-e50c-483f-bbc7-50019eea64d7" // dummy 4
  private val dummyUserLoginParams = Map("d" -> compact(render(("id" -> "dummy4") ~ ("password" -> "password"))))

  "API test" - {
    "dataset" - {
      "GET /api/datasets/:datasetId" in {
        testReadPermission((x: String) => s"/api/datasets/${x}") {
          status should be(200)
          val result = parse(body).extract[AjaxResponse[Any]]
          result.status should be("OK")
        }
      }
      "GET /api/datasets/:datasetId/attributes/export" in {
        testReadPermission((x: String) => s"/api/datasets/${x}/attributes/export") {
          status should be(200)
          // bodyの確認の代わりに、正常ケースで付与されるContent-Dispositionヘッダの有無で正常動作か否かを判定している
          response.header.get("Content-Disposition").isDefined should be(true)
        }
      }
      "GET/api/datasets/:datasetId/acl" in {
        testReadPermission((x: String) => s"/api/datasets/${x}/acl") {
          status should be(200)
          val result = parse(body).extract[AjaxResponse[Any]]
          result.status should be("OK")
        }
      }
      "GET /api/datasets/:datasetId/images" in {
        testReadPermission((x: String) => s"/api/datasets/${x}/images") {
          status should be(200)
          val result = parse(body).extract[AjaxResponse[Any]]
          result.status should be("OK")
        }
      }
      "GET /api/datasets/:datasetId/files" in {
        testReadPermission((x: String) => s"/api/datasets/${x}/files") {
          status should be(200)
          val result = parse(body).extract[AjaxResponse[Any]]
          result.status should be("OK")
        }
      }
      "GET /api/datasets/:datasetId/files/:fileId/zippedfiles" in {
        // Deny*Deny*Deny_GuestUser
        block {
          session {
            signIn()
            val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.Deny), UserAccessLevel.Deny, GroupAccessLevel.Deny)
            val fileId = getFileId(datasetId, dummyZipFile)
            post("/api/signout") { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(403)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("AccessDenied")
            }
          }
        }
        // Deny*Deny*Deny_LoginUser 
        block {
          session {
            signIn()
            val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.Deny), UserAccessLevel.Deny, GroupAccessLevel.Deny)
            val fileId = getFileId(datasetId, dummyZipFile)
            post("/api/signout") { checkStatus() }
            post("/api/signin", dummyUserLoginParams) { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(403)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("AccessDenied")
            }
          }
        }
        // Limited*Deny*Deny_GuestUser"
        block {
          session {
            signIn()
            val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.LimitedPublic), UserAccessLevel.Deny, GroupAccessLevel.Deny)
            val fileId = getFileId(datasetId, dummyZipFile)
            post("/api/signout") { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
        // Limited*Deny*Deny_LoginUser"
        block {
          session {
            signIn()
            val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.LimitedPublic), UserAccessLevel.Deny, GroupAccessLevel.Deny)
            val fileId = getFileId(datasetId, dummyZipFile)
            post("/api/signout") { checkStatus() }
            post("/api/signin", dummyUserLoginParams) { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
        // Full*Deny*Deny_GuestUser
        block {
          session {
            signIn()
            val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.FullPublic), UserAccessLevel.Deny, GroupAccessLevel.Deny)
            val fileId = getFileId(datasetId, dummyZipFile)
            post("/api/signout") { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
        // Full*Deny*Deny_LoginUser
        block {
          session {
            signIn()
            val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.FullPublic), UserAccessLevel.Deny, GroupAccessLevel.Deny)
            val fileId = getFileId(datasetId, dummyZipFile)
            post("/api/signout") { checkStatus() }
            post("/api/signin", dummyUserLoginParams) { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
        // 未設定*Deny*Deny_GuestUser
        block {
          session {
            signIn()
            val datasetId = createPermissionedDataset(None, UserAccessLevel.Deny, GroupAccessLevel.Deny)
            val fileId = getFileId(datasetId, dummyZipFile)
            post("/api/signout") { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(403)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("AccessDenied")
            }
          }
        }
        // 未設定*Deny*Deny_LoginUser
        block {
          session {
            signIn()
            val datasetId = createPermissionedDataset(None, UserAccessLevel.Deny, GroupAccessLevel.Deny)
            val fileId = getFileId(datasetId, dummyZipFile)
            post("/api/signout") { checkStatus() }
            post("/api/signin", dummyUserLoginParams) { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(403)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("AccessDenied")
            }
          }
        }
        // 未設定*未設定*未設定_GuestUser
        block {
          session {
            signIn()
            val datasetId = createDataset()
            val fileId = getFileId(datasetId, dummyZipFile)
            post("/api/signout") { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(403)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("AccessDenied")
            }
          }
        }
        // 未設定*未設定*未設定_LoginUser
        block {
          session {
            signIn()
            val datasetId = createDataset()
            val fileId = getFileId(datasetId, dummyZipFile)
            post("/api/signout") { checkStatus() }
            post("/api/signin", dummyUserLoginParams) { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(403)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("AccessDenied")
            }
          }
        }
        // Deny*Limited*Deny_LoginUser
        block {
          session {
            signIn()
            val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.Deny), UserAccessLevel.LimitedRead, GroupAccessLevel.Deny)
            val fileId = getFileId(datasetId, dummyZipFile)
            post("/api/signout") { checkStatus() }
            post("/api/signin", dummyUserLoginParams) { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
        // Deny*Full*Deny_LoginUser
        block {
          session {
            signIn()
            val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.Deny), UserAccessLevel.FullPublic, GroupAccessLevel.Deny)
            val fileId = getFileId(datasetId, dummyZipFile)
            post("/api/signout") { checkStatus() }
            post("/api/signin", dummyUserLoginParams) { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
        // Deny*Owner*Deny_LoginUser
        block {
          session {
            signIn()
            val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.Deny), UserAccessLevel.Owner, GroupAccessLevel.Deny)
            val fileId = getFileId(datasetId, dummyZipFile)
            post("/api/signout") { checkStatus() }
            post("/api/signin", dummyUserLoginParams) { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
        // Deny*Deny*Limited_LoginUser
        block {
          session {
            signIn()
            val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.Deny), UserAccessLevel.Deny, GroupAccessLevel.LimitedPublic)
            val fileId = getFileId(datasetId, dummyZipFile)
            post("/api/signout") { checkStatus() }
            post("/api/signin", dummyUserLoginParams) { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
        // Deny*Deny*Full_LoginUser
        block {
          session {
            signIn()
            val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.Deny), UserAccessLevel.Deny, GroupAccessLevel.FullPublic)
            val fileId = getFileId(datasetId, dummyZipFile)
            post("/api/signout") { checkStatus() }
            post("/api/signin", dummyUserLoginParams) { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
        // Deny*Deny*Provider_LoginUser
        block {
          session {
            signIn()
            val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.Deny), UserAccessLevel.Deny, GroupAccessLevel.Provider)
            val fileId = getFileId(datasetId, dummyZipFile)
            post("/api/signout") { checkStatus() }
            post("/api/signin", dummyUserLoginParams) { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
        // Full*Owner*Provider_GuestUser
        block {
          session {
            signIn()
            val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.FullPublic), UserAccessLevel.Owner, GroupAccessLevel.Provider)
            val fileId = getFileId(datasetId, dummyZipFile)
            post("/api/signout") { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
        // Full*Owner*Provider_LoginUser
        block {
          session {
            signIn()
            val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.FullPublic), UserAccessLevel.Owner, GroupAccessLevel.Provider)
            val fileId = getFileId(datasetId, dummyZipFile)
            post("/api/signout") { checkStatus() }
            post("/api/signin", dummyUserLoginParams) { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
      }
    }
  }

  /**
   * 独自スコープを持つブロックを作成するためのメソッドです。
   *
   * @param procedure ブロックで行う処理
   * @return ブロックでの処理結果
   */
  private def block[T](procedure: => T): T = {
    procedure
  }

  /**
   * データセットのRead権限をテストします。
   * @param getUrl テスト対象APIのURLを生成する関数
   * @param successCaseCheck 成功時のチェック内容
   */
  private def testReadPermission[A](getUrl: String => String)(successCaseCheck: => A): Unit = {
    // Deny*Deny*Deny_GuestUser
    block {
      session {
        signIn()
        val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.Deny), UserAccessLevel.Deny, GroupAccessLevel.Deny)
        post("/api/signout") { checkStatus() }
        get(getUrl(datasetId)) {
          status should be(403)
          val result = parse(body).extract[AjaxResponse[Any]]
          result.status should be("AccessDenied")
        }
      }
    }
    // Deny*Deny*Deny_LoginUser 
    block {
      session {
        signIn()
        val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.Deny), UserAccessLevel.Deny, GroupAccessLevel.Deny)
        post("/api/signout") { checkStatus() }
        post("/api/signin", dummyUserLoginParams) { checkStatus() }
        get(getUrl(datasetId)) {
          status should be(403)
          val result = parse(body).extract[AjaxResponse[Any]]
          result.status should be("AccessDenied")
        }
      }
    }
    // Limited*Deny*Deny_GuestUser"
    block {
      session {
        signIn()
        val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.LimitedPublic), UserAccessLevel.Deny, GroupAccessLevel.Deny)
        post("/api/signout") { checkStatus() }
        get[A](getUrl(datasetId)) {
          successCaseCheck
        }
      }
    }
    // Limited*Deny*Deny_LoginUser"
    block {
      session {
        signIn()
        val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.LimitedPublic), UserAccessLevel.Deny, GroupAccessLevel.Deny)
        post("/api/signout") { checkStatus() }
        post("/api/signin", dummyUserLoginParams) { checkStatus() }
        get[A](getUrl(datasetId)) {
          successCaseCheck
        }
      }
    }
    // Full*Deny*Deny_GuestUser
    block {
      session {
        signIn()
        val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.FullPublic), UserAccessLevel.Deny, GroupAccessLevel.Deny)
        post("/api/signout") { checkStatus() }
        get[A](getUrl(datasetId)) {
          successCaseCheck
        }
      }
    }
    // Full*Deny*Deny_LoginUser
    block {
      session {
        signIn()
        val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.FullPublic), UserAccessLevel.Deny, GroupAccessLevel.Deny)
        post("/api/signout") { checkStatus() }
        post("/api/signin", dummyUserLoginParams) { checkStatus() }
        get[A](getUrl(datasetId)) {
          successCaseCheck
        }
      }
    }
    // 未設定*Deny*Deny_GuestUser
    block {
      session {
        signIn()
        val datasetId = createPermissionedDataset(None, UserAccessLevel.Deny, GroupAccessLevel.Deny)
        post("/api/signout") { checkStatus() }
        get(getUrl(datasetId)) {
          status should be(403)
          val result = parse(body).extract[AjaxResponse[Any]]
          result.status should be("AccessDenied")
        }
      }
    }
    // 未設定*Deny*Deny_LoginUser
    block {
      session {
        signIn()
        val datasetId = createPermissionedDataset(None, UserAccessLevel.Deny, GroupAccessLevel.Deny)
        post("/api/signout") { checkStatus() }
        post("/api/signin", dummyUserLoginParams) { checkStatus() }
        get(getUrl(datasetId)) {
          status should be(403)
          val result = parse(body).extract[AjaxResponse[Any]]
          result.status should be("AccessDenied")
        }
      }
    }
    // 未設定*未設定*未設定_GuestUser
    block {
      session {
        signIn()
        val datasetId = createDataset()
        post("/api/signout") { checkStatus() }
        get(getUrl(datasetId)) {
          status should be(403)
          val result = parse(body).extract[AjaxResponse[Any]]
          result.status should be("AccessDenied")
        }
      }
    }
    // 未設定*未設定*未設定_LoginUser
    block {
      session {
        signIn()
        val datasetId = createDataset()
        post("/api/signout") { checkStatus() }
        post("/api/signin", dummyUserLoginParams) { checkStatus() }
        get(getUrl(datasetId)) {
          status should be(403)
          val result = parse(body).extract[AjaxResponse[Any]]
          result.status should be("AccessDenied")
        }
      }
    }
    // Deny*Limited*Deny_LoginUser
    block {
      session {
        signIn()
        val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.Deny), UserAccessLevel.LimitedRead, GroupAccessLevel.Deny)
        post("/api/signout") { checkStatus() }
        post("/api/signin", dummyUserLoginParams) { checkStatus() }
        get[A](getUrl(datasetId)) {
          successCaseCheck
        }
      }
    }
    // Deny*Full*Deny_LoginUser
    block {
      session {
        signIn()
        val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.Deny), UserAccessLevel.FullPublic, GroupAccessLevel.Deny)
        post("/api/signout") { checkStatus() }
        post("/api/signin", dummyUserLoginParams) { checkStatus() }
        get[A](getUrl(datasetId)) {
          successCaseCheck
        }
      }
    }
    // Deny*Owner*Deny_LoginUser
    block {
      session {
        signIn()
        val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.Deny), UserAccessLevel.Owner, GroupAccessLevel.Deny)
        post("/api/signout") { checkStatus() }
        post("/api/signin", dummyUserLoginParams) { checkStatus() }
        get[A](getUrl(datasetId)) {
          successCaseCheck
        }
      }
    }
    // Deny*Deny*Limited_LoginUser
    block {
      session {
        signIn()
        val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.Deny), UserAccessLevel.Deny, GroupAccessLevel.LimitedPublic)
        post("/api/signout") { checkStatus() }
        post("/api/signin", dummyUserLoginParams) { checkStatus() }
        get[A](getUrl(datasetId)) {
          successCaseCheck
        }
      }
    }
    // Deny*Deny*Full_LoginUser
    block {
      session {
        signIn()
        val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.Deny), UserAccessLevel.Deny, GroupAccessLevel.FullPublic)
        post("/api/signout") { checkStatus() }
        post("/api/signin", dummyUserLoginParams) { checkStatus() }
        get[A](getUrl(datasetId)) {
          successCaseCheck
        }
      }
    }
    // Deny*Deny*Provider_LoginUser
    block {
      session {
        signIn()
        val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.Deny), UserAccessLevel.Deny, GroupAccessLevel.Provider)
        post("/api/signout") { checkStatus() }
        post("/api/signin", dummyUserLoginParams) { checkStatus() }
        get[A](getUrl(datasetId)) {
          successCaseCheck
        }
      }
    }
    // Full*Owner*Provider_GuestUser
    block {
      session {
        signIn()
        val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.FullPublic), UserAccessLevel.Owner, GroupAccessLevel.Provider)
        post("/api/signout") { checkStatus() }
        get[A](getUrl(datasetId)) {
          successCaseCheck
        }
      }
    }
    // Full*Owner*Provider_LoginUser
    block {
      session {
        signIn()
        val datasetId = createPermissionedDataset(Some(DefaultAccessLevel.FullPublic), UserAccessLevel.Owner, GroupAccessLevel.Provider)
        post("/api/signout") { checkStatus() }
        post("/api/signin", dummyUserLoginParams) { checkStatus() }
        get[A](getUrl(datasetId)) {
          successCaseCheck
        }
      }
    }
  }

  /**
   * 権限を設定したデータセットを作成します。
   * @param guestAccessLevel ゲストアクセスレベル。None時は設定しない。
   * @param userAccessLevel ユーザアクセスレベル
   * @param groupAccessLevel グループアクセスレベル
   * @return 作成したデータセットのID
   */
  private def createPermissionedDataset(
    guestAccessLevel: Option[Int],
    userAccessLevel: Int,
    groupAccessLevel: Int
  ): String = {
    // グループ作成
    val groupId = createGroup()
    val memberParams = Map("d" -> compact(render(Seq(("userId" -> dummyUserId) ~ ("role" -> GroupMemberRole.Member)))))
    post(s"/api/groups/${groupId}/members", memberParams) { checkStatus() }
    // データセット作成
    val datasetId = createDataset()
    setPermission(datasetId, groupId, guestAccessLevel, userAccessLevel, groupAccessLevel)
    datasetId
  }

  /**
   * データセットに権限を設定します。
   * @param datasetId データセットID
   * @param groupId グループID
   * @param guestAccessLevel ゲストアクセスレベル。None時は設定しない。
   * @param userAccessLevel ユーザアクセスレベル
   * @param groupAccessLevel グループアクセスレベル
   */
  private def setPermission(
    datasetId: String,
    groupId: String,
    guestAccessLevel: Option[Int],
    userAccessLevel: Int,
    groupAccessLevel: Int
  ): Unit = {
    // アクセスレベル設定(ユーザー/グループ)
    val accessLevelParams = Map(
      "d" -> compact(
        render(
          Seq(
            ("id" -> dummyUserId) ~ ("ownerType" -> JInt(OwnerType.User)) ~ ("accessLevel" -> JInt(userAccessLevel)),
            ("id" -> groupId) ~ ("ownerType" -> JInt(OwnerType.Group)) ~ ("accessLevel" -> JInt(groupAccessLevel))
          )
        )
      )
    )
    post(s"/api/datasets/${datasetId}/acl", accessLevelParams) { checkStatus() }
    guestAccessLevel.foreach { accessLevel =>
      // ゲストアクセスレベル設定
      val guestAccessLevelParams = Map("d" -> compact(render(("accessLevel" -> accessLevel))))
      put(s"/api/datasets/${datasetId}/guest_access", guestAccessLevelParams) { checkStatus() }
    }
  }

  /**
   * データセットを作成します。
   * @return 作成したデータセットID
   */
  private def createDataset(): String = {
    createDataset(file = Some(dummyFile))
  }

  /**
   * グループを作成します。
   * @return 作成したグループID
   */
  private def createGroup(): String = {
    val groupName = "groupName" + UUID.randomUUID.toString
    val params = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "groupDescription"))))
    post("/api/groups", params) {
      checkStatus()
      parse(body).extract[AjaxResponse[Group]].data.id
    }
  }

  /**
   * データセットにファイルを追加し、そのIDを取得します。
   *
   * @param datasetId データセットID
   * @param file 追加するファイル
   * @return ファイルID
   */
  private def getFileId(datasetId: String, file: File): String = {
    val files = Map("files" -> file)
    post(s"/api/datasets/${datasetId}/files", Map.empty, files) {
      parse(body).extract[AjaxResponse[DatasetAddFiles]].data.files.headOption.map(_.id).getOrElse("")
    }
  }
}
