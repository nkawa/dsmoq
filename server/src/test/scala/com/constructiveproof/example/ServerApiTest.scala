package com.constructiveproof.example

import com.constructiveproof.example.facade.{Datasets, User}
import org.scalatest.FreeSpec
import org.scalatra.test.scalatest._

import org.json4s._
import org.json4s.jackson.JsonMethods._

class ServerApiTest extends FreeSpec with ScalatraSuite {
  protected implicit val jsonFormats: Formats = DefaultFormats

  addServlet(classOf[ServerApiController], "/api/*")

  "API test" - {
    "profile" - {
      "is guest" in {
        get("/api/profile") {
          status should equal (200)
          val result = parse(body).extract[AjaxResponse[User]]
          assert(result === AjaxResponse("OK", User(
            "", "", "", "", "", "http://xxxx", true
          )))
        }
      }
    }
    "signin" - {
      "is success" in {
        val params = Map("id" -> "foo", "password" -> "foo")
        post("/api/signin", params) {
          status should equal (200)
          assert(body == """{"status":"OK","data":{}}""")
          response.getHeader("Set-Cookie") should not equal (null)
        }
      }
    }
    "signout" - {
      "is success" in {
        post("/api/signout") {
          status should equal (200)
          assert(body == """{"status":"OK","data":{}}""")
        }
      }
    }

    "datasets" - {
      "can get" in {
        get("/api/datasets") {
          status should equal (200)
          val result = parse(body).extract[AjaxResponse[Datasets]]
          result.status should equal ("OK")
          result.data.summary.count should equal (20)
          val datasetResult = result.data.results(0)
          datasetResult.license should equal (1)
        }
      }
    }
  }

  "Authentication" - {
    "sign-in user" - {
      "profile is not guest" in {
        session {
          val params = Map("id" -> "foo", "password" -> "foo")
          post("/api/signin", params) { status should equal (200) }
          get("/api/profile") {
            status should equal (200)
            val result = parse(body).extract[AjaxResponse[User]]
            assert(!result.data.isGuest)
            post("/api/signout") { status should equal (200) }
          }
        }
      }
    }
    "sign-out user" - {
      "profile is guest" in {
        session {
          val params = Map("id" -> "foo", "password" -> "foo")
          post("/api/signin", params) { status should equal (200) }
          post("/api/signout") { status should equal (200) }
          get("/api/profile") {
            status should equal (200)
            val result = parse(body).extract[AjaxResponse[User]]
            assert(result.data.isGuest)
          }
        }
      }
    }
  }
}
