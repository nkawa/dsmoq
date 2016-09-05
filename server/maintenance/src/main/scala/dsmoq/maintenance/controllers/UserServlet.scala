package dsmoq.maintenance.controllers

import org.scalatra.Ok
import org.scalatra.ScalatraServlet
import org.scalatra.SeeOther
import org.scalatra.scalate.ScalateSupport
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.maintenance.AppConfig
import dsmoq.maintenance.controllers.ResponseUtil.resultAs
import dsmoq.maintenance.data.user.SearchCondition
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
    val condition = SearchCondition.fromMap(params)
    Ok(search(condition))
  }

  post("/proc") {
    val originals = multiParams("disabled.originals")
    val updates = multiParams("disabled.updates")
    val condition = SearchCondition.fromMap(params)
    val result = for {
      _ <- UserService.updateDisabled(originals, updates)
    } yield {
      SeeOther(searchUrl(condition.toMap))
    }
    resultAs(result) { error =>
      search(condition, Some(error))
    }
  }

  def search(condition: SearchCondition, error: Option[String] = None): String = {
    val result = UserService.search(condition)
    ssp(
      "user/index",
      "condition" -> condition,
      "result" -> result,
      "error" -> error,
      "url" -> searchUrl _
    )
  }

  def searchUrl(params: Map[String, String]): String = {
    url("/", params)
  }
}
