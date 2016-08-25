package api

import _root_.api.api.logic.SpecCommonLogic
import dsmoq.controllers.{ AjaxResponse, FileController, ApiController }
import dsmoq.persistence.DefaultAccessLevel
import dsmoq.services.json.DatasetData.{ Dataset, DatasetFile }
import dsmoq.services.json.RangeSlice
import java.io.File
import java.net.URLEncoder
import java.util.Base64
import java.util.ResourceBundle
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.eclipse.jetty.servlet.ServletHolder
import org.json4s.JInt
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.{ DefaultFormats, Formats }
import org.scalatest.{ BeforeAndAfter, FreeSpec }
import org.scalatra.servlet.MultipartConfig
import org.scalatra.test.scalatest.ScalatraSuite
import scalikejdbc.config.{ DBsWithEnv, DBs }

trait GuestHeaderBehaviors { this: FreeSpec with ScalatraSuite =>

  protected implicit val jsonFormats: Formats = DefaultFormats

  private val dummyFile = new File("../README.md")

  def guestHeaderCheck(sessionUser: Boolean, resource: Boolean, permission: Boolean, innerError: Boolean)(expected: => Any): Unit = {
    val uuid = UUID.randomUUID.toString
    s"guest header check (sessionUser: ${sessionUser}, resource: ${resource}, permission: ${permission}, innerError: ${innerError}) - ${uuid}" in {
      val datasetId = session {
        signInDummy1()
        createDataset(permission)
      }
      session {
        if (sessionUser) {
          if (permission) {
            signInDummy1()
          } else {
            signInDummy2()
          }
        }
        val proc = () => {
          get(s"/api/datasets/${if (resource) datasetId else uuid}") {
            expected
          }
        }
        if (innerError) {
          dbDisconnectedBlock {
            proc()
          }
        } else {
          proc()
        }
      }
    }
  }

  def signInDummy1() {
    signIn("dummy1", "password")
  }

  def signInDummy2() {
    signIn("dummy2", "password")
  }

  def signIn(id: String, password: String) {
    post("/api/signin", params = Map("d" -> compact(render(("id" -> id) ~ ("password" -> password))))) {
      checkAjaxStatus()
    }
  }

  def createDataset(allowGuest: Boolean = false): String = {
    val params = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
    val files = Map("file[]" -> dummyFile)
    val dataset = post("/api/datasets", params, files) {
      checkAjaxStatus()
      parse(body).extract[AjaxResponse[Dataset]].data
    }
    if (allowGuest) {
      val params = Map("d" -> compact(render(("accessLevel" -> JInt(DefaultAccessLevel.FullPublic)))))
      put(s"/api/datasets/${dataset.id}/guest_access", params) {
        checkAjaxStatus()
      }
    }
    dataset.id
  }

  def checkAjaxStatus(expectedCode: Int = 200, expectedStatus: Option[String] = Some("OK")) {
    status should be(expectedCode)
    expectedStatus.foreach { expected =>
      val result = parse(body).extract[AjaxResponse[Any]]
      result.status should be(expected)
    }
  }

  def dbDisconnectedBlock[T](procedure: => T): T = {
    DBsWithEnv("test").close()
    try {
      procedure
    } finally {
      DBsWithEnv("test").setup()
    }
  }
}

class GuestHeaderSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter with GuestHeaderBehaviors {

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
    SpecCommonLogic.insertDummyData()
  }

  after {
    SpecCommonLogic.deleteAllCreateData()
  }

  "Guest header test" - {
    for {
      sessionUser <- Seq(true, false)
      resource <- Seq(true, false)
      permission <- Seq(true, false)
      innerError <- Seq(true, false)
      if resource || !permission
    } {
      guestHeaderCheck(sessionUser, resource, permission, innerError) {
        if (!innerError) {
          header.get("isGuest") should be(Some((!sessionUser).toString))
        }
      }
    }
  }
}
