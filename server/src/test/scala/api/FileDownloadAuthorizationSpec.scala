package api

import java.io.File
import java.util.UUID

import _root_.api.api.logic.SpecCommonLogic
import dsmoq.controllers.{FileController, ApiController, AjaxResponse}
import dsmoq.persistence._
import dsmoq.services.json.DatasetData.Dataset
import dsmoq.services.json.GroupData.Group
import org.eclipse.jetty.server.Connector
import org.json4s.{DefaultFormats, Formats}
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfter, FreeSpec}
import org.scalatra.servlet.MultipartConfig
import org.scalatra.test.scalatest.ScalatraSuite
import scalikejdbc.config.{DBsWithEnv, DBs}

class FileDownloadAuthorizationSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormats: Formats = DefaultFormats

  private val dummyFile = new File("README.md")
  private val dummyUserId = "eb7a596d-e50c-483f-bbc7-50019eea64d7"  // dummy 4
  private val dummyUserLoginParams = Map("id" -> "dummy4", "password" -> "password")
  private val anotherUserLoginParams = Map("id" -> "dummy2", "password" -> "password")

  // multi-part file upload config
  val holder = addServlet(classOf[ApiController], "/api/*")
  holder.getRegistration.setMultipartConfig(
    MultipartConfig(
      maxFileSize = Some(3 * 1024 * 1024),
      fileSizeThreshold = Some(1 * 1024 * 1024)
    ).toMultipartConfigElement
  )
  addServlet(classOf[FileController], "/files/*")

  override def baseUrl: String =
    server.getConnectors collectFirst {
      case conn: Connector =>
        val host = Option(conn.getHost) getOrElse "localhost"
        val port = conn.getLocalPort
        require(port > 0, "The detected local port is < 1, that's not allowed")
        "http://%s:%d".format(host, port)
    } getOrElse sys.error("can't calculate base URL: no connector")

  override def beforeAll() {
    super.beforeAll()
    DBsWithEnv("test").setup()
    System.setProperty(org.scalatra.EnvironmentKey, "test")
  }

  override def afterAll() {
    DBsWithEnv("test").close()
    super.afterAll()
  }

  before {
    SpecCommonLogic.insertDummyData()
  }

  after {
    SpecCommonLogic.deleteAllCreateData()
  }

  "Authorization Test" - {
    "設定した権限にあわせてファイルをダウンロードできるか" in {
      session {
        signIn()

        // データセットを作成
        val userAccessLevels = List(UserAccessLevel.Deny, UserAccessLevel.LimitedRead, UserAccessLevel.FullPublic, UserAccessLevel.Owner)
        val groupAccessLevels = List(GroupAccessLevel.Deny, GroupAccessLevel.LimitedPublic, GroupAccessLevel.FullPublic, GroupAccessLevel.Provider)
        val guestAccessLevels = List(DefaultAccessLevel.Deny, DefaultAccessLevel.LimitedPublic, DefaultAccessLevel.FullPublic)
        val files = Map("file[]" -> dummyFile)
        val datasetParams = userAccessLevels.map { userAccessLevel =>
          groupAccessLevels.map { groupAccessLevel =>
            guestAccessLevels.map { guestAccessLevel =>
              // グループ作成/メンバー追加
              val groupId = createGroup()
              val memberParams = List("id[]" -> dummyUserId, "role[]" -> GroupMemberRole.Member.toString)
              post("/api/groups/" + groupId + "/members", memberParams) { checkStatus() }

              post("/api/datasets", Map.empty, files) {
                checkStatus()
                val datasetId = parse(body).extract[AjaxResponse[Dataset]].data.id
                val fileUrl = parse(body).extract[AjaxResponse[Dataset]].data.files(0).url

                // アクセスレベル設定(ユーザー/グループ)
                val accessLevelParams = List(
                  "id[]" -> dummyUserId, "type[]" -> OwnerType.User.toString, "accessLevel[]" -> userAccessLevel.toString,
                  "id[]" -> groupId, "type[]" -> OwnerType.Group.toString, "accessLevel[]" -> groupAccessLevel.toString
                )
                post("/api/datasets/" + datasetId + "/acl", accessLevelParams) { checkStatus() }

                // ゲストアクセスレベル設定
                val guestAccessLevelParams = Map("accessLevel" -> guestAccessLevel.toString)
                put("/api/datasets/" + datasetId + "/guest_access", guestAccessLevelParams) { checkStatus() }

                (datasetId, fileUrl, userAccessLevel, groupAccessLevel, guestAccessLevel)
              }
            }
          }
        }.flatten.flatten

        // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
        post("/api/signout") { checkStatus() }
        post("/api/signin", dummyUserLoginParams) { checkStatus() }
        datasetParams.foreach { params =>
          // for debug
          println("debug params(user):" + params)

          val uri = new java.net.URI(params._2)
          if (params._3 >= UserAccessLevel.FullPublic || params._4 >= GroupAccessLevel.FullPublic || params._5 >= DefaultAccessLevel.FullPublic) {
            get(uri.getPath) {
              status should be(200)
              bodyBytes.size should be(dummyFile.length())
            }
          } else {
            get(uri.getPath) {
              // forbidden
              status should be(403)
            }
          }
        }

        // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
        post("/api/signout") { checkStatus() }
        datasetParams.foreach { params =>
          // for debug
          println("debug params(guest):" + params)

          val uri = new java.net.URI(params._2)
          if (params._5 >= DefaultAccessLevel.FullPublic) {
            get(uri.getPath) {
              status should be(200)
              bodyBytes.size should be(dummyFile.length())
            }
          } else {
            get(uri.getPath) {
              // forbidden
              status should be(403)
            }
          }
        }

        // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
        post("/api/signin", anotherUserLoginParams) { checkStatus() }
        datasetParams.foreach { params =>
          // for debug
          println("debug params(not authorization user):" + params)

          val uri = new java.net.URI(params._2)
          if (params._5 >= DefaultAccessLevel.FullPublic) {
            get(uri.getPath) {
              status should be(200)
              bodyBytes.size should be(dummyFile.length())
            }
          } else {
            get(uri.getPath) {
              // forbidden
              status should be(403)
            }
          }
        }
      }
    }
  }

  private def signIn() {
    val params = Map("id" -> "dummy1", "password" -> "password")
    post("/api/signin", params) {
      checkStatus()
    }
  }

  private def checkStatus() {
    status should be(200)
    val result = parse(body).extract[AjaxResponse[Any]]
    result.status should be("OK")
  }

  private def createDataset(): String = {
    val files = Map("file[]" -> dummyFile)
    post("/api/datasets", Map.empty, files) {
      checkStatus()
      parse(body).extract[AjaxResponse[Dataset]].data.id
    }
  }
  private def createGroup(): String = {
    val groupName = "groupName" + UUID.randomUUID.toString
    val params = Map("name" -> groupName, "description" -> "groupDescription")
    post("/api/groups", params) {
      checkStatus()
      parse(body).extract[AjaxResponse[Group]].data.id
    }
  }
}
