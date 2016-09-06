package dsmoq.maintenance.controllers

import org.scalatra.Ok
import org.scalatra.ScalatraServlet
import org.scalatra.SeeOther
import org.scalatra.scalate.ScalateSupport
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.maintenance.AppConfig
import dsmoq.maintenance.controllers.ResponseUtil.resultAs
import dsmoq.maintenance.services.ApiKeyService

/**
 * APIキー処理系画面のサーブレット
 */
class ApiKeyServlet extends ScalatraServlet with ScalateSupport with LazyLogging {
  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("MAINTENANCE_APIKEY_LOG")

  before() {
    contentType = "text/html"
  }

  get("/") {
    Ok(list())
  }

  post("/proc") {
    val id = params.get("id")
    val result = for {
      _ <- ApiKeyService.disable(id)
    } yield {
      SeeOther(url("/"))
    }
    resultAs(result) { error =>
      list(Some(error))
    }
  }

  /**
   * 一覧画面を作成する。
   *
   * @param error エラー文言
   * @return 一覧画面のHTML
   */
  def list(error: Option[String] = None): String = {
    val keys = ApiKeyService.list()
    ssp(
      "apikey/index",
      "error" -> error,
      "keys" -> keys
    )
  }

  get("/add") {
    ssp("apikey/add", "error" -> None)
  }

  post("/add/proc") {
    val userName = params.get("name")
    val result = for {
      _ <- ApiKeyService.add(userName)
    } yield {
      SeeOther(url("/"))
    }
    resultAs(result) { error =>
      ssp("apikey/add", "error" -> Some(error))
    }
  }
}
