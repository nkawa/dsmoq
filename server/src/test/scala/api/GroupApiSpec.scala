package api

import org.scalatest.{BeforeAndAfter, FreeSpec}
import org.scalatra.test.scalatest.ScalatraSuite
import org.json4s.{DefaultFormats, Formats}
import java.io.File
import dsmoq.controllers.{ImageController, FileController, ApiController}
import scalikejdbc.config.DBs
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

class GroupApiSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormats: Formats = DefaultFormats

  private val dummyFile = new File("README.md")
  private val dummyImage = new File("../client/www/dummy/images/nagoya.jpg")
  private val dummyUserUUID = "eb7a596d-e50c-483f-bbc7-50019eea64d7"
  private val dummyUserLoginParams = Map("id" -> "kawaguti", "password" -> "password")

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

  before {
    DBs.setup()
  }
  after {
    DBs.close()
  }

  "API test" - {
    "dataset" - {
      "グループ一覧が取得できるか" in {
        session {
          signIn()
          val params = Map("limit" -> "10", "offset" -> "5")
          get("/api/groups", params) {
            status should be(200)
            val result = parse(body).extract[AjaxResponse[RangeSlice[GroupsSummary]]]
            result.data.summary.count should be(10)
            result.data.summary.offset should be(5)
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
          val params = Map(
            "name" -> "change name",
            "description" -> "change description"
          )
          put("/api/groups/" + groupId, params) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Group]]
            result.data.description should be ("change description")
          }
          get("/api/groups/" + groupId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Group]]
            result.data.name should be ("change name")
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
            result.data.images.size should be(1)
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
            result.data.images.size should be(2)
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
          val params = Map("id" -> dummyUserUUID, "role" -> "0")
          post("/api/groups/" + groupId + "/members", params) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[AddMember]]
            result.data.id should be(dummyUserUUID)
          }

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
          val params = Map("id" -> dummyUserUUID, "role" -> "0")
          post("/api/groups/" + groupId + "/members", params) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[AddMember]]
            result.data.id should be(dummyUserUUID)
          }

          val changeParams = Map("role" -> "1")
          put("/api/groups/" + groupId + "/members/" + dummyUserUUID + "/role", changeParams) { checkStatus() }

          get("/api/groups/" + groupId + "/members") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[MemberSummary]]]
            assert(result.data.results.map(_.id).contains(dummyUserUUID))
            result.data.results.map {x =>
              if (x.id == dummyUserUUID) {
                x.role should be(1)
              }
            }
          }
        }
      }

      "追加したグループメンバーを削除できるか" in {
        session {
          signIn()
          val groupId = createGroup()
          val params = Map("id" -> dummyUserUUID, "role" -> "0")
          post("/api/groups/" + groupId + "/members", params) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[AddMember]]
            result.data.id should be(dummyUserUUID)
          }

          delete("/api/groups/" + groupId + "/members/" + dummyUserUUID) { checkStatus() }

          get("/api/groups/" + groupId + "/members") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[MemberSummary]]]
            assert(!result.data.results.map(_.id).contains(dummyUserUUID))
          }
        }
      }

      "グループに所属するデータセットを取得できるか" in {
        session {
          // データセット作成
          signIn()
          val files = Map("file[]" -> dummyFile)
          val datasetId = post("/api/datasets", Map.empty, files) {
            checkStatus()
            parse(body).extract[AjaxResponse[Dataset]].data.id
          }
          post("/api/signout") { checkStatus() }

          // 権限を与えるグループを作成(別ユーザーで作成)
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          val groupId = createGroup()
          // この時点ではグループに所属しているデータセットは0件のはず
          val params = Map("limit" -> "10")
          get("/api/groups/" + groupId + "/datasets", params) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[MemberSummary]]]
            result.data.summary.total should be(0)
          }
          post("/api/signout") { checkStatus() }

          // グループにアクセスレベルを設定
          signIn()
          val accessLevelParams = Map("accessLevel" -> "2")
          put("/api/datasets/" + datasetId + "/acl/" + groupId, accessLevelParams) { checkStatus() }
          post("/api/signout") { checkStatus() }

          // 1件データセットが見えるはず
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get("/api/groups/" + groupId + "/datasets", params) {
            checkStatus()
            println(body)
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(1)
            result.data.results.size should be(1)
          }
        }
      }
    }
  }

  private def signIn() {
    val params = Map("id" -> "t_okada", "password" -> "password")
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
    val params = Map("name" -> "groupName", "description" -> "groupDescription")
    post("/api/groups", params) {
      checkStatus()
      parse(body).extract[AjaxResponse[Group]].data.id
    }
  }
}
