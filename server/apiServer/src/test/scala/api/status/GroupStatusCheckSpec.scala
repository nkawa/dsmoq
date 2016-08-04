package api.status

import java.net.URLEncoder
import java.util.ResourceBundle
import java.util.UUID

import org.eclipse.jetty.servlet.ServletHolder

import _root_.api.api.logic.SpecCommonLogic
import org.scalatest.{BeforeAndAfter, FreeSpec}
import org.scalatra.test.scalatest.ScalatraSuite
import org.json4s.{DefaultFormats, Formats}
import dsmoq.controllers.{ApiController, AjaxResponse}
import scalikejdbc.config.{DBsWithEnv, DBs}
import java.io.File
import org.scalatra.servlet.MultipartConfig
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.{compact, parse, render}
import org.json4s.{DefaultFormats, Formats, JBool, JInt}
import org.scalatest.{BeforeAndAfter, FreeSpec}
import org.scalatra.servlet.MultipartConfig
import org.scalatra.test.scalatest.ScalatraSuite
import scalikejdbc._
import scalikejdbc.config.DBsWithEnv

import dsmoq.AppConf
import dsmoq.services.json.GroupData.Group
import dsmoq.services.json.GroupData.GroupAddImages

class GroupStatusCheckSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormatChecks: Formats = DefaultFormats

  private val dummyImage = new File("../testdata/image/1byteover.png")
  private val dummyFile = new File("../testdata/test1.csv")
  private val dummyZipFile = new File("../testdata/test1.zip")

  private val testUserName = "dummy1"
  private val dummyUserName = "dummy4"
  private val testUserId = "023bfa40-e897-4dad-96db-9fd3cf001e79" // dummy1
  private val dummyUserId = "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04"  // dummy 2
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
    holder.getRegistration.setMultipartConfig(
      MultipartConfig(
        maxFileSize = Some(3 * 1024 * 1024),
        fileSizeThreshold = Some(1 * 1024 * 1024)
      ).toMultipartConfigElement
    )
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
    "group" - {
      "GET /api/groups" - {
        "400(Illegal Argument)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
          get("api/groups", params) {
            checkStatus(400, ILLEGAL_ARGUMENT)
          }
        }
        "403(Unauthorized)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
          get("api/groups", params, invalidApiKeyHeader) {
            checkStatus(403, UNAUTHORIZED)
          }
        }
        "500(NG)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
          dbDisconnectedBlock {
            get("api/groups", params) {
              checkStatus(500, NG)
            }
          }
        }
        "All" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
          get("api/groups", params, invalidApiKeyHeader) {
            checkStatus(400, ILLEGAL_ARGUMENT)
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
          dbDisconnectedBlock {
            get("api/groups", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
          dbDisconnectedBlock {
            get("api/groups", params, invalidApiKeyHeader) {
              checkStatus(500, NG)
            }
          }
        }
      }

      "GET /api/groups/:group_id" - {
        "404(Illegal Argument)" in {
          session {
            signIn()
            get(s"/api/groups/test") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val groupId = createGroup().id
            get(s"/api/groups/${groupId}", Map.empty, invalidApiKeyHeader) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            get(s"/api/groups/${dummyId}") {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            dbDisconnectedBlock {
              get(s"/api/groups/${groupId}") {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            dummySignIn()
            val groupId = createGroup().id
            get(s"/api/groups/test", Map.empty, invalidApiKeyHeader) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            dbDisconnectedBlock {
              get(s"/api/groups/test") {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            dbDisconnectedBlock {
              get(s"/api/groups/${groupId}", Map.empty, invalidApiKeyHeader) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "403(Unauthorized) * 404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            get(s"/api/groups/${dummyId}", Map.empty, invalidApiKeyHeader) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
      }

      "GET /api/groups/:group_id/members"  - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            get(s"/api/groups/${groupId}/members", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/groups/test/members", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/groups/${groupId}/members", params, invalidApiKeyHeader) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/groups/${dummyId}/members", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            dbDisconnectedBlock {
              get(s"/api/groups/${groupId}/members", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            dummySignIn()
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            get(s"/api/groups/test/members", params, invalidApiKeyHeader) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            get(s"/api/groups/test/members", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            dbDisconnectedBlock {
              get(s"/api/groups/${groupId}/members", params) {
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
              get(s"/api/groups/test/members", params) {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            dbDisconnectedBlock {
              get(s"/api/groups/${groupId}/members", params, invalidApiKeyHeader) {
                checkStatus(500, NG)
              }
            }
          }
        }
      }

      "POST /api/groups" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            post("/api/groups") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> ""))))
            post("/api/groups", params) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "400(BadRequest)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> ""))))
            post("/api/groups", params) {
              checkStatus(200, OK)
            }
            post("/api/groups", params) {
              checkStatus(400, BAD_REQUEST)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> ""))))
            dbDisconnectedBlock {
              post("/api/groups", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> ""))))
            post("/api/groups", params) {
              checkStatus(200, OK)
            }
            signOut()
            post("/api/groups") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            dbDisconnectedBlock {
              post("/api/groups") {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> ""))))
            dbDisconnectedBlock {
              post("/api/groups", params) {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
      }

      "PUT /api/groups/:group_id"  - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val groupId = createGroup().id
            put(s"/api/groups/${groupId}") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> "desc1"))))
            put(s"/api/groups/test", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> "desc1"))))
            signOut()
            put(s"/api/groups/${groupId}", params) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> "desc1"))))
            put(s"/api/groups/${dummyId}", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> "desc1"))))
            signOut()
            dummySignIn()
            put(s"/api/groups/${groupId}", params) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "400(BadRequest)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> ""))))
            post("/api/groups", params) {
              checkStatus(200, OK)
            }
            put(s"/api/groups/${groupId}", params) {
              checkStatus(400, BAD_REQUEST)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> "desc1"))))
            dbDisconnectedBlock {
              put(s"/api/groups/${groupId}", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            dummySignIn()
            put(s"/api/groups/test") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            put(s"/api/groups/test") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            dbDisconnectedBlock {
              put(s"/api/groups/${groupId}") {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> "desc1"))))
            dbDisconnectedBlock {
              put(s"/api/groups/test", params) {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> "desc1"))))
            signOut()
            dbDisconnectedBlock {
              put(s"/api/groups/${groupId}", params) {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "403(Unauthorized) * 404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> "desc1"))))
            signOut()
            put(s"/api/groups/${dummyId}", params) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "403(AccessDenied) * 400(BadRequest)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> ""))))
            post("/api/groups", params) {
              checkStatus(200, OK)
            }
            signOut()
            dummySignIn()
            put(s"/api/groups/${groupId}", params) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
      }

      "GET /api/groups/:group_id/images"  - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            get(s"/api/groups/${groupId}/images", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/groups/test/images", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/groups/${groupId}/images", params, invalidApiKeyHeader) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            get(s"/api/groups/${dummyId}/images", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            dbDisconnectedBlock {
              get(s"/api/groups/${groupId}/images", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            get(s"/api/groups/test/images", params, invalidApiKeyHeader) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            get(s"/api/groups/test/images", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
            dbDisconnectedBlock {
              get(s"/api/groups/${groupId}/images", params) {
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
              get(s"/api/groups/test/images", params) {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
            dbDisconnectedBlock {
              get(s"/api/groups/${groupId}/images", params, invalidApiKeyHeader) {
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
            get(s"/api/groups/${dummyId}/images", params, invalidApiKeyHeader) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
      }

      "POST /api/groups/:group_id/images"  - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val groupId = createGroup().id
            post(s"/api/groups/${groupId}/images", Map.empty) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val images = Map("images" -> dummyImage)
            post(s"/api/groups/test/images", Map.empty, images) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val images = Map("images" -> dummyImage)
            signOut()
            post(s"/api/groups/${groupId}/images", Map.empty, images) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val images = Map("images" -> dummyImage)
            post(s"/api/groups/${dummyId}/images", Map.empty, images) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val images = Map("images" -> dummyImage)
            signOut()
            dummySignIn()
            post(s"/api/groups/${groupId}/images", Map.empty, images) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val images = Map("images" -> dummyImage)
            dbDisconnectedBlock {
              post(s"/api/groups/${groupId}/images", Map.empty, images) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            post(s"/api/groups/test/images", Map.empty) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            val groupId = createGroup().id
            post(s"/api/groups/test/images", Map.empty) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            dbDisconnectedBlock {
              post(s"/api/groups/${groupId}/images", Map.empty) {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val images = Map("images" -> dummyImage)
            post(s"/api/groups/test/images", Map.empty, images) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val images = Map("images" -> dummyImage)
            signOut()
            dbDisconnectedBlock {
              post(s"/api/groups/${groupId}/images", Map.empty, images) {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "404(NotFound) * 403(AccessDenied)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val images = Map("images" -> dummyImage)
            signOut()
            dummySignIn()
            post(s"/api/groups/${dummyId}/images", Map.empty, images) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
      }

      "PUT /api/groups/:group_id/images/primary" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val groupId = createGroup().id
            put(s"/api/groups/${groupId}/images/primary") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val imageId = AppConf.defaultGroupImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            put(s"/api/groups/test/images/primary", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val imageId = AppConf.defaultGroupImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            signOut()
            put(s"/api/groups/${groupId}/images/primary", params) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val imageId = AppConf.defaultGroupImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            put(s"/api/groups/${dummyId}/images/primary", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val imageId = AppConf.defaultGroupImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            signOut()
            dummySignIn()
            put(s"/api/groups/${groupId}/images/primary", params) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val imageId = AppConf.defaultGroupImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            dbDisconnectedBlock {
              put(s"/api/groups/${groupId}/images/primary", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            put(s"/api/groups/test/images/primary") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            val groupId = createGroup().id
            put(s"/api/groups/test/images/primary") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            dbDisconnectedBlock {
              put(s"/api/groups/${groupId}/images/primary") {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val imageId = AppConf.defaultGroupImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            dbDisconnectedBlock {
              put(s"/api/groups/test/images/primary", params) {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val imageId = AppConf.defaultGroupImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            signOut()
            dbDisconnectedBlock {
              put(s"/api/groups/${groupId}/images/primary", params) {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "404(NotFound) * 403(AccessDenied)" in {
          session {
            val dummyId = UUID.randomUUID.toString
            val imageId = AppConf.defaultGroupImageId
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            dummySignIn()
            put(s"/api/groups/${dummyId}/images/primary", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
      }

      "DELETE /api/groups/:group_id/images/:image_id" - {
        "404(Illegal Argument)" in {
          session {
            signIn()
            val groupId = createGroup().id
            delete(s"/api/groups/${groupId}/images/test") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val imageId = getGroupImageId(groupId)
            signOut()
            delete(s"/api/groups/${groupId}/images/${imageId}") {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val dummyId = UUID.randomUUID.toString
            delete(s"/api/groups/${groupId}/images/${dummyId}") {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val imageId = getGroupImageId(groupId)
            signOut()
            dummySignIn()
            delete(s"/api/groups/${groupId}/images/${imageId}") {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "400(BadRequest)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val imageId = AppConf.defaultGroupImageId
            delete(s"/api/groups/${groupId}/images/${imageId}") {
              checkStatus(400, BAD_REQUEST)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val imageId = getGroupImageId(groupId)
            dbDisconnectedBlock {
              delete(s"/api/groups/${groupId}/images/${imageId}") {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            val imageId = AppConf.defaultGroupImageId
            delete(s"/api/groups/test/images/${imageId}") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            dbDisconnectedBlock {
              delete(s"/api/groups/${groupId}/images/test") {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val imageId = getGroupImageId(groupId)
            signOut()
            dbDisconnectedBlock {
              delete(s"/api/groups/${groupId}/images/${imageId}") {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "403(Unauthorized) * 404(NotFound)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val dummyId = UUID.randomUUID.toString
            signOut()
            delete(s"/api/groups/${groupId}/images/${dummyId}") {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "403(AccessDenied) * 400(BadRequest)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val imageId = AppConf.defaultGroupImageId
            signOut()
            dummySignIn()
            delete(s"/api/groups/${groupId}/images/${imageId}") {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
      }

      "POST /api/groups/:group_id/members" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val groupId = createGroup().id
            post(s"/api/groups/${groupId}/members") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(Seq(("userId" -> testUserId) ~ ("role" -> JInt(2))))))
            post(s"/api/groups/test/members", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(Seq(("userId" -> testUserId) ~ ("role" -> JInt(2))))))
            signOut()
            post(s"/api/groups/${groupId}/members", params) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(Seq(("userId" -> testUserId) ~ ("role" -> JInt(2))))))
            post(s"/api/groups/${dummyId}/members", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(Seq(("userId" -> testUserId) ~ ("role" -> JInt(2))))))
            signOut()
            dummySignIn()
            post(s"/api/groups/${groupId}/members", params) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(Seq(("userId" -> testUserId) ~ ("role" -> JInt(2))))))
            dbDisconnectedBlock {
              post(s"/api/groups/${groupId}/members", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            post(s"/api/groups/test/members") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            post(s"/api/groups/test/members") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            dbDisconnectedBlock {
              post(s"/api/groups/${groupId}/members") {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(Seq(("userId" -> testUserId) ~ ("role" -> JInt(2))))))
            post(s"/api/groups/test/members", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val params = Map("d" -> compact(render(Seq(("userId" -> testUserId) ~ ("role" -> JInt(2))))))
            signOut()
            dbDisconnectedBlock {
              post(s"/api/groups/${groupId}/members", params) {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "403(Unauthorized) * 404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val params = Map("d" -> compact(render(Seq(("userId" -> testUserId) ~ ("role" -> JInt(2))))))
            signOut()
            post(s"/api/groups/${dummyId}/members", params) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
      }

      "PUT /api/groups/:group_id/members/:user_id" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val groupId = createGroup().id
            addMember(groupId, dummyUserId)
            val userId = dummyUserId
            put(s"/api/groups/${groupId}/members/${userId}") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument)" in {
          session {
            signIn()
            val userId = dummyUserId
            val params = Map("d" -> compact(render(("role" -> JInt(1)))))
            put(s"/api/groups/test/members/${userId}", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val groupId = createGroup().id
            addMember(groupId, dummyUserId)
            val userId = dummyUserId
            val params = Map("d" -> compact(render(("role" -> JInt(1)))))
            signOut()
            put(s"/api/groups/${groupId}/members/${userId}", params) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val userId = dummyUserId
            val params = Map("d" -> compact(render(("role" -> JInt(1)))))
            put(s"/api/groups/${dummyId}/members/${userId}", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val groupId = createGroup().id
            addMember(groupId, dummyUserId)
            val userId = dummyUserId
            val params = Map("d" -> compact(render(("role" -> JInt(3)))))
            signOut()
            dummySignIn()
            put(s"/api/groups/${groupId}/members/${userId}", params) {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "400(BadRequest)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val userId = testUserId
            val params = Map("d" -> compact(render(("role" -> JInt(1)))))
            put(s"/api/groups/${groupId}/members/${userId}", params) {
              checkStatus(400, BAD_REQUEST)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            addMember(groupId, dummyUserId)
            val userId = dummyUserId
            val params = Map("d" -> compact(render(("role" -> JInt(1)))))
            dbDisconnectedBlock {
              put(s"/api/groups/${groupId}/members/${userId}", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            val dummyId = UUID.randomUUID.toString
            put(s"/api/groups/test/members/${dummyId}") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 404(Illegal Argument)" in {
          session {
            signIn()
            val userId = dummyUserId
            put(s"/api/groups/test/members/${userId}") {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            addMember(groupId, dummyUserId)
            val userId = dummyUserId
            dbDisconnectedBlock {
              put(s"/api/groups/${groupId}/members/${userId}") {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val userId = dummyUserId
            val params = Map("d" -> compact(render(("role" -> JInt(1)))))
            put(s"/api/groups/test/members/${userId}", params) {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            addMember(groupId, dummyUserId)
            val userId = dummyUserId
            val params = Map("d" -> compact(render(("role" -> JInt(1)))))
            signOut()
            dbDisconnectedBlock {
              put(s"/api/groups/${groupId}/members/${userId}", params) {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "404(NotFound) * 403(AccessDenied)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val userId = dummyUserId
            val params = Map("d" -> compact(render(("role" -> JInt(1)))))
            signOut()
            dummySignIn()
            put(s"/api/groups/${dummyId}/members/${userId}", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "404(NotFound) * 400(BadRequest)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            val userId = testUserId
            val params = Map("d" -> compact(render(("role" -> JInt(1)))))
            put(s"/api/groups/${dummyId}/members/${userId}", params) {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
      }

      "DELETE /api/groups/:group_id/members/:user_id" - {
        "404(Illegal Argument)" in {
          session {
            signIn()
            val groupId = createGroup().id
            delete(s"/api/groups/${groupId}/members/test") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val groupId = createGroup().id
            addMember(groupId, dummyUserId)
            val userId = dummyUserId
            signOut()
            delete(s"/api/groups/${groupId}/members/${userId}") {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val groupId = createGroup().id
            addMember(groupId, dummyUserId)
            val dummyId = UUID.randomUUID.toString
            delete(s"/api/groups/${groupId}/members/${dummyId}") {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val groupId = createGroup().id
            addMember(groupId, dummyUserId)
            val userId = dummyUserId
            signOut()
            dummySignIn()
            delete(s"/api/groups/${groupId}/members/${userId}") {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "400(BadRequest)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val userId = testUserId
            delete(s"/api/groups/${groupId}/members/${userId}") {
              checkStatus(400, BAD_REQUEST)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            addMember(groupId, dummyUserId)
            val userId = dummyUserId
            dbDisconnectedBlock {
              delete(s"/api/groups/${groupId}/members/${userId}") {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            val dummyId = UUID.randomUUID.toString
            delete(s"/api/groups/${dummyId}/members/test") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            delete(s"/api/groups/${groupId}/members/test") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            addMember(groupId, dummyUserId)
            val userId = dummyUserId
            signOut()
            dbDisconnectedBlock {
              delete(s"/api/groups/${groupId}/members/${userId}") {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "403(Unauthorized) * 404(NotFound)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val dummyId = UUID.randomUUID.toString
            signOut()
            delete(s"/api/groups/${groupId}/members/${dummyId}") {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "403(AccessDenied) * 400(BadRequest)" in {
          session {
            signIn()
            val groupId = createGroup().id
            val userId = testUserId
            signOut()
            dummySignIn()
            delete(s"/api/groups/${groupId}/members/${userId}") {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
      }

      "DELETE /api/groups/:group_id" - {
        "404(Illegal Argument)" in {
          session {
            signIn()
            delete(s"/api/groups/test") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          session {
            signIn()
            val groupId = createGroup().id
            signOut()
            delete(s"/api/groups/${groupId}") {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
        "404(NotFound)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            delete(s"/api/groups/${dummyId}") {
              checkStatus(404, NOT_FOUND)
            }
          }
        }
        "403(AccessDenied)" in {
          session {
            signIn()
            val groupId = createGroup().id
            signOut()
            dummySignIn()
            delete(s"/api/groups/${groupId}") {
              checkStatus(403, ACCESS_DENIED)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            dbDisconnectedBlock {
              delete(s"/api/groups/${groupId}") {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          session {
            delete(s"/api/groups/test") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            dbDisconnectedBlock {
              delete(s"/api/groups/test") {
                checkStatus(404, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          session {
            signIn()
            val groupId = createGroup().id
            signOut()
            dbDisconnectedBlock {
              delete(s"/api/groups/${groupId}") {
                checkStatus(403, UNAUTHORIZED)
              }
            }
          }
        }
        "404(NotFound) * 403(AccessDenied)" in {
          session {
            signIn()
            val dummyId = UUID.randomUUID.toString
            signOut()
            dummySignIn()
            delete(s"/api/groups/${dummyId}") {
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
   * グループにメンバーを追加します。
   *
   * @param groupId グループID
   * @param userId 追加するユーザID
   */
  private def addMember(groupId: String, userId: String): Unit = {
    val params = Map("d" -> compact(render(Seq(("userId" -> userId) ~ ("role" -> JInt(1))))))
    post(s"/api/groups/${groupId}/members", params) {
      checkStatus(200, "OK")
    }
  }

  /**
   * グループに画像を追加し、そのIDを取得します。
   *
   * @param groupId グループID
   * @return 画像ID
   */
  private def getGroupImageId(groupId: String): String = {
    val files = Map("images" -> dummyImage)
    post(s"/api/groups/${groupId}/images", Map.empty, files) {
      checkStatus(200, "OK")
      parse(body).extract[AjaxResponse[GroupAddImages]].data.images.headOption.map(_.id).getOrElse("")
    }
  }

  /**
   * グループを作成します。
   *
   * @return 作成したグループ
   */
  private def createGroup(): Group = {
    val params = Map("d" -> compact(render(("name" -> "group1") ~ ("description" -> "des1"))))
    post("/api/groups", params) {
      checkStatus(200, "OK")
      parse(body).extract[AjaxResponse[Group]].data
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
