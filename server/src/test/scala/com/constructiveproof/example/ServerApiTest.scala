package com.constructiveproof.example

import _root_.com.constructiveproof.example.facade.{LoginInfo, AjaxResponse, User }
import org.scalatest.FreeSpec
import org.scalatra.test.scalatest._

import org.json4s._
import org.json4s.jackson.JsonMethods._

class ServerApiTest extends FreeSpec with ScalatraSuite {
  protected implicit val jsonFormats: Formats = DefaultFormats

  addServlet(classOf[ApiController], "/api/*")

  "API test" - {
    "login" - {
      "simple" in {
        get("/api/login") {
          status should equal (200)
          val result = parse(body).extract[AjaxResponse[LoginInfo]]
          assert(result === AjaxResponse("OK", LoginInfo(Some(User("test01", "Test User 01")))))
        }
      }
    }
  }
}
