package api

import java.io.File

import org.json4s.JInt
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import common.DsmoqSpec
import dsmoq.controllers.AjaxResponse
import dsmoq.persistence.PostgresqlHelper._
import dsmoq.persistence.User
import dsmoq.services.json.DatasetData.{ Dataset, DatasetFile }
import dsmoq.services.json.RangeSlice
import scalikejdbc._

class AuthenticationSpec extends DsmoqSpec {
  private val dummyFile = new File("../README.md")

  "Authentication test" - {
    "to api" in {
      for {
        sessionUser <- Seq(true, false)
        allowGuest <- Seq(true, false)
        headers <- testHeaders
      } {
        authenticationCheckForDataset(sessionUser, allowGuest, headers)(datasetExpected(sessionUser, allowGuest, headers))
      }
    }
    "to file" in {
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
        signIn()
        createDataset(true)
      }
      disableDummy1()
      val apiKey = "5dac067a4c91de87ee04db3e3c34034e84eb4a599165bcc9741bb9a91e8212cb"
      val signature = "nFGVWB7iGxemC2D0wQ177hjla7Q%3D"
      val headers = Map("Authorization" -> s"api_key=${apiKey},signature=${signature}")
      get(s"/api/datasets/${datasetId}", headers = headers) {
        checkStatus(403, Some("Unauthorized"))
      }
    }
    "Session" in {
      disableDummy1()
      session {
        post("/api/signin", params = Map("d" -> compact(render(("id" -> "dummy1") ~ ("password" -> "password"))))) {
          checkStatus(400, Some("BadRequest"))
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
        checkStatus(if (sessionUser || allowGuest) 200 else 403, Some(if (sessionUser || allowGuest) "OK" else "AccessDenied"))
      }
      case Some(v) if v.startsWith("api_key=5dac067a4c91de87ee04db3e3c34034e84eb4a599165bcc9741bb9a91e8212cb,signature=nFGVWB7iGxemC2D0wQ177hjla7Q") => {
        checkStatus()
      }
      case Some(_) => {
        checkStatus(403, Some("Unauthorized"))
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

  def authenticationCheckForDataset(
    sessionUser: Boolean = false,
    allowGuest: Boolean = false,
    headers: Map[String, String] = Map.empty
  )(expected: => Any): Unit = {
    withClue(s"for dataset - sessionUser: ${sessionUser}, allowGuest: ${allowGuest}, headers: ${headers}") {
      val datasetId = session {
        signIn()
        createDataset(allowGuest)
      }
      session {
        if (sessionUser) {
          signIn()
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
    withClue(s"for file - sessionUser: ${sessionUser}, allowGuest: ${allowGuest}, headers: ${headers}") {
      val (datasetId, fileId) = session {
        signIn()
        val datasetId = createDataset(allowGuest = allowGuest, file = Some(dummyFile))
        val fileId = get(s"/api/datasets/${datasetId}/files") {
          checkStatus()
          val files = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]].data.results
          val file = files.headOption.orNull
          file should not be (null)
          file.id
        }
        (datasetId, fileId)
      }
      session {
        if (sessionUser) {
          signIn()
        }
        get(s"/files/${datasetId}/${fileId}", headers = headers) {
          expected
        }
      }
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

  def checkResonse(expectedCode: Int = 200, expectedBody: Option[String] = None) {
    status should be(expectedCode)
    expectedBody.foreach { expected =>
      body should be(expected)
    }
  }
}
