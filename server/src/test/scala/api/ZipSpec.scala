package api

import java.io.File
import java.net.URLEncoder
import java.nio.file.Paths
import java.util.{Base64, UUID}
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import _root_.api.api.logic.SpecCommonLogic
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import dsmoq.AppConf
import dsmoq.controllers.{AjaxResponse, ApiController, FileController, ImageController}
import dsmoq.persistence.{DefaultAccessLevel, GroupAccessLevel, OwnerType, UserAccessLevel}
import dsmoq.services.json.DatasetData.{Dataset, DatasetAddFiles, DatasetAddImages, DatasetDeleteImage, _}
import dsmoq.services.json.GroupData.Group
import dsmoq.services.json.RangeSlice
import dsmoq.services.json.TaskData._
import org.eclipse.jetty.server.Connector
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats, _}
import org.scalatest.{BeforeAndAfter, FreeSpec}
import org.scalatra.servlet.MultipartConfig
import org.scalatra.test.scalatest.ScalatraSuite
import scalikejdbc._
import scalikejdbc.config.DBsWithEnv

class ZipSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormats: Formats = DefaultFormats

  private val dummyFile = new File("README.md")

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

  //NOTE baseUrlの解決に失敗するため、まったく同じロジックでScalatraSuiteとoverrideしている
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

  "API test" - {
    "dataset" - {
      "非ZipファイルをZipファイルで上書きした場合に、zipの中身が更新されるか" in {
        session {
          signIn()
          val files = Map("file[]" -> dummyFile)
          val (datasetId, fileId) = post("/api/datasets", Map.empty, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            val datasetId = result.data.id
            val fileId = result.data.files.head.id
            result.data.files.head.zipedFiles.length should be(0)
            (datasetId, fileId)
          }

          val file = Map("file" -> new File("testdata/test1.zip"))
          post("/api/datasets/" + datasetId + "/files/" + fileId, Map.empty, file) {
            checkStatus()
            val zips = parse(body).extract[AjaxResponse[DatasetFile]].data.zipedFiles
            zips.length should be(2)
          }
        }
      }

      "Zipファイルを非Zipファイルで上書きした場合に、zipの中身が更新されるか" in {
        session {
          signIn()
          val files = Map("file[]" -> new File("testdata/test1.zip"))
          val (datasetId, fileId) = post("/api/datasets", Map.empty, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            val datasetId = result.data.id
            val fileId = result.data.files.head.id
            result.data.files.head.zipedFiles.length should be(2)
            (datasetId, fileId)
          }
          val file = Map("file" -> dummyFile)
          post("/api/datasets/" + datasetId + "/files/" + fileId, Map.empty, file) {
            checkStatus()
            val zips = parse(body).extract[AjaxResponse[DatasetFile]].data.zipedFiles
            zips.length should be(0)
          }
        }
      }

      "ZipファイルをZipファイルで上書きした場合に、zipの中身が更新されるか" in {
        session {
          signIn()
          val files = Map("file[]" -> new File("testdata/test1.zip"))
          val (datasetId, fileId) = post("/api/datasets", Map.empty, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            val datasetId = result.data.id
            val fileId = result.data.files.head.id
            result.data.files.head.zipedFiles.length should be(2)
            (datasetId, fileId)
          }
          val file = Map("file" -> new File("testdata/test2.zip"))
          post("/api/datasets/" + datasetId + "/files/" + fileId, Map.empty, file) {
            checkStatus()
            val zips = parse(body).extract[AjaxResponse[DatasetFile]].data.zipedFiles
            zips.length should be(3)
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
}
