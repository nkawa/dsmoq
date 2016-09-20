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
import dsmoq.maintenance.data.dataset.OwnerType
import dsmoq.maintenance.data.dataset.SearchAclGroupParameter
import dsmoq.maintenance.data.dataset.SearchAclsParameter
import dsmoq.maintenance.data.dataset.SearchAclUserParameter
import dsmoq.maintenance.data.dataset.SearchCondition
import dsmoq.maintenance.data.dataset.UpdateParameter
import dsmoq.maintenance.data.dataset.AddAclGroupParameter
import dsmoq.maintenance.data.dataset.AddAclUserParameter
import dsmoq.maintenance.data.dataset.UpdateAclGroupParameter
import dsmoq.maintenance.data.dataset.UpdateAclUserParameter
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
    val result = for {
      data <- DatasetService.getAclListData(SearchAclsParameter.fromMap(params))
    } yield {
      Ok(
        ssp(
          "dataset/acl/index",
          "condition" -> condition,
          "data" -> data
        )
      )
    }
    resultAs(result) { error =>
      ssp("util/error", "error" -> error)
    }
  }

  get("/acl/update/user") {
    val condition = SearchCondition.fromMap(params)
    val result = for {
      data <- DatasetService.getAclUpdateDataForUser(SearchAclUserParameter.fromMap(params))
    } yield {
      Ok(
        ssp(
          "dataset/acl/update",
          "condition" -> condition,
          "data" -> data,
          "csrfKey" -> csrfKey,
          "csrfToken" -> csrfToken
        )
      )
    }
    resultAs(result) { error =>
      ssp("util/error", "error" -> error)
    }
  }

  get("/acl/update/group") {
    val condition = SearchCondition.fromMap(params)
    val result = for {
      data <- DatasetService.getAclUpdateDataForGroup(SearchAclGroupParameter.fromMap(params))
    } yield {
      Ok(
        ssp(
          "dataset/acl/update",
          "condition" -> condition,
          "data" -> data,
          "csrfKey" -> csrfKey,
          "csrfToken" -> csrfToken
        )
      )
    }
    resultAs(result) { error =>
      ssp("util/error", "error" -> error)
    }
  }

  get("/acl/add/user") {
    val condition = SearchCondition.fromMap(params)
    val result = for {
      data <- DatasetService.getAclAddData(SearchAclsParameter.fromMap(params))
    } yield {
      Ok(
        ssp(
          "dataset/acl/add",
          "condition" -> condition,
          "data" -> data,
          "ownerType" -> OwnerType.User,
          "csrfKey" -> csrfKey,
          "csrfToken" -> csrfToken
        )
      )
    }
    resultAs(result) { error =>
      ssp("util/error", "error" -> error)
    }
  }

  get("/acl/add/group") {
    val condition = SearchCondition.fromMap(params)
    val result = for {
      data <- DatasetService.getAclAddData(SearchAclsParameter.fromMap(params))
    } yield {
      Ok(
        ssp(
          "dataset/acl/add",
          "condition" -> condition,
          "data" -> data,
          "ownerType" -> OwnerType.Group,
          "csrfKey" -> csrfKey,
          "csrfToken" -> csrfToken
        )
      )
    }
    resultAs(result) { error =>
      ssp("util/error", "error" -> error)
    }
  }

  post("/apply") {
    val condition = SearchCondition.fromMap(params)
    val param = UpdateParameter.fromMap(multiParams)
    val result = for {
      _ <- params.get("update") match {
        case Some("logical_delete") => DatasetService.applyLogicalDelete(param)
        case Some("rollback_logical_delete") => DatasetService.applyRollbackLogicalDelete(param)
        case Some("physical_delete") => DatasetService.applyPhysicalDelete(param)
        case _ => Success(())
      }
    } yield {
      SeeOther(searchUrl(condition.toMap))
    }
    resultAs(result) { error =>
      ssp("util/error", "error" -> error)
    }
  }

  post("/acl/update/user/apply") {
    val condition = SearchCondition.fromMap(params)
    val param = UpdateAclUserParameter.fromMap(params)
    val result = for {
      _ <- params.get("update") match {
        case Some("update") => DatasetService.applyUpdateAclUser(param)
        case Some("delete") => DatasetService.applyDeleteAclUser(param)
        case _ => Success(())
      }
    } yield {
      SeeOther(aclListUrl(param.datasetId, condition.toMap))
    }
    resultAs(result) { error =>
      ssp("util/error", "error" -> error)
    }
  }

  post("/acl/update/group/apply") {
    val condition = SearchCondition.fromMap(params)
    val param = UpdateAclGroupParameter.fromMap(params)
    val result = for {
      _ <- params.get("update") match {
        case Some("update") => DatasetService.applyUpdateAclGroup(param)
        case Some("delete") => DatasetService.applyDeleteAclGroup(param)
        case _ => Success(())
      }
    } yield {
      SeeOther(aclListUrl(param.datasetId, condition.toMap))
    }
    resultAs(result) { error =>
      ssp("util/error", "error" -> error)
    }
  }

  post("/acl/add/user/apply") {
    val condition = SearchCondition.fromMap(params)
    val param = AddAclUserParameter.fromMap(params)
    val result = for {
      _ <- params.get("update") match {
        case Some("add") => DatasetService.applyAddAclUser(param)
        case _ => Success(())
      }
    } yield {
      SeeOther(aclListUrl(param.datasetId, condition.toMap))
    }
    resultAs(result) { error =>
      ssp("util/error", "error" -> error)
    }
  }

  post("/acl/add/group/apply") {
    val condition = SearchCondition.fromMap(params)
    val param = AddAclGroupParameter.fromMap(params)
    val result = for {
      _ <- params.get("update") match {
        case Some("add") => DatasetService.applyAddAclGroup(param)
        case _ => Success(())
      }
    } yield {
      SeeOther(aclListUrl(param.datasetId, condition.toMap))
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
      "csrfToken" -> csrfToken
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

  /**
   * アクセス権一覧画面のURLを作成する。
   *
   * @param datasetId データセットID
   * @param params クエリパラメータ
   * @return アクセス権一覧画面のURL
   */
  def aclListUrl(datasetId: Option[String], params: Map[String, String]): String = {
    val fullParams = datasetId match {
      case Some(datasetId) => params + ("datasetId" -> datasetId)
      case None => params
    }
    url("/acl", fullParams)
  }
}
