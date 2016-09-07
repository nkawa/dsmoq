package api

import java.util.ResourceBundle

import org.eclipse.jetty.servlet.ServletHolder

import _root_.api.api.logic.SpecCommonLogic
import org.eclipse.jetty.server.Connector
import org.scalatest.{ BeforeAndAfter, FreeSpec }
import org.scalatra.test.scalatest.ScalatraSuite
import org.json4s.{ DefaultFormats, Formats }
import dsmoq.controllers.{ ImageController, FileController, ApiController }
import scalikejdbc.config.{ DBsWithEnv, DBs }
import org.json4s.jackson.JsonMethods._
import java.io.File
import dsmoq.persistence._
import dsmoq.services.json.DatasetData._
import dsmoq.services.json.TaskData._
import dsmoq.AppConf
import org.scalatra.servlet.MultipartConfig
import dsmoq.services.json.DatasetData.DatasetAddFiles
import dsmoq.services.json.DatasetData.Dataset
import dsmoq.services.json.GroupData.GroupAddImages
import dsmoq.services.json.GroupData.AddMembers
import dsmoq.controllers.AjaxResponse
import dsmoq.services.json.DatasetData.DatasetAddImages
import dsmoq.services.json.RangeSlice
import dsmoq.services.json.GroupData.Group
import java.util.{ Base64, UUID }
import dsmoq.persistence.{ DefaultAccessLevel, OwnerType, UserAccessLevel, GroupAccessLevel }
import org.json4s._
import org.json4s.JsonDSL._
import scalikejdbc._
import dsmoq.persistence.PostgresqlHelper._

class ResponseFormatSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormats: Formats = DefaultFormats

  private val dummyFile = new File("../README.md")
  private val dummyImage = new File("../../client/www/dummy/images/nagoya.jpg")
  private val testUserName = "dummy1"
  private val dummyUserName = "dummy4"
  private val testUserId = "023bfa40-e897-4dad-96db-9fd3cf001e79" // dummy1
  private val dummyUserId = "eb7a596d-e50c-483f-bbc7-50019eea64d7" // dummy 4

  private val host = "http://localhost:8080"

  override def beforeAll() {
    super.beforeAll()
    DBsWithEnv("test").setup()
    System.setProperty(org.scalatra.EnvironmentKey, "test")

    val resource = ResourceBundle.getBundle("message")
    val servlet = new ApiController(resource)
    val holder = new ServletHolder(servlet.getClass.getName, servlet)
    // multi-part file upload config
    val multipartConfig = MultipartConfig(
      maxFileSize = Some(3 * 1024 * 1024),
      fileSizeThreshold = Some(1 * 1024 * 1024)
    ).toMultipartConfigElement
    holder.getRegistration.setMultipartConfig(multipartConfig)
    servletContextHandler.addServlet(holder, "/api/*")
    addServlet(new FileController(resource), "/files/*")
    addServlet(new ImageController(resource), "/images/*")
    SpecCommonLogic.deleteAllCreateData()
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
      "POST /api/datasets/:dataset_id/images" in {
        session {
          signIn()
          val datasetId = createDataset()
          val files = Map("images" -> dummyImage)
          post(s"/api/datasets/${datasetId}/images", Map.empty, files) {
            status should be(200)
            parse(body).extract[AjaxResponse[DatasetAddImages]]
          }
        }
      }
      "POST /api/groups/:group_id/images" in {
        session {
          signIn()
          val groupId = createGroup()
          val files = Map("images" -> dummyImage)
          post(s"/api/groups/${groupId}/images", Map.empty, files) {
            status should be(200)
            parse(body).extract[AjaxResponse[GroupAddImages]]
          }
        }
      }
      "PUT /api/datasets/:dataset_id/guest_access" in {
        session {
          signIn()
          val datasetId = createDataset()
          val params = Map("d" -> compact(render(("accessLevel" -> JInt(DefaultAccessLevel.FullPublic)))))
          put(s"/api/datasets/${datasetId}/guest_access", params) {
            status should be(200)
            parse(body).extract[AjaxResponse[DatasetGuestAccessLevel]]
          }
        }
      }
      "PUT /api/datasets/:dataset_id/metadata" in {
        session {
          signIn()
          val datasetId = createDataset()
          val params = Map(
            "d" -> compact(
              render(
                ("name" -> "変更後データセット") ~
                  ("description" -> "change description") ~
                  ("license" -> AppConf.defaultLicenseId)
              )
            )
          )
          put(s"/api/datasets/${datasetId}/metadata", params) {
            status should be(200)
            parse(body).extract[AjaxResponse[DatasetMetaData]]
          }
        }
      }
      "POST /api/groups/:groupId/members" in {
        session {
          signIn()
          val groupId = createGroup()
          val params = Map("d" -> compact(render(Seq(("userId" -> dummyUserId) ~ ("role" -> JInt(GroupMemberRole.Member))))))
          post(s"/api/groups/${groupId}/members", params) {
            status should be(200)
            parse(body).extract[AjaxResponse[AddMembers]]
          }
        }
      }
    }
  }

  /**
   * ダミーユーザでサインインします。
   */
  private def signIn() {
    val params = Map("d" -> compact(render(("id" -> "dummy1") ~ ("password" -> "password"))))
    post("/api/signin", params) {
      checkStatus()
    }
  }

  /**
   * Responseのステータスが200:OKであるかどうかをチェックします。
   */
  private def checkStatus() {
    status should be(200)
    val result = parse(body).extract[AjaxResponse[Any]]
    result.status should be("OK")
  }

  /**
   * データセットを作成します。
   * @return 作成したデータセットID
   */
  private def createDataset(): String = {
    val files = Map("file[]" -> dummyFile)
    val params = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
    post("/api/datasets", params, files) {
      checkStatus()
      parse(body).extract[AjaxResponse[Dataset]].data.id
    }
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
}
