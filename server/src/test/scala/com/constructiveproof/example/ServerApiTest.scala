package com.constructiveproof.example

import _root_.com.constructiveproof.example.facade.{Profile, AjaxResponse, User }
import org.scalatest.FreeSpec
import org.scalatra.test.scalatest._

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.junit.Ignore

class ServerApiTest extends FreeSpec with ScalatraSuite {
  protected implicit val jsonFormats: Formats = DefaultFormats

  addServlet(classOf[ApiController], "/*")

  "API test" - {
    "login" - {
      "signin" in {
        get("/profile") {
          status should equal (200)
          val result = parse(body).extract[AjaxResponse[Profile]]
          assert(result === AjaxResponse("OK", Profile(Some(User(
            "id", "name", "fullname", "organization", "title", "http://xxxx", false
          )))))
        }
      }

      "guest" ignore {
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
}
