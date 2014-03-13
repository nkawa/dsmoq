package com.constructiveproof.example

import com.constructiveproof.example.facade.{Dataset, Datasets, User}
import org.scalatest.{BeforeAndAfter, FreeSpec}
import org.scalatra.test.scalatest._

import org.json4s._
import org.json4s.jackson.JsonMethods._
import scalikejdbc.config.DBs

class ServerApiTest extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormats: Formats = DefaultFormats

  addServlet(classOf[ServerApiController], "/api/*")

  before {
    DBs.setup()
  }
  after {
    DBs.close()
  }

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
      "with user name" - {
        "is success" in {
          val params = Map("id" -> "test", "password" -> "foo")
          post("/api/signin", params) {
            status should equal (200)
            assert(body == """{"status":"OK","data":{}}""")
            response.getHeader("Set-Cookie") should not equal (null)
          }
        }
      }
      "with mail address" - {
        "is success" in {
          val params = Map("id" -> "test@example.com", "password" -> "foo")
          post("/api/signin", params) {
            status should equal (200)
            assert(body == """{"status":"OK","data":{}}""")
            response.getHeader("Set-Cookie") should not equal (null)
          }
        }
      }
      "is fail(wrong password)" in {
        val params = Map("id" -> "test", "password" -> "bar")
        post("/api/signin", params) {
          status should equal (200)
          assert(body == """{"status":"NG","data":{}}""")
          response.getHeader("Set-Cookie") should equal (null)
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
      "is login user data" in {
        session {
          val params = Map("id" -> "test", "password" -> "foo")
          post("/api/signin", params) { status should equal (200) }
          get("/api/datasets") {
            status should equal (200)
            val result = parse(body).extract[AjaxResponse[Datasets]]
            result.data.results shouldNot(be(empty))
            val datasetResult = result.data.results(0)
            datasetResult.name should equal ("user")
          }
          post("/api/signout", params) { status should equal (200) }
        }
      }
      "is guest data" in {
        session {
          get("/api/datasets") {
            status should equal (200)
            val result = parse(body).extract[AjaxResponse[Datasets]]
            result.data.results shouldNot(be(empty))
            val datasetResult = result.data.results(0)
            datasetResult.name should equal ("guest")
          }
        }
      }
    }

    "dataset" - {
      "can get" in {
        get("/api/datasets/1") {
          status should equal (200)
          val result = parse(body).extract[AjaxResponse[Dataset]]
          result.status should equal ("OK")
          result.data.id should equal ("1")
          result.data.permission should equal (1)
        }
      }
      "is login user data" in {
        session {
          val params = Map("id" -> "test", "password" -> "foo")
          post("/api/signin", params) { status should equal (200) }
          get("/api/datasets/1") {
            status should equal (200)
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.primaryImage should equal ("primaryImage user")
          }
          post("/api/signout", params) { status should equal (200) }
        }
      }
      "is guest data" in {
        session {
          get("/api/datasets/1") {
            status should equal (200)
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.primaryImage should equal ("primaryImage guest")
          }
        }
      }
    }
  }

  "Authentication" - {
    "sign-in user" - {
      "profile is not guest" in {
        session {
          val params = Map("id" -> "test", "password" -> "foo")
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
          val params = Map("id" -> "test", "password" -> "foo")
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
