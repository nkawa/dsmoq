package api.common

import java.io.File
import java.util.ResourceBundle

import org.eclipse.jetty.servlet.ServletHolder
import org.json4s.DefaultFormats
import org.json4s.Formats
import org.json4s.JInt
import org.json4s.JsonDSL.pair2Assoc
import org.json4s.JsonDSL.pair2jvalue
import org.json4s.JsonDSL.string2jvalue
import org.json4s.jackson.JsonMethods.compact
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.JsonMethods.render
import org.scalatest.BeforeAndAfter
import org.scalatest.FreeSpec
import org.scalatra.servlet.MultipartConfig
import org.scalatra.test.scalatest.ScalatraSuite

import dsmoq.controllers.AjaxResponse
import dsmoq.controllers.ApiController
import dsmoq.controllers.AppController
import dsmoq.controllers.DateTimeSerializer
import dsmoq.controllers.FileController
import dsmoq.controllers.ImageController
import dsmoq.controllers.json.SearchDatasetParams
import dsmoq.controllers.json.SearchDatasetParamsSerializer
import dsmoq.persistence.DefaultAccessLevel
import dsmoq.services.json.DatasetData.Dataset
import dsmoq.services.json.RangeSlice
import dsmoq.services.json.SearchDatasetCondition
import dsmoq.services.json.SearchDatasetConditionSerializer
import scalikejdbc.config.DBsWithEnv

trait DsmoqSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormats: Formats = DefaultFormats + DateTimeSerializer +
    SearchDatasetConditionSerializer + SearchDatasetParamsSerializer

  val resource = ResourceBundle.getBundle("message")

  override def beforeAll() {
    super.beforeAll()
    DBsWithEnv("test").setup()
    System.setProperty(org.scalatra.EnvironmentKey, "test")

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
    addServlet(new AppController(resource), "/apps/*")
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

  def signIn(id: String = "dummy1", password: String = "password"): Unit = {
    post("/api/signin", params = Map("d" -> compact(render(("id" -> id) ~ ("password" -> password))))) {
      checkStatus()
    }
  }

  def createDataset(allowGuest: Boolean = false, name: String = "test", file: Option[File] = None): String = {
    val fileParam = file.map { x => Map("file[]" -> x) }.getOrElse(Map.empty)
    val params = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> name)
    val dataset = post("/api/datasets", params, fileParam) {
      checkStatus()
      parse(body).extract[AjaxResponse[Dataset]].data
    }
    if (allowGuest) {
      val params = Map("d" -> compact(render(("accessLevel" -> JInt(DefaultAccessLevel.FullPublic)))))
      put(s"/api/datasets/${dataset.id}/guest_access", params) {
        checkStatus()
      }
    }
    dataset.id
  }

  def checkStatus(expectedCode: Int = 200, expectedAjaxStatus: Option[String] = Some("OK")) {
    status should be(expectedCode)
    expectedAjaxStatus.foreach { expected =>
      val result = parse(body).extract[AjaxResponse[Any]]
      result.status should be(expected)
    }
  }
}
