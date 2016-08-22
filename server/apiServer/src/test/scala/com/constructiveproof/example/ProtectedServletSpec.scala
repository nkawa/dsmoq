package com.constructiveproof.example

import org.scalatra.test.specs2._
import dsmoq.controllers.ResourceController

// For more on Specs2, see http://etorreborre.github.com/specs2/guide/org.specs2.guide.QuickStart.html
class ProtectedServletSpec extends ScalatraSpec {
  def is =
    "GET / on ProtectedServlet" ^
      "should return status 200" ! root200 ^
      end

  addServlet(classOf[ResourceController], "/*")

  def root200 = get("/") {
    status must_== 200
  }
}
