package api

import java.io.File
import java.util.UUID

import _root_.api.api.logic.SpecCommonLogic
import dsmoq.controllers.{AjaxResponse, ApiController}
import dsmoq.persistence._
import dsmoq.services.json.DatasetData.Dataset
import dsmoq.services.json.GroupData.Group
import org.eclipse.jetty.server.Connector
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats}
import org.scalatest.{BeforeAndAfter, FreeSpec}
import org.scalatra.servlet.MultipartConfig
import org.scalatra.test.scalatest.ScalatraSuite
import scalikejdbc.config.{DBsWithEnv, DBs}
import org.json4s._
import org.json4s.JsonDSL._

class DatasetDetailAuthorizationSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormats: Formats = DefaultFormats

  private val dummyFile = new File("README.md")
  private val dummyUserId = "eb7a596d-e50c-483f-bbc7-50019eea64d7"  // dummy 4
  private val dummyUserLoginParams = Map("d" -> compact(render(("id" -> "dummy4") ~ ("password" -> "password"))))
  private val anotherUserLoginParams = Map("d" -> compact(render(("id" -> "dummy2") ~ ("password" -> "password"))))

  // multi-part file upload config
  val holder = addServlet(classOf[ApiController], "/api/*")
  holder.getRegistration.setMultipartConfig(
    MultipartConfig(
      maxFileSize = Some(3 * 1024 * 1024),
      fileSizeThreshold = Some(1 * 1024 * 1024)
    ).toMultipartConfigElement
  )

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
    "設定した権限にあわせてデータセット詳細を閲覧できるか" in {
      session {
        signIn()

        // データセットを作成
        val userAccessLevels = List(UserAccessLevel.Deny, UserAccessLevel.LimitedRead, UserAccessLevel.FullPublic, UserAccessLevel.Owner)
        val groupAccessLevels = List(GroupAccessLevel.Deny, GroupAccessLevel.LimitedPublic, GroupAccessLevel.FullPublic, GroupAccessLevel.Provider)
        val guestAccessLevels = List(DefaultAccessLevel.Deny, DefaultAccessLevel.LimitedPublic, DefaultAccessLevel.FullPublic)
        val files = Map("file[]" -> dummyFile)
        val datasetParams = userAccessLevels.map { userAccessLevel =>
          guestAccessLevels.map { groupAccessLevel =>
            guestAccessLevels.map { guestAccessLevel =>
              // グループ作成/メンバー追加
              val groupId = createGroup()
              val memberParams = Map("d" -> compact(render(List(("userId" -> dummyUserId) ~ ("role" -> GroupMemberRole.Member)))))
              post("/api/groups/" + groupId + "/members", memberParams) {
                checkStatus()
              }

              post("/api/datasets", Map.empty, files) {
                checkStatus()
                val datasetId = parse(body).extract[AjaxResponse[Dataset]].data.id

                // アクセスレベル設定(ユーザー/グループ)
                val accessLevelParams = Map("d" -> compact(render(List(
                    ("id" -> dummyUserId) ~ ("ownerType" -> JInt(OwnerType.User)) ~ ("accessLevel" -> JInt(userAccessLevel)),
                    ("id" -> groupId) ~ ("ownerType" -> JInt(OwnerType.Group)) ~ ("accessLevel" -> JInt(groupAccessLevel))
                  )))
                )
                post("/api/datasets/" + datasetId + "/acl", accessLevelParams) {
                  checkStatus()
                }

                // ゲストアクセスレベル設定
                val guestAccessLevelParams = Map("d" -> compact(render(("accessLevel" -> JInt(guestAccessLevel)))))
                put("/api/datasets/" + datasetId + "/guest_access", guestAccessLevelParams) {
                  checkStatus()
                }

                (datasetId, userAccessLevel, groupAccessLevel, guestAccessLevel)
              }
            }
          }
        }.flatten.flatten

        // ダミーユーザー時のデータセット詳細閲覧 Denyではない(AllowLimitedRead以上)であれば閲覧可
        post("/api/signout") { checkStatus() }
        post("/api/signin", dummyUserLoginParams) { checkStatus() }
        datasetParams.foreach { params =>
          // for debug
          println("debug params(user):" + params)

          if (params._2 > UserAccessLevel.Deny || params._3 > GroupAccessLevel.Deny || params._4 > DefaultAccessLevel.Deny) {
            get("/api/datasets/" + params._1) {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id should be(params._1)
              val permission = List(params._2, params._3, params._4).sorted.last
              result.data.permission should be(permission)
              result.data.defaultAccessLevel should be (params._4)
            }
          } else {
            get("/api/datasets/" + params._1) {
              // Unauthorized
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("Unauthorized")
            }
          }
        }

        // ゲストアクセス時のデータセット詳細閲覧  Denyではない(AllowLimitedRead以上)であれば閲覧可
        post("/api/signout") { checkStatus() }
        datasetParams.foreach { params =>
          // for debug
          println("debug params(guest):" + params)

          if (params._4 > DefaultAccessLevel.Deny) {
            get("/api/datasets/" + params._1) {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id should be(params._1)
              result.data.permission should be(params._4)
              result.data.defaultAccessLevel should be (params._4)
            }
          } else {
            get("/api/datasets/" + params._1) {
              // Unauthorized
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("Unauthorized")
            }
          }
        }

        // 何も権限を付与していないユーザーのデータセット詳細閲覧 ゲストと同じアクセス制限となる
        post("/api/signin", anotherUserLoginParams) { checkStatus() }
        datasetParams.foreach { params =>
          // for debug
          println("debug params(not authorization user):" + params)

          if (params._4 > DefaultAccessLevel.Deny) {
            get("/api/datasets/" + params._1) {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id should be(params._1)
              result.data.permission should be(params._4)
              result.data.defaultAccessLevel should be (params._4)
            }
          } else {
            get("/api/datasets/" + params._1) {
              // Unauthorized
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("Unauthorized")
            }
          }
        }
      }
    }
  }
  
  private def signIn() {
    val params = Map("d" -> compact(render(("id" -> "dummy1") ~ ("password" -> "password"))))
    post("/api/signin", params) {
      checkStatus()
    }
  }
  
  private def checkStatus() {
    status should be(200)
    val result = parse(body).extract[AjaxResponse[Any]]
    result.status should be("OK")
  }

  private def createGroup(): String = {
    val groupName = "groupName" + UUID.randomUUID.toString
    val params = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "groupDescription"))))
    post("/api/groups", params) {
      checkStatus()
      parse(body).extract[AjaxResponse[Group]].data.id
    }
  }
}
