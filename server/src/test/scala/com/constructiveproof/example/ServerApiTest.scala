package com.constructiveproof.example

import _root_.com.constructiveproof.example.facade.{Profile, AjaxResponse, User }
import org.scalatest.FreeSpec
import org.scalatra.test.scalatest._

import org.json4s._
import org.json4s.jackson.JsonMethods._

class ServerApiTest extends FreeSpec with ScalatraSuite {
  protected implicit val jsonFormats: Formats = DefaultFormats

  addServlet(classOf[ResourceController], "/*")

  "API test" - {
    "profile" - {
      "is signin user" in {
        get("/profile") {
          status should equal (200)
          val result = parse(body).extract[AjaxResponse[Profile]]
          assert(result === AjaxResponse("OK", Profile(Some(User(
            "id", "name", "fullname", "organization", "title", "http://xxxx", false
          )))))
        }
      }

      "is guest" ignore {
        get("/profile") {
          status should equal (200)
          val result = parse(body).extract[AjaxResponse[Profile]]
          assert(result === AjaxResponse("OK",Profile(Some(User(
            "id", "name", "fullname", "organization", "title", "http://xxxx", true
          )))))
        }
      }
    }
  }

  "Authentication" - {
    "signin" - {
      "is success" in {
        val params = Map("id" -> "1", "password" -> "hoge")
        post("/signin", params) {
          status should equal (302)
        }
      }
    }
    "signout" - {
      "is success" in {
        post("/signout") {
          status should equal (302)
        }
      }
    }
  }
}
