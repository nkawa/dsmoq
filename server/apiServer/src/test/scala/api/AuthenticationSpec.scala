package api

import _root_.api.api.logic.SpecCommonLogic
import dsmoq.controllers.{ AjaxResponse, FileController, ApiController }
import dsmoq.persistence.{ DefaultAccessLevel, User }
import dsmoq.persistence.PostgresqlHelper._
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
import scalikejdbc._
import scalikejdbc.config.{ DBsWithEnv, DBs }

trait AuthenticationBehaviors { this: FreeSpec with ScalatraSuite =>

  protected implicit val jsonFormats: Formats = DefaultFormats

  private val dummyFile = new File("../README.md")

  def authenticationCheckForDataset(
    sessionUser: Boolean = false,
    allowGuest: Boolean = false,
    headers: Map[String, String] = Map.empty
  )(expected: => Any): Unit = {
    s"authentication check for dataset (sessionUser: ${sessionUser}, allowGuest: ${allowGuest}, headers: ${headers}) - ${UUID.randomUUID.toString}" in {
      val datasetId = session {
        signInDummy1()
        createDataset(allowGuest)
      }
      session {
        if (sessionUser) {
          signInDummy1()
        }
        get(s"/api/datasets/${datasetId}", headers = headers) {
          expected
        }
      }
    }
  }

  def authenticationCheckForFile(
    sessionUser: Boolean = false,
    allowGuest: Boolean = false,
    headers: Map[String, String] = Map.empty
  )(expected: => Any): Unit = {
    s"authentication check for file (sessionUser: ${sessionUser}, allowGuest: ${allowGuest}, headers: ${headers}) - ${UUID.randomUUID.toString}" in {
      val (datasetId, fileId) = session {
        signInDummy1()
        val datasetId = createDataset(allowGuest)
        val fileId = get(s"/api/datasets/${datasetId}/files") {
          checkAjaxStatus()
          val files = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]].data.results
          val file = files.headOption.orNull
          file should not be (null)
          file.id
        }
        (datasetId, fileId)
      }
      session {
        if (sessionUser) {
          signInDummy1()
        }
        get(s"/files/${datasetId}/${fileId}", headers = headers) {
          expected
        }
      }
    }
  }

  def signInDummy1() {
    signIn("dummy1", "password")
  }

  def signIn(id: String, password: String) {
    post("/api/signin", params = Map("d" -> compact(render(("id" -> id) ~ ("password" -> password))))) {
      checkAjaxStatus()
    }
  }

  def disableDummy1() {
    DB.localTx { implicit s =>
      withSQL {
        val u = User.column
        update(User)
          .set(u.disabled -> true)
          .where
          .eqUuid(u.id, "023bfa40-e897-4dad-96db-9fd3cf001e79")
      }.update.apply()
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

  def checkResonse(expectedCode: Int = 200, expectedBody: Option[String] = None) {
    status should be(expectedCode)
    expectedBody.foreach { expected =>
      body should be(expected)
    }
  }
}

class AuthenticationSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter with AuthenticationBehaviors {

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

  "Authentication test" - {
    "to api" - {
      for {
        sessionUser <- Seq(true, false)
        allowGuest <- Seq(true, false)
        headers <- testHeaders
      } {
        authenticationCheckForDataset(sessionUser, allowGuest, headers)(datasetExpected(sessionUser, allowGuest, headers))
      }
    }
    "to file" - {
      for {
        sessionUser <- Seq(true, false)
        allowGuest <- Seq(true, false)
        headers <- testHeaders
      } {
        authenticationCheckForFile(sessionUser, allowGuest, headers)(fileExpected(sessionUser, allowGuest, headers))
      }
    }
  }

  "Disabled user" - {
    "Authorization Header" in {
      val datasetId = session {
        signInDummy1()
        createDataset(true)
      }
      disableDummy1()
      val apiKey = "5dac067a4c91de87ee04db3e3c34034e84eb4a599165bcc9741bb9a91e8212cb"
      val signature = "nFGVWB7iGxemC2D0wQ177hjla7Q%3D"
      val headers = Map("Authorization" -> s"api_key=${apiKey},signature=${signature}")
      get(s"/api/datasets/${datasetId}", headers = headers) {
        checkAjaxStatus(403, Some("Unauthorized"))
      }
    }
    "Session" in {
      disableDummy1()
      session {
        post("/api/signin", params = Map("d" -> compact(render(("id" -> "dummy1") ~ ("password" -> "password"))))) {
          checkAjaxStatus(400, Some("BadRequest"))
        }
      }
    }
  }

  private def testHeaders: Seq[Map[String, String]] = {
    val es: Seq[Map[String, String]] = Seq(
      Map.empty,
      Map("Authorization" -> ""),
      Map("Authorization" -> ",,,"),
      Map("Authorization" -> ",,,hoge=piyo")
    )
    val ns: Seq[Map[String, String]] = for {
      apiKey <- Seq("", "hello", "5dac067a4c91de87ee04db3e3c34034e84eb4a599165bcc9741bb9a91e8212cb")
      signature <- Seq("", "world", "nFGVWB7iGxemC2D0wQ177hjla7Q%3D")
      ext <- Seq("", ",a=b")
    } yield {
      Map("Authorization" -> s"api_key=${apiKey},signature=${signature}${ext}")
    }
    es ++ ns
  }

  private def datasetExpected(sessionUser: Boolean, allowGuest: Boolean, headers: Map[String, String]): Unit = {
    headers.get("Authorization") match {
      case None | Some("") => {
        checkAjaxStatus(if (sessionUser || allowGuest) 200 else 403, Some(if (sessionUser || allowGuest) "OK" else "AccessDenied"))
      }
      case Some(v) if v.startsWith("api_key=5dac067a4c91de87ee04db3e3c34034e84eb4a599165bcc9741bb9a91e8212cb,signature=nFGVWB7iGxemC2D0wQ177hjla7Q") => {
        checkAjaxStatus()
      }
      case Some(_) => {
        checkAjaxStatus(403, Some("Unauthorized"))
      }
    }
  }

  private def fileExpected(sessionUser: Boolean, allowGuest: Boolean, headers: Map[String, String]): Unit = {
    headers.get("Authorization") match {
      case None | Some("") => {
        if (sessionUser || allowGuest) checkResonse() else checkResonse(403, Some("Access Denied"))
      }
      case Some(v) if v.startsWith("api_key=5dac067a4c91de87ee04db3e3c34034e84eb4a599165bcc9741bb9a91e8212cb,signature=nFGVWB7iGxemC2D0wQ177hjla7Q") => {
        checkResonse()
      }
      case Some(_) => {
        checkResonse(403, Some("Unauthorized"))
      }
    }
  }
}
