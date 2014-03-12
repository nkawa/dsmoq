package com.constructiveproof.example.traits

import org.scalatra.ScalatraServlet
import com.constructiveproof.example.facade.User

trait SessionTrait extends ScalatraServlet {
  def getSessionParameter(key: String) = {
    sessionOption match {
      case Some(_) => Option(session.getAttribute(key).asInstanceOf[User])
      case None => None
    }
  }
}

trait SessionUserInfo {
  val userInfo: Option[User]
}