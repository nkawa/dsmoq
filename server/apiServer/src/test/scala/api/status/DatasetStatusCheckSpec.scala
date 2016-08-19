package api.status

import java.net.URLEncoder
import java.util.ResourceBundle
import java.util.UUID

import org.eclipse.jetty.servlet.ServletHolder

import _root_.api.api.logic.SpecCommonLogic
import org.scalatest.{ BeforeAndAfter, FreeSpec }
import org.scalatra.test.scalatest.ScalatraSuite
import org.json4s.{ DefaultFormats, Formats }
import dsmoq.controllers.{ ApiController, AjaxResponse }
import scalikejdbc.config.{ DBsWithEnv, DBs }
import java.io.File
import org.scalatra.servlet.MultipartConfig
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.{ compact, parse, render }
import org.json4s.{ DefaultFormats, Formats, JBool, JInt }
import org.scalatest.{ BeforeAndAfter, FreeSpec }
import org.scalatra.servlet.MultipartConfig
import org.scalatra.test.scalatest.ScalatraSuite
import scalikejdbc._
import scalikejdbc.config.DBsWithEnv

import dsmoq.AppConf
import dsmoq.services.json.DatasetData.Dataset
import dsmoq.services.json.DatasetData.DatasetAddFiles
import dsmoq.services.json.DatasetData.DatasetAddImages

class DatasetStatusCheckSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormatChecks: Formats = DefaultFormats

  private val dummyImage = new File("../testdata/image/1byteover.png")
  private val dummyFile = new File("../testdata/test1.csv")
  private val dummyZipFile = new File("../testdata/test1.zip")

  private val testUserName = "dummy1"
  private val dummyUserName = "dummy4"
  private val testUserId = "023bfa40-e897-4dad-96db-9fd3cf001e79" // dummy1
  private val dummyUserId = "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04" // dummy 2
  private val dummyUserLoginParams = Map("d" -> compact(render(("id" -> "dummy4") ~ ("password" -> "password"))))

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
  }

  override def afterAll() {
    DBsWithEnv("test").close()
    super.afterAll()
  }

  before {
    SpecCommonLogic.deleteAllCreateData()
    SpecCommonLogic.insertDummyData()
  }

  after {
    SpecCommonLogic.deleteAllCreateData()
  }

  private val OK = "OK"
  private val ILLEGAL_ARGUMENT = "Illegal Argument"
  private val UNAUTHORIZED = "Unauthorized"
  private val NOT_FOUND = "NotFound"
  private val ACCESS_DENIED = "AccessDenied"
  private val BAD_REQUEST = "BadRequest"
  private val NG = "NG"
  private val invalidApiKeyHeader = Map("Authorization" -> "api_key=hoge,signature=fuga")

  "API Status test" - {
    "dataset" - {
      "POST /api/datasets" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val params = Map("saveLocal" -> "false", "saveS3" -> "false", "name" -> "dummy1")
            post("/api/datasets", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          val params = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "dummy1")
          post("/api/datasets", params) {
            checkStatus(403, UNAUTHORIZED)
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val params = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "dummy1")
            dbDisconnectedBlock {
              post("/api/datasets", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          val params = Map("saveLocal" -> "false", "saveS3" -> "false", "name" -> "dummy1")
          post("/api/datasets", params) {
            checkStatus(400, ILLEGAL_ARGUMENT)
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val params = Map("saveLocal" -> "false", "saveS3" -> "false", "name" -> "dummy1")
            dbDisconnectedBlock {
              post("/api/datasets", params) {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          val params = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "dummy1")
          dbDisconnectedBlock {
            post("/api/datasets", params) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
      }

      "GET /api/datasets" - {
        "400(Illegal Argument)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
          get("/api/datasets", params) {
            checkStatus(400, ILLEGAL_ARGUMENT)
          }
        }
        "403(Unauthorized)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
          get("/api/datasets", params, invalidApiKeyHeader) {
            checkStatus(403, UNAUTHORIZED)
          }
        }
        "500(NG)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
          dbDisconnectedBlock {
            get("/api/datasets", params) {
              checkStatus(500, NG)
            }
          }
        }
        "All" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
          get("/api/datasets", params, invalidApiKeyHeader) {
            checkStatus(400, ILLEGAL_ARGUMENT)
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
          dbDisconnectedBlock {
            get("/api/datasets", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
          dbDisconnectedBlock {
            get("/api/datasets", params, invalidApiKeyHeader) {
              checkStatus(500, NG)
            }
          }
        }
      }

      "GET /api/datasets/:dataset_id" - {
        "404(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            get(s"/api/datasets/hoge") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            get(s"/api/datasets/${datasetId}", Seq.empty, invalidApiKeyHeader) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            get(s"/api/datasets/${dummyId}") {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            signOut()
            get(s"/api/datasets/${datasetId}") {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            dbDisconnectedBlock {
              get(s"/api/datasets/${datasetId}") {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            signIn()
            val datasetId = createDataset().id
          }
          get(s"/api/datasets/hoge", Seq.empty, invalidApiKeyHeader) {
            checkStatus(404, ILLEGAL_ARGUMENT)
          }
        }
        "403(Unauthorized) * 404(NotFound)" in {
          val dummyId = UUID.randomUUID.toString
          get(s"/api/datasets/${dummyId}", Seq.empty, invalidApiKeyHeader) {
            checkStatus(403, UNAUTHORIZED)
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            dbDisconnectedBlock {
              get(s"/api/datasets/hoge") {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            dbDisconnectedBlock {
              get(s"/api/datasets/${datasetId}", Seq.empty, invalidApiKeyHeader) {
                checkStatus(500, NG)
              }
            }
          }
        }
      }

      "POST /api/datasets/:dataset_id/files" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            post(s"/api/datasets/${datasetId}/files") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val files = Map("files" -> dummyFile)
            post(s"/api/datasets/test/files", Map.empty, files) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            signOut()
            val files = Map("files" -> dummyFile)
            post(s"/api/datasets/${datasetId}/files", Map.empty, files) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val files = Map("files" -> dummyFile)
            val dummyId = UUID.randomUUID.toString
            post(s"/api/datasets/${dummyId}/files", Map.empty, files) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            signOut()
            dummySignIn()
            val files = Map("files" -> dummyFile)
            post(s"/api/datasets/${datasetId}/files", Map.empty, files) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val files = Map("files" -> dummyFile)
            dbDisconnectedBlock {
              post(s"/api/datasets/${datasetId}/files", Map.empty, files) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            post(s"/api/datasets/test/files") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            post(s"/api/datasets/test/files") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            dbDisconnectedBlock {
              post(s"/api/datasets/${datasetId}/files") {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val files = Map("files" -> dummyFile)
            dbDisconnectedBlock {
              post(s"/api/datasets/test/files", Map.empty, files) {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            signOut()
            val files = Map("files" -> dummyFile)
            dbDisconnectedBlock {
              post(s"/api/datasets/${datasetId}/files", Map.empty, files) {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "404(NotFound) * 403(AccessDenied)" in {
          session {
            dummySignIn()
            val dummyId = UUID.randomUUID.toString
            val files = Map("files" -> dummyFile)
            post(s"/api/datasets/${dummyId}/files", Map.empty, files) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
      }

      "POST /api/datasets/:dataset_id/files/:file_id" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            post(s"/api/datasets/${datasetId}/files/${fileId}") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            val files = Map("file" -> dummyFile)
            post(s"/api/datasets/${datasetId}/files/test", Map.empty, files) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            val files = Map("file" -> dummyFile)
            signOut()
            post(s"/api/datasets/${datasetId}/files/${fileId}", Map.empty, files) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val dummyId = UUID.randomUUID.toString
            val files = Map("file" -> dummyFile)
            post(s"/api/datasets/${datasetId}/files/${dummyId}", Map.empty, files) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            val files = Map("file" -> dummyFile)
            signOut()
            dummySignIn()
            post(s"/api/datasets/${datasetId}/files/${fileId}", Map.empty, files) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            val files = Map("file" -> dummyFile)
            dbDisconnectedBlock {
              post(s"/api/datasets/${datasetId}/files/${fileId}", Map.empty, files) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            signOut()
            dummySignIn()
            post(s"/api/datasets/${datasetId}/files/test") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            post(s"/api/datasets/${datasetId}/files/test") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            dbDisconnectedBlock {
              post(s"/api/datasets/${datasetId}/files/${fileId}") {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            val files = Map("file" -> dummyFile)
            dbDisconnectedBlock {
              post(s"/api/datasets/${datasetId}/files/test", Map.empty, files) {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            val files = Map("file" -> dummyFile)
            signOut()
            dbDisconnectedBlock {
              post(s"/api/datasets/${datasetId}/files/${fileId}", Map.empty, files) {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "403(Unauthorized) * 404(NotFound)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            val files = Map("file" -> dummyFile)
            val dummyId = UUID.randomUUID.toString
            signOut()
            post(s"/api/datasets/${datasetId}/files/${dummyId}", Map.empty, files) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
      }

      "PUT /api/datasets/:dataset_id/files/:file_id/metadata" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            put(s"/api/datasets/${datasetId}/files/${fileId}/metadata") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> ""))))
            put(s"/api/datasets/${datasetId}/files/test/metadata", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            signOut()
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> ""))))
            put(s"/api/datasets/${datasetId}/files/${fileId}/metadata", params) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> ""))))
            put(s"/api/datasets/${datasetId}/files/${dummyId}/metadata", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            signOut()
            dummySignIn()
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> ""))))
            put(s"/api/datasets/${datasetId}/files/${fileId}/metadata", params) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> ""))))
            dbDisconnectedBlock {
              put(s"/api/datasets/${datasetId}/files/${fileId}/metadata", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          val dummyId = UUID.randomUUID.toString
          put(s"/api/datasets/${dummyId}/files/test/metadata") {
            checkStatus(400, ILLEGAL_ARGUMENT)
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            put(s"/api/datasets/${datasetId}/files/test/metadata") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            dbDisconnectedBlock {
              put(s"/api/datasets/${datasetId}/files/${fileId}/metadata") {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> ""))))
            dbDisconnectedBlock {
              put(s"/api/datasets/${datasetId}/files/test/metadata", params) {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            signOut()
            dbDisconnectedBlock {
              val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> ""))))
              put(s"/api/datasets/${datasetId}/files/${fileId}/metadata", params) {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "404(NotFound) * 403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val dummyId = UUID.randomUUID.toString
            signOut()
            dummySignIn()
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> ""))))
            put(s"/api/datasets/${datasetId}/files/${dummyId}/metadata", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
      }

      "DELETE /api/datasets/:dataset_id/files/:file_id" - {
        "404(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            delete(s"/api/datasets/${datasetId}/files/test") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            signOut()
            delete(s"/api/datasets/${datasetId}/files/${fileId}") {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val dummyId = UUID.randomUUID.toString
            delete(s"/api/datasets/${datasetId}/files/${dummyId}") {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            signOut()
            dummySignIn()
            delete(s"/api/datasets/${datasetId}/files/${fileId}") {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            dbDisconnectedBlock {
              delete(s"/api/datasets/${datasetId}/files/${fileId}") {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            dummySignIn()
            val dummyId = UUID.randomUUID.toString
            delete(s"/api/datasets/${dummyId}/files/test") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            dbDisconnectedBlock {
              delete(s"/api/datasets/${datasetId}/files/test") {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            signOut()
            dbDisconnectedBlock {
              delete(s"/api/datasets/${datasetId}/files/${fileId}") {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "403(Unauthorized) * 404(NotFound)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val dummyId = UUID.randomUUID.toString
            signOut()
            delete(s"/api/datasets/${datasetId}/files/${dummyId}") {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
      }

      "PUT /api/datasets/:dataset_id/metadata" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            put(s"/api/datasets/${datasetId}/metadata") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("name" -> "test") ~ ("description" -> "") ~ ("license" -> AppConf.defaultLicenseId) ~ ("attributes" -> Seq()))))
            put(s"/api/datasets/test/metadata", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            signOut()
            val params = Map("d" -> compact(render(("name" -> "test") ~ ("description" -> "") ~ ("license" -> AppConf.defaultLicenseId) ~ ("attributes" -> Seq()))))
            put(s"/api/datasets/${datasetId}/metadata", params) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(("name" -> "test") ~ ("description" -> "") ~ ("license" -> AppConf.defaultLicenseId) ~ ("attributes" -> Seq()))))
            put(s"/api/datasets/${dummyId}/metadata", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            signOut()
            dummySignIn()
            val params = Map("d" -> compact(render(("name" -> "test") ~ ("description" -> "") ~ ("license" -> AppConf.defaultLicenseId) ~ ("attributes" -> Seq()))))
            put(s"/api/datasets/${datasetId}/metadata", params) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "400(BadRequest)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(("name" -> "test") ~ ("description" -> "") ~ ("license" -> dummyId) ~ ("attributes" -> Seq()))))
            put(s"/api/datasets/${datasetId}/metadata", params) {
              checkStatus(400, BAD_REQUEST)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("name" -> "test") ~ ("description" -> "") ~ ("license" -> AppConf.defaultLicenseId) ~ ("attributes" -> Seq()))))
            dbDisconnectedBlock {
              put(s"/api/datasets/${datasetId}/metadata", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            put(s"/api/datasets/test/metadata") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            put(s"/api/datasets/test/metadata") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            dbDisconnectedBlock {
              put(s"/api/datasets/${datasetId}/metadata") {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("name" -> "test") ~ ("description" -> "") ~ ("license" -> AppConf.defaultLicenseId) ~ ("attributes" -> Seq()))))
            dbDisconnectedBlock {
              put(s"/api/datasets/test/metadata", params) {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            signOut()
            val params = Map("d" -> compact(render(("name" -> "test") ~ ("description" -> "") ~ ("license" -> AppConf.defaultLicenseId) ~ ("attributes" -> Seq()))))
            dbDisconnectedBlock {
              put(s"/api/datasets/${datasetId}/metadata", params) {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }

        "404(NotFound) * 403(AccessDenied)" in {
          session {
            dummySignIn()
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(("name" -> "test") ~ ("description" -> "") ~ ("license" -> AppConf.defaultLicenseId) ~ ("attributes" -> Seq()))))
            put(s"/api/datasets/${dummyId}/metadata", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied) * 400(BadRequest)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val dummyId = UUID.randomUUID.toString
            signOut()
            dummySignIn()
            val params = Map("d" -> compact(render(("name" -> "test") ~ ("description" -> "") ~ ("license" -> dummyId) ~ ("attributes" -> Seq()))))
            put(s"/api/datasets/${datasetId}/metadata", params) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
      }

      "GET /api/datasets/:dataset_id/images" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            get(s"/api/datasets/${datasetId}/images", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/datasets/test/images", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/datasets/${datasetId}/images", params, invalidApiKeyHeader) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/datasets/${dummyId}/images", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            signOut()
            dummySignIn()
            get(s"/api/datasets/${datasetId}/images", params) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            dbDisconnectedBlock {
              get(s"/api/datasets/${datasetId}/images", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            get(s"/api/datasets/test/images", params, invalidApiKeyHeader) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            get(s"/api/datasets/test/images", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            dbDisconnectedBlock {
              get(s"/api/datasets/${datasetId}/images", params) {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/datasets/test/images", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            dbDisconnectedBlock {
              get(s"/api/datasets/${datasetId}/images", params, invalidApiKeyHeader) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "403(Unauthorized) * 404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/datasets/${dummyId}/images", params, invalidApiKeyHeader) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
      }

      "POST /api/datasets/:dataset_id/images" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            post(s"/api/datasets/${datasetId}/images", Map.empty) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val files = Map("images" -> dummyImage)
            post(s"/api/datasets/test/images", Map.empty, files) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val files = Map("images" -> dummyImage)
            signOut()
            post(s"/api/datasets/${datasetId}/images", Map.empty, files) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val files = Map("images" -> dummyImage)
            post(s"/api/datasets/${dummyId}/images", Map.empty, files) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val files = Map("images" -> dummyImage)
            signOut()
            dummySignIn()
            post(s"/api/datasets/${datasetId}/images", Map.empty, files) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val files = Map("images" -> dummyImage)
            dbDisconnectedBlock {
              post(s"/api/datasets/${datasetId}/images", Map.empty, files) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            post(s"/api/datasets/test/images", Map.empty) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            post(s"/api/datasets/test/images", Map.empty) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            dbDisconnectedBlock {
              post(s"/api/datasets/${datasetId}/images", Map.empty) {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val files = Map("images" -> dummyImage)
            dbDisconnectedBlock {
              post(s"/api/datasets/test/images", Map.empty, files) {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val files = Map("images" -> dummyImage)
            signOut()
            dbDisconnectedBlock {
              post(s"/api/datasets/${datasetId}/images", Map.empty, files) {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "404(NotFound) * 403(AccessDenied)" in {
          session {
            dummySignIn()
            val dummyId = UUID.randomUUID.toString
            val files = Map("images" -> dummyImage)
            post(s"/api/datasets/${dummyId}/images", Map.empty, files) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
      }

      "PUT /api/datasets/:dataset_id/images/primary" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            put(s"/api/datasets/${datasetId}/images/primary") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val imageId = AppConf.defaultDatasetImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            put(s"/api/datasets/test/images/primary", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val imageId = AppConf.defaultDatasetImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            signOut()
            put(s"/api/datasets/${datasetId}/images/primary", params) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val imageId = AppConf.defaultDatasetImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            put(s"/api/datasets/${dummyId}/images/primary", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val imageId = AppConf.defaultDatasetImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            signOut()
            dummySignIn()
            put(s"/api/datasets/${datasetId}/images/primary", params) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val imageId = AppConf.defaultDatasetImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            dbDisconnectedBlock {
              put(s"/api/datasets/${datasetId}/images/primary", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            dummySignIn()
            put(s"/api/datasets/test/images/primary") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            put(s"/api/datasets/test/images/primary") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            dbDisconnectedBlock {
              put(s"/api/datasets/${datasetId}/images/primary") {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val imageId = AppConf.defaultDatasetImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            dbDisconnectedBlock {
              put(s"/api/datasets/test/images/primary", params) {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(NotFound) * 403(AccessDenied)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val imageId = AppConf.defaultDatasetImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            signOut()
            dummySignIn()
            put(s"/api/datasets/${dummyId}/images/primary", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
      }

      "DELETE /api/datasets/:dataset_id/images/:image_id" - {
        "404(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            delete(s"/api/datasets/${datasetId}/images/test") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val imageId = getDatasetImageId(datasetId)
            signOut()
            delete(s"/api/datasets/${datasetId}/images/${imageId}") {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val dummyId = UUID.randomUUID.toString
            delete(s"/api/datasets/${datasetId}/images/${dummyId}") {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val imageId = getDatasetImageId(datasetId)
            signOut()
            dummySignIn()
            delete(s"/api/datasets/${datasetId}/images/${imageId}") {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "400(BadRequest)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val imageId = AppConf.defaultDatasetImageId
            delete(s"/api/datasets/${datasetId}/images/${imageId}") {
              checkStatus(400, BAD_REQUEST)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val imageId = getDatasetImageId(datasetId)
            dbDisconnectedBlock {
              delete(s"/api/datasets/${datasetId}/images/${imageId}") {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            val imageId = AppConf.defaultDatasetImageId
            delete(s"/api/datasets/test/images/${imageId}") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            delete(s"/api/datasets/${datasetId}/images/test") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val imageId = getDatasetImageId(datasetId)
            signOut()
            dbDisconnectedBlock {
              delete(s"/api/datasets/${datasetId}/images/${imageId}") {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "403(Unauthorized) * 404(NotFound)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val imageId = getDatasetImageId(datasetId)
            signOut()
            val dummyId = UUID.randomUUID.toString
            delete(s"/api/datasets/${dummyId}/images/${imageId}") {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound) * 400(BadRequest)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val dummyId = UUID.randomUUID.toString
            val imageId = AppConf.defaultDatasetImageId
            delete(s"/api/datasets/${dummyId}/images/${imageId}") {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
      }

      "GET /api/datasets/:dataset_id/acl" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            get(s"/api/datasets/${datasetId}/acl", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/datasets/test/acl", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/datasets/${datasetId}/acl", params, invalidApiKeyHeader) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/datasets/${dummyId}/acl", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            signOut()
            dummySignIn()
            get(s"/api/datasets/${datasetId}/acl", params) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            dbDisconnectedBlock {
              get(s"/api/datasets/${datasetId}/acl", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            dummySignIn()
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            get(s"/api/datasets/test/acl", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            get(s"/api/datasets/test/acl", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            dbDisconnectedBlock {
              get(s"/api/datasets/${datasetId}/acl", params) {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            dbDisconnectedBlock {
              get(s"/api/datasets/test/acl", params) {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            dbDisconnectedBlock {
              get(s"/api/datasets/${datasetId}/acl", params, invalidApiKeyHeader) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "404(NotFound) * 403(AccessDenied)" in {
          session {
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            dummySignIn()
            get(s"/api/datasets/${dummyId}/acl", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
      }

      "POST /api/datasets/:dataset_id/acl" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            post(s"/api/datasets/${datasetId}/acl") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3))))))
            post(s"/api/datasets/test/acl", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3))))))
            signOut()
            post(s"/api/datasets/${datasetId}/acl", params) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3))))))
            val dummyId = UUID.randomUUID.toString
            post(s"/api/datasets/${dummyId}/acl", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3))))))
            signOut()
            dummySignIn()
            post(s"/api/datasets/${datasetId}/acl", params) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "400(BadRequest)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(1))))))
            post(s"/api/datasets/${datasetId}/acl", params) {
              checkStatus(400, BAD_REQUEST)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3))))))
            dbDisconnectedBlock {
              post(s"/api/datasets/${datasetId}/acl", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            post(s"/api/datasets/test/acl") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            post(s"/api/datasets/test/acl") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            dbDisconnectedBlock {
              post(s"/api/datasets/${datasetId}/acl") {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3))))))
            dbDisconnectedBlock {
              post(s"/api/datasets/test/acl", params) {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3))))))
            signOut()
            dbDisconnectedBlock {
              post(s"/api/datasets/${datasetId}/acl", params) {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "404(NotFound) * 403(AccessDenied)" in {
          session {
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3))))))
            val dummyId = UUID.randomUUID.toString
            dummySignIn()
            post(s"/api/datasets/${dummyId}/acl", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied) * 400(BadRequest)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(1))))))
            signOut()
            dummySignIn()
            post(s"/api/datasets/${datasetId}/acl", params) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
      }

      "PUT /api/datasets/:dataset_id/guest_access" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            put(s"/api/datasets/${datasetId}/guest_access") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("accessLevel" -> JInt(1)))))
            put(s"/api/datasets/test/guest_access", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("accessLevel" -> JInt(1)))))
            signOut()
            put(s"/api/datasets/${datasetId}/guest_access", params) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(("accessLevel" -> JInt(1)))))
            put(s"/api/datasets/${dummyId}/guest_access", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("accessLevel" -> JInt(1)))))
            signOut()
            dummySignIn()
            put(s"/api/datasets/${datasetId}/guest_access", params) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("accessLevel" -> JInt(1)))))
            dbDisconnectedBlock {
              put(s"/api/datasets/${datasetId}/guest_access", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            put(s"/api/datasets/test/guest_access") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            put(s"/api/datasets/test/guest_access") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            dbDisconnectedBlock {
              put(s"/api/datasets/${datasetId}/guest_access") {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("accessLevel" -> JInt(1)))))
            dbDisconnectedBlock {
              put(s"/api/datasets/test/guest_access", params) {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("accessLevel" -> JInt(1)))))
            signOut()
            dbDisconnectedBlock {
              put(s"/api/datasets/${datasetId}/guest_access", params) {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "403(Unauthorized) * 404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(("accessLevel" -> JInt(1)))))
            signOut()
            put(s"/api/datasets/${dummyId}/guest_access", params) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
      }

      "DELETE /api/datasets/:dataset_id" - {
        "404(Illegal Argument)" in {
          session {
            signIn()
            delete(s"/api/datasets/test") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            signOut()
            delete(s"/api/datasets/${datasetId}") {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            delete(s"/api/datasets/${dummyId}") {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            signOut()
            dummySignIn()
            delete(s"/api/datasets/${datasetId}") {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            dbDisconnectedBlock {
              delete(s"/api/datasets/${datasetId}") {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            delete(s"/api/datasets/test") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            dbDisconnectedBlock {
              delete(s"/api/datasets/test") {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            signOut()
            dbDisconnectedBlock {
              delete(s"/api/datasets/${datasetId}") {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "403(Unauthorized) * 404(NotFound)" in {
          session {
            val dummyId = UUID.randomUUID.toString
            delete(s"/api/datasets/${dummyId}") {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
      }

      "PUT /api/datasets/:dataset_id/storage" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            put(s"/api/datasets/${datasetId}/storage") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> JBool(false)))))
            put(s"/api/datasets/test/storage", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> JBool(false)))))
            signOut()
            put(s"/api/datasets/${datasetId}/storage", params) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> JBool(false)))))
            put(s"/api/datasets/${dummyId}/storage", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> JBool(false)))))
            signOut()
            dummySignIn()
            put(s"/api/datasets/${datasetId}/storage", params) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> JBool(false)))))
            dbDisconnectedBlock {
              put(s"/api/datasets/${datasetId}/storage", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            put(s"/api/datasets/test/storage") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            put(s"/api/datasets/test/storage") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            dbDisconnectedBlock {
              put(s"/api/datasets/${datasetId}/storage") {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> JBool(false)))))
            dbDisconnectedBlock {
              put(s"/api/datasets/test/storage", params) {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> JBool(false)))))
            signOut()
            dbDisconnectedBlock {
              put(s"/api/datasets/${datasetId}/storage", params) {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "404(NotFound) * 403(AccessDenied)" in {
          session {
            dummySignIn()
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> JBool(false)))))
            put(s"/api/datasets/${dummyId}/storage", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
      }

      "POST /api/datasets/:dataset_id/copy" - {
        "404(Illegal Argument)" in {
          session {
            signIn()
            post(s"/api/datasets/test/copy") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            signOut()
            post(s"/api/datasets/${datasetId}/copy") {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            post(s"/api/datasets/${dummyId}/copy") {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            signOut()
            dummySignIn()
            post(s"/api/datasets/${datasetId}/copy") {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            dbDisconnectedBlock {
              post(s"/api/datasets/${datasetId}/copy") {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            post(s"/api/datasets/test/copy") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            dbDisconnectedBlock {
              post(s"/api/datasets/test/copy") {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            signOut()
            dbDisconnectedBlock {
              post(s"/api/datasets/${datasetId}/copy") {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "403(Unauthorized) * 404(NotFound)" in {
          session {
            val dummyId = UUID.randomUUID.toString
            post(s"/api/datasets/${dummyId}/copy") {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
      }

      "GET /api/datasets/:dataset_id/attributes/export" - {
        "404(Illegal Argument)" in {
          session {
            signIn()
            get(s"/api/datasets/test/attributes/export") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            get(s"/api/datasets/${datasetId}/attributes/export", Map.empty, invalidApiKeyHeader) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            get(s"/api/datasets/${dummyId}/attributes/export") {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            signOut()
            dummySignIn()
            get(s"/api/datasets/${datasetId}/attributes/export") {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            dbDisconnectedBlock {
              get(s"/api/datasets/${datasetId}/attributes/export") {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            get(s"/api/datasets/test/attributes/export", Map.empty, invalidApiKeyHeader) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            get(s"/api/datasets/test/attributes/export") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            signOut()
            dbDisconnectedBlock {
              get(s"/api/datasets/${datasetId}/attributes/export", Map.empty, invalidApiKeyHeader) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "404(NotFound) * 403(AccessDenied)" in {
          session {
            val dummyId = UUID.randomUUID.toString
            dummySignIn()
            get(s"/api/datasets/${dummyId}/attributes/export") {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
      }

      "PUT /api/datasets/:dataset_id/images/featured" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            put(s"/api/datasets/${datasetId}/images/featured") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val imageId = AppConf.defaultDatasetImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            put(s"/api/datasets/test/images/featured", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val imageId = AppConf.defaultDatasetImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            signOut()
            put(s"/api/datasets/${datasetId}/images/featured", params) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val imageId = AppConf.defaultDatasetImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            put(s"/api/datasets/${dummyId}/images/featured", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val imageId = AppConf.defaultDatasetImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            signOut()
            dummySignIn()
            put(s"/api/datasets/${datasetId}/images/featured", params) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val imageId = AppConf.defaultDatasetImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            dbDisconnectedBlock {
              put(s"/api/datasets/${datasetId}/images/featured", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            dummySignIn()
            put(s"/api/datasets/test/images/featured") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            put(s"/api/datasets/test/images/featured") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            dbDisconnectedBlock {
              put(s"/api/datasets/${datasetId}/images/featured") {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val imageId = AppConf.defaultDatasetImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            dbDisconnectedBlock {
              put(s"/api/datasets/test/images/featured", params) {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(NotFound) * 403(AccessDenied)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val imageId = AppConf.defaultDatasetImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            signOut()
            dummySignIn()
            put(s"/api/datasets/${dummyId}/images/featured", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
      }

      "GET /api/datasets/:dataset_id/files" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            get(s"/api/datasets/${datasetId}/files", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/datasets/test/files", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/datasets/${datasetId}/files", params, invalidApiKeyHeader) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/datasets/${dummyId}/files", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            signOut()
            dummySignIn()
            get(s"/api/datasets/${datasetId}/files", params) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            dbDisconnectedBlock {
              get(s"/api/datasets/${datasetId}/files", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            dummySignIn()
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            get(s"/api/datasets/test/files", params, invalidApiKeyHeader) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            get(s"/api/datasets/test/files", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            dbDisconnectedBlock {
              get(s"/api/datasets/${datasetId}/files", params) {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            dbDisconnectedBlock {
              get(s"/api/datasets/test/files", params) {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            signOut()
            dbDisconnectedBlock {
              get(s"/api/datasets/${datasetId}/files", params, invalidApiKeyHeader) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "403(Unauthorized) * 404(NotFound)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            signOut()
            get(s"/api/datasets/${datasetId}/files", params, invalidApiKeyHeader) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
      }

      "GET /api/datasets/:dataset_id/files/:file_id/zippedfiles" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyZipFile)
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/datasets/test/files/test/zippedfiles", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyZipFile)
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params, invalidApiKeyHeader) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val dummyId = UUID.randomUUID.toString
            val fileId = getFileId(datasetId, dummyZipFile)
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/datasets/${dummyId}/files/${fileId}/zippedfiles", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyZipFile)
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            signOut()
            dummySignIn()
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "400(BadRequest)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyFile)
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params) {
              checkStatus(400, BAD_REQUEST)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyZipFile)
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            dbDisconnectedBlock {
              get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            signOut()
            dummySignIn()
            get(s"/api/datasets/${dummyId}/files/test/zippedfiles", params, invalidApiKeyHeader) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            get(s"/api/datasets/${datasetId}/files/test/zippedfiles", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyZipFile)
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            dbDisconnectedBlock {
              get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params) {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            dbDisconnectedBlock {
              get(s"/api/datasets/test/files/test/zippedfiles", params) {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val fileId = getFileId(datasetId, dummyZipFile)
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            dbDisconnectedBlock {
              get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params, invalidApiKeyHeader) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "404(NotFound) * 403(AccessDenied)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val dummyId = UUID.randomUUID.toString
            val fileId = getFileId(datasetId, dummyZipFile)
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            signOut()
            dummySignIn()
            get(s"/api/datasets/${dummyId}/files/${fileId}/zippedfiles", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "404(NotFound) * 400(BadRequest)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val dummyId = UUID.randomUUID.toString
            val fileId = getFileId(datasetId, dummyFile)
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/datasets/${dummyId}/files/${fileId}/zippedfiles", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
      }
    }
  }

  /**
   * DBが切断される独自スコープを持つブロックを作成するためのメソッドです。
   *
   * @param procedure ブロックで行う処理
   * @return ブロックでの処理結果
   */
  private def dbDisconnectedBlock[T](procedure: => T): T = {
    DBsWithEnv("test").close()
    try {
      procedure
    } finally {
      DBsWithEnv("test").setup()
    }
  }

  /**
   * サインアウトします。
   */
  private def signOut() {
    post("/api/signout") {
      checkStatus(200, "OK")
    }
  }

  /**
   * ダミーユーザでサインインします。
   */
  private def dummySignIn(): Unit = {
    post("/api/signin", dummyUserLoginParams) {
      checkStatus(200, "OK")
    }
  }

  /**
   * テストユーザでサインインします。
   */
  private def signIn() {
    val params = Map("d" -> compact(render(("id" -> "dummy1") ~ ("password" -> "password"))))
    post("/api/signin", params) {
      checkStatus(200, "OK")
    }
  }

  /**
   * データセットに画像を追加し、そのIDを取得します。
   *
   * @param datasetId データセットID
   * @return 画像ID
   */
  private def getDatasetImageId(datasetId: String): String = {
    val files = Map("images" -> dummyImage)
    post(s"/api/datasets/${datasetId}/images", Map.empty, files) {
      checkStatus(200, "OK")
      parse(body).extract[AjaxResponse[DatasetAddImages]].data.images.headOption.map(_.id).getOrElse("")
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
      checkStatus(200, "OK")
      parse(body).extract[AjaxResponse[DatasetAddFiles]].data.files.headOption.map(_.id).getOrElse("")
    }
  }

  /**
   * データセットを作成します。
   *
   * @return 作成したデータセット
   */
  private def createDataset(): Dataset = {
    val params = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
    post("/api/datasets", params) {
      checkStatus(200, "OK")
      parse(body).extract[AjaxResponse[Dataset]].data
    }
  }

  /**
   * API呼び出し結果のstatusを確認します。
   *
   * @param statusCode ステータスコード
   * @param statuString ステータス文字列
   */
  private def checkStatus(statusCode: Int, statusString: String): Unit = {
    status should be(statusCode)
    val result = parse(body).extract[AjaxResponse[Any]]
    result.status should be(statusString)
  }
}
