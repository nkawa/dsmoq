package api

import java.io.File
import java.net.URLEncoder
import java.nio.file.Paths
import java.util.{ Base64, UUID }
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.ResourceBundle

import org.eclipse.jetty.servlet.ServletHolder

import _root_.api.api.logic.SpecCommonLogic
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import dsmoq.AppConf
import dsmoq.controllers.{ AjaxResponse, ApiController, FileController, ImageController }
import dsmoq.persistence.{ DefaultAccessLevel, GroupAccessLevel, OwnerType, UserAccessLevel }
import dsmoq.services.json.DatasetData.{ Dataset, DatasetAddFiles, DatasetAddImages, DatasetDeleteImage, _ }
import dsmoq.services.json.GroupData.Group
import dsmoq.services.json.RangeSlice
import dsmoq.services.json.TaskData._
import org.eclipse.jetty.server.Connector
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.{ DefaultFormats, Formats, _ }
import org.scalatest.{ BeforeAndAfter, FreeSpec }
import org.scalatra.servlet.MultipartConfig
import org.scalatra.test.scalatest.ScalatraSuite
import scalikejdbc._
import scalikejdbc.config.DBsWithEnv

class ZipSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormats: Formats = DefaultFormats

  private val dummyFile = new File("../README.md")

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
      "非ZipファイルをZipファイルで上書きした場合に、zipの中身が更新されるか" in {
        session {
          signIn()
          val files = Map("file[]" -> dummyFile)
          val params = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
          val (datasetId, fileId) = post("/api/datasets", params, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            val datasetId = result.data.id
            val fileId = result.data.files.head.id
            result.data.files.head.zipedFiles.length should be(0)
            (datasetId, fileId)
          }

          val file = Map("file" -> new File("../testdata/test1.zip"))
          val (fileUrl, zipUrl) = post("/api/datasets/" + datasetId + "/files/" + fileId, Map.empty, file) {
            checkStatus()
            val f = parse(body).extract[AjaxResponse[DatasetFile]].data
            f.zipedFiles.length should be(2)
            (f.url, f.zipedFiles.head.url)
          }

          get(new java.net.URI(fileUrl).getPath) {
            status should be(200)
          }

          get(new java.net.URI(zipUrl).getPath) {
            status should be(200)
          }
        }
      }

      "Zipファイルを非Zipファイルで上書きした場合に、zipの中身が更新されるか" in {
        session {
          signIn()
          val files = Map("file[]" -> new File("../testdata/test1.zip"))
          val params = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
          val (datasetId, fileId) = post("/api/datasets", params, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            val datasetId = result.data.id
            val fileId = result.data.files.head.id
            result.data.files.head.zipedFiles.length should be(2)
            (datasetId, fileId)
          }
          val file = Map("file" -> dummyFile)
          val url = post("/api/datasets/" + datasetId + "/files/" + fileId, Map.empty, file) {
            checkStatus()
            val f = parse(body).extract[AjaxResponse[DatasetFile]].data
            f.zipedFiles.length should be(0)
            f.url
          }

          get(new java.net.URI(url).getPath) {
            status should be(200)
          }
        }
      }

      "ZipファイルをZipファイルで上書きした場合に、zipの中身が更新されるか" in {
        session {
          signIn()
          val files = Map("file[]" -> new File("../testdata/test1.zip"))
          val params = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
          val (datasetId, fileId) = post("/api/datasets", params, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            val datasetId = result.data.id
            val fileId = result.data.files.head.id
            result.data.files.head.zipedFiles.length should be(2)
            (datasetId, fileId)
          }
          val file = Map("file" -> new File("../testdata/test2.zip"))
          val (fileUrl, zipUrl) = post("/api/datasets/" + datasetId + "/files/" + fileId, Map.empty, file) {
            checkStatus()
            val f = parse(body).extract[AjaxResponse[DatasetFile]].data
            f.zipedFiles.length should be(3)
            (f.url, f.zipedFiles.head.url)
          }

          get(new java.net.URI(fileUrl).getPath) {
            status should be(200)
          }

          get(new java.net.URI(zipUrl).getPath) {
            status should be(200)
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
