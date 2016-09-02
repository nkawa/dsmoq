package dsmoq.maintenance.controllers

import scala.util.Try

import org.scalatra.ScalatraServlet
import org.scalatra.scalate.ScalateSupport
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.maintenance.AppConfig
import dsmoq.maintenance.views.{ user => view }
import dsmoq.maintenance.services.UserService

class UserServlet extends ScalatraServlet with ScalateSupport with LazyLogging {
  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("MAINTENANCE_USER_LOG")

  before() {
    contentType = "text/html"
  }

  get("/") {
    search()
  }

  post("/proc") {
    // TODO: proc

    search()
  }

  def search() = {
    val condition = view.SearchCondition(
      userType = view.SearchCondition.UserType(params.get("userType")),
      query = params.getOrElse("query", ""),
      offset = toInt(params.get("offset"), 0),
      limit = toInt(params.get("limit"), AppConfig.searchLimit)
    )
    val result = UserService.search(condition)
    ssp(
      "user/index",
      "condition" -> condition,
      "result" -> result
    )
  }

  def toInt(str: Option[String], default: Int): Int = {
    str.flatMap(s => Try(s.toInt).toOption).getOrElse(default)
  }
}
