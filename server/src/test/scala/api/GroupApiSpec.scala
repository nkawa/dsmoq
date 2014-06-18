package api

import _root_.api.api.logic.SpecCommonLogic
import org.scalatest.{BeforeAndAfter, FreeSpec}
import org.scalatra.test.scalatest.ScalatraSuite
import org.json4s.{DefaultFormats, Formats}
import java.io.File
import dsmoq.controllers.{ImageController, FileController, ApiController}
import scalikejdbc.config.{DBsWithEnv, DBs}
import org.json4s.jackson.JsonMethods._
import dsmoq.services.data.GroupData._
import dsmoq.services.data.GroupData.Group
import org.scalatra.servlet.MultipartConfig
import dsmoq.services.data.GroupData.GroupAddImages
import scala.Some
import dsmoq.services.data.GroupData.GroupsSummary
import dsmoq.controllers.AjaxResponse
import dsmoq.services.data.{User, RangeSlice}
import dsmoq.services.data.DatasetData.{DatasetsSummary, Dataset}
import dsmoq.persistence.GroupMemberRole
import java.util.UUID

class GroupApiSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormats: Formats = DefaultFormats

  private val dummyFile = new File("README.md")
  private val dummyImage = new File("../client/www/dummy/images/nagoya.jpg")
  private val dummyUserUUID = "eb7a596d-e50c-483f-bbc7-50019eea64d7"  // dummy 4
  private val dummyUserLoginParams = Map("id" -> "dummy4", "password" -> "password")

  // multi-part file upload config
  val holder = addServlet(classOf[ApiController], "/api/*")
  holder.getRegistration.setMultipartConfig(
    MultipartConfig(
      maxFileSize = Some(3 * 1024 * 1024),
      fileSizeThreshold = Some(1 * 1024 * 1024)
    ).toMultipartConfigElement
  )
  addServlet(classOf[FileController], "/files/*")
  addServlet(classOf[ImageController], "/images/*")

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

  "API test" - {
    "dataset" - {
      "グループ一覧が取得できるか" in {
        session {
          signIn()
          val groupId = createGroup()

          val params = Map("limit" -> "10")
          get("/api/groups", params) {
            status should be(200)
            val result = parse(body).extract[AjaxResponse[RangeSlice[GroupsSummary]]]
            result.data.summary.count should be(10)
            result.data.results.map(_.id).contains(groupId)
          }
        }
      }

      "グループが作成できるか" in {
        session {
          signIn()
          val groupId = createGroup()
          get("/api/groups/" + groupId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Group]]
            result.data.id should be (groupId)
          }
        }
      }

      "作成したグループが削除できるか" in {
        session {
          signIn()
          val groupId = createGroup()
          delete("/api/groups/" + groupId) { checkStatus() }
          get("/api/groups/" + groupId) {
            status should be(200)
            val result = parse(body).extract[AjaxResponse[Group]]
            result.status should be("NotFound")
          }
        }
      }

      "グループの情報が編集できるか" in {
        session {
          signIn()
          val groupId = createGroup()
          val changeGroupName = "changeName" + UUID.randomUUID().toString
          val changeDescription = "change description"
          val params = Map(
            "name" -> changeGroupName,
            "description" -> changeDescription
          )
          put("/api/groups/" + groupId, params) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Group]]
            result.data.name should be(changeGroupName)
            result.data.description should be (changeDescription)
          }
          get("/api/groups/" + groupId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Group]]
            result.data.name should be (changeGroupName)
            result.data.description should be (changeDescription)
          }
        }
      }

      "グループに画像が追加できるか" in {
        session {
          signIn()
          val groupId = createGroup()
          val images = Map("images" -> dummyImage)
          val imageId = post("/api/groups/" + groupId + "/images", Map.empty, images) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[GroupAddImages]]
            result.data.images(0).id
          }
          get("/api/groups/" + groupId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Group]]
            result.data.images.size should be(2)
            assert(result.data.images.map(_.id).contains(imageId))
          }
        }
      }

      "グループに追加した画像が削除できるか" in {
        session {
          signIn()
          val groupId = createGroup()

          // add images
          val images = Map("images" -> dummyImage)
          post("/api/groups/" + groupId + "/images", Map.empty, images) { checkStatus() }
          val imageId = post("/api/groups/" + groupId + "/images", Map.empty, images) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[GroupAddImages]]
            result.data.images(0).id
          }
          post("/api/groups/" + groupId + "/images", Map.empty, images) { checkStatus() }

          delete("/api/groups/" + groupId + "/images/" + imageId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[GroupDeleteImage]]
            result.data.primaryImage should not be(imageId)
          }
          get("/api/groups/" + groupId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Group]]
            result.data.images.size should be(3)
            assert(!result.data.images.map(_.id).contains(imageId))
          }
        }
      }

      "グループのメイン画像を変更できるか" in {
        session {
          signIn()
          val groupId = createGroup()

          // add images
          val images = Map("images" -> dummyImage)
          post("/api/groups/" + groupId + "/images", Map.empty, images) { checkStatus() }
          val imageId = post("/api/groups/" + groupId + "/images", Map.empty, images) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[GroupAddImages]]
            result.data.images(0).id
          }
          post("/api/groups/" + groupId + "/images", Map.empty, images) { checkStatus() }

          // change primary image
          val params = Map("id" -> imageId)
          put("/api/groups/" + groupId + "/images/primary", params) { checkStatus() }

          get("/api/groups/" + groupId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Group]]
            result.data.primaryImage should be(imageId)
          }
        }
      }

      "グループメンバー一覧を取得できるか" in {
        session {
          signIn()
          val myUserId = get("/api/profile") {
            checkStatus()
            parse(body).extract[AjaxResponse[User]].data.id
          }

          val groupId = createGroup()
          val params = Map("limit" -> "10")
          get("/api/groups/" + groupId + "/members", params) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[MemberSummary]]]
            result.data.summary.count should be(10)
            assert(result.data.results.map(_.id).contains(myUserId))
          }
        }
      }

      "グループにメンバーを追加できるか" in {
        session {
          signIn()
          val groupId = createGroup()
          val params = Map("id[]" -> dummyUserUUID, "role[]" -> GroupMemberRole.Member.toString)
          post("/api/groups/" + groupId + "/members", params) { checkStatus() }

          get("/api/groups/" + groupId + "/members") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[MemberSummary]]]
            assert(result.data.results.map(_.id).contains(dummyUserUUID))
          }
        }
      }

      "グループメンバーのロールを変更できるか" in {
        session {
          signIn()
          val groupId = createGroup()
          val params = Map("id[]" -> dummyUserUUID, "role[]" -> GroupMemberRole.Member.toString)
          post("/api/groups/" + groupId + "/members", params) { checkStatus() }

          // ロール変更
          val changeParams = Map("id[]" -> dummyUserUUID, "role[]" -> GroupMemberRole.Manager.toString)
          post("/api/groups/" + groupId + "/members", changeParams) { checkStatus() }

          get("/api/groups/" + groupId + "/members") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[MemberSummary]]]
            // IDの有無をチェック後、付随するデータのチェック
            assert(result.data.results.map(_.id).contains(dummyUserUUID))
            result.data.results.map {x =>
              if (x.id == dummyUserUUID) {
                x.role should be(GroupMemberRole.Manager)
              }
            }
          }
        }
      }

      "追加したグループメンバーを削除できるか" in {
        session {
          signIn()
          val groupId = createGroup()
          val params = Map("id[]" -> dummyUserUUID, "role[]" -> GroupMemberRole.Member.toString)
          post("/api/groups/" + groupId + "/members", params) { checkStatus() }

          // ロール削除(Denyに変更)
          val deleteParams = Map("id[]" -> dummyUserUUID, "role[]" -> GroupMemberRole.Deny.toString)
          post("/api/groups/" + groupId + "/members", deleteParams) { checkStatus() }

          get("/api/groups/" + groupId + "/members") {
            checkStatus()
            println(groupId)
            val result = parse(body).extract[AjaxResponse[RangeSlice[MemberSummary]]]
            assert(!result.data.results.map(_.id).contains(dummyUserUUID))
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

  private def createGroup(): String = {
    val groupName = "groupName" + UUID.randomUUID.toString
    val params = Map("name" -> groupName, "description" -> "groupDescription")
    post("/api/groups", params) {
      checkStatus()
      parse(body).extract[AjaxResponse[Group]].data.id
    }
  }
}
