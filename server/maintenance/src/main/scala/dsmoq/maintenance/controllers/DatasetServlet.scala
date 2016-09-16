package dsmoq.maintenance.controllers

import scala.util.Success

import org.scalatra.CsrfTokenSupport
import org.scalatra.Forbidden
import org.scalatra.Ok
import org.scalatra.ScalatraServlet
import org.scalatra.SeeOther
import org.scalatra.scalate.ScalateSupport
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.maintenance.AppConfig
import dsmoq.maintenance.controllers.ResponseUtil.resultAs
import dsmoq.maintenance.data.dataset.SearchCondition
import dsmoq.maintenance.services.DatasetService

/**
 * データセット処理系画面のサーブレット
 */
class DatasetServlet extends ScalatraServlet with ScalateSupport with LazyLogging with CsrfTokenSupport {
  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("MAINTENANCE_DATASET_LOG")

  before() {
    contentType = "text/html"
  }

  /**
   * CSRFが検出された場合のActionを定義する。
   */
  override def handleForgery() {
    contentType = "text/html"
    val message = "無効なトークンが指定されました。"
    logger.error(LOG_MARKER, message)
    halt(
      Forbidden(ssp("util/error", "error" -> message))
    )
  }

  get("/") {
    val condition = SearchCondition.fromMap(params)
    Ok(search(condition))
  }

  get("/acl") {
    val condition = SearchCondition.fromMap(params)
    val datasetId = params.get("datasetId")
    val result = for {
      data <- DatasetService.getAclListData(datasetId)
    } yield {
      Ok(
        ssp(
          "dataset/acl/index",
          "condition" -> condition,
          "result" -> data
        )
      )
    }
    resultAs(result) { error =>
      ssp("util/error", "error" -> error)
    }
  }

  get("/acl/update/user") {
    val condition = SearchCondition.fromMap(params)
    val datasetId = params.get("datasetId")
    val userId = params.get("userId")
    val result = for {
      data <- DatasetService.getAclUpdateDataForUser(datasetId, userId)
    } yield {
      Ok(
        ssp(
          "dataset/acl/update",
          "condition" -> condition,
          "data" -> data
        )
      )
    }
    resultAs(result) { error =>
      ssp("util/error", "error" -> error)
    }
  }

  get("/acl/update/group") {
    val condition = SearchCondition.fromMap(params)
    val datasetId = params.get("datasetId")
    val groupId = params.get("groupId")
  }

  get("/acl/add/user") {
    val condition = SearchCondition.fromMap(params)
    val datasetId = params.get("datasetId")
  }

  get("/acl/add/group") {
    val condition = SearchCondition.fromMap(params)
    val datasetId = params.get("datasetId")
  }

  post("/proc") {
    val targets = multiParams("checked")
    val condition = SearchCondition.fromMap(params)
    val result = for {
      _ <- params.get("update") match {
        case Some("logical_delete") => DatasetService.applyLogicalDelete(targets)
        case Some("rollback_logical_delete") => DatasetService.applyRollbackLogicalDelete(targets)
        case Some("physical_delete") => DatasetService.applyPhysicalDelete(targets)
        case _ => Success(())
      }
    } yield {
      SeeOther(searchUrl(condition.toMap))
    }
    resultAs(result) { error =>
      ssp("util/error", "error" -> error)
    }
  }

  /**
   * 検索画面を作成する。
   *
   * @param condition 検索条件
   * @param error エラー文言
   * @return 検索画面のHTML
   */
  def search(condition: SearchCondition): String = {
    val result = DatasetService.search(condition)
    ssp(
      "dataset/index",
      "condition" -> condition,
      "result" -> result,
      "url" -> searchUrl _,
      "csrfKey" -> csrfKey,
      "csrfToken" -> csrfToken,
      "limit" -> AppConfig.searchLimit
    )
  }

  /**
   * 検索画面のURLを作成する。
   *
   * @param params クエリパラメータ
   * @return 検索画面のURL
   */
  def searchUrl(params: Map[String, String]): String = {
    url("/", params)
  }
}
