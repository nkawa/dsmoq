package com.constructiveproof.example

import _root_.com.constructiveproof.example.facade.{Profile, User }
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
          val result = parse(body).extract[AjaxResponse[Profile]]
          assert(result === AjaxResponse("OK",Profile(Some(User(
            "id", "name", "fullname", "organization", "title", "http://xxxx", true
          )))))
        }
      }
    }
    "signin" - {
      "is success" in {
        val params = Map("id" -> "foo", "password" -> "foo")
        post("/api/signin", params) {
          status should equal (200)
          println(body)
          assert(body == """{"status":"OK","data":{}}""")
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
  }

  "Authentication" - {
    "sign-in user" - {
      "profile is not guest" in {
        val params = Map("id" -> "foo", "password" -> "foo")
        post("/api/signin", params) {}
        get("/api/profile") {
          status should equal (200)
          val response = parse(body).extract[AjaxResponse[Profile]]
          val isGuest = response.data.user match {
            case Some(x) => x.isGuest
            case None => fail("Illegal Argument")
          }
          assert(!isGuest)
        }
        post("/api/signout") {}
      }
    }
    "sign-out user" - {
      "profile is guest" in {
        val params = Map("id" -> "foo", "password" -> "foo")
        post("/api/signin", params) {}
        post("/api/signout") {}
        get("/api/profile") {
          status should equal (200)
          val result = parse(body).extract[AjaxResponse[Profile]]
          val isGuest = result.data.user match {
            case Some(x) => x.isGuest
            case None => fail("Illegal Argument")
          }
          assert(isGuest)
        }
      }
    }
  }
}