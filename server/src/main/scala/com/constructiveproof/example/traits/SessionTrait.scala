package com.constructiveproof.example.traits

import org.scalatra.ScalatraServlet

trait SessionTrait extends ScalatraServlet {
  def getSessionParameter(key: String) = {
    sessionOption match {
      case Some(_) => Option(session.getAttribute(key))
      case None => None
    }
  }
}
