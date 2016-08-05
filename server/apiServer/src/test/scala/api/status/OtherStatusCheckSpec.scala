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
import dsmoq.services.json.DatasetData.Dataset
import dsmoq.services.json.DatasetData.DatasetAddFiles
import dsmoq.services.json.DatasetData.DatasetAddImages
import dsmoq.services.json.DatasetData.DatasetTask

class OtherStatusCheckSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
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
    "other" - {
      "GET /api/licenses" - {
        "500(NG)" in {
          dbDisconnectedBlock {
            get("/api/licenses") {
              checkStatus(500, NG)
            }
          }
        }
      }

      "GET /api/accounts" - {
        "500(NG)" in {
          dbDisconnectedBlock {
            get("/api/accounts") {
              checkStatus(500, NG)
            }
          }
        }
      }

      "GET /api/tags" - {
        "500(NG)" in {
          dbDisconnectedBlock {
            get("/api/tags") {
              checkStatus(500, NG)
            }
          }
        }
      }

      "GET /api/suggests/users" - {
        "400(Illegal Argument)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
          get("/api/suggests/users", params) {
            checkStatus(400, ILLEGAL_ARGUMENT)
          }
        }
        "500(NG)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
          dbDisconnectedBlock {
            get("/api/suggests/users", params) {
              checkStatus(500, NG)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
          dbDisconnectedBlock {
            get("/api/suggests/users", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
      }

      "GET /api/suggests/groups" - {
        "400(Illegal Argument)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
          get("/api/suggests/groups", params) {
            checkStatus(400, ILLEGAL_ARGUMENT)
          }
        }
        "500(NG)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
          dbDisconnectedBlock {
            get("/api/suggests/groups", params) {
              checkStatus(500, NG)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
          dbDisconnectedBlock {
            get("/api/suggests/groups", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
      }

      "GET /api/suggests/users_and_groups" - {
        "400(Illegal Argument)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
          get("/api/suggests/users_and_groups", params) {
            checkStatus(400, ILLEGAL_ARGUMENT)
          }
        }
        "500(NG)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
          dbDisconnectedBlock {
            get("/api/suggests/users_and_groups", params) {
              checkStatus(500, NG)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
          dbDisconnectedBlock {
            get("/api/suggests/users_and_groups", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
      }

      "GET /api/suggests/attributes" - {
        "400(Illegal Argument)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
          get("/api/suggests/attributes", params) {
            checkStatus(400, ILLEGAL_ARGUMENT)
          }
        }
        "500(NG)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(1)))))
          dbDisconnectedBlock {
            get("/api/suggests/attributes", params) {
              checkStatus(500, NG)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          val params = Map("d" -> compact(render(("limit" -> JInt(-1)))))
          dbDisconnectedBlock {
            get("/api/suggests/attributes", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
      }

      "GET /api/tasks/:task_id" - {
        "404(Illegal Argument)" in {
          get(s"/api/tasks/test") {
            checkStatus(404, ILLEGAL_ARGUMENT)
          }
        }
        "404(NotFound)" in {
          val dummyId = UUID.randomUUID.toString
          get(s"/api/tasks/${dummyId}") {
            checkStatus(404, NOT_FOUND)
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val datasetId = createDataset().id
            val taskId = createDatasetTask(datasetId)
            dbDisconnectedBlock {
              get(s"/api/tasks/${taskId}") {
                checkStatus(500, NG)
              }
            }
          }
        }
        "404(Illegal Argument) * 500(NG)" in {
          dbDisconnectedBlock {
            get(s"/api/tasks/test") {
              checkStatus(404, ILLEGAL_ARGUMENT)
            }
          }
        }
      }

      "GET /api/statistics" - {
        "400(Illegal Argument)" in {
          val params = Map("d" -> "test")
          get("/api/statistics", params) {
            checkStatus(400, ILLEGAL_ARGUMENT)
          }
        }
        "500(NG)" in {
          dbDisconnectedBlock {
            get("/api/statistics") {
              checkStatus(500, NG)
            }
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          val params = Map("d" -> "test")
          dbDisconnectedBlock {
            get("/api/statistics", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
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
   * テストユーザでサインインします。
   */
  private def signIn() {
    val params = Map("d" -> compact(render(("id" -> "dummy1") ~ ("password" -> "password"))))
    post("/api/signin", params) {
      checkStatus(200, "OK")
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
   * データセットの保存先を変更し、タスクを生成して、そのIDを取得します。
   *
   * @param datasetId データセットID
   * @return タスクID
   */
  private def createDatasetTask(datasetId: String): String = {
    val params = Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> JBool(true)))))
    put(s"/api/datasets/${datasetId}/storage", params) {
      checkStatus(200, "OK")
      parse(body).extract[AjaxResponse[DatasetTask]].data.taskId
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
