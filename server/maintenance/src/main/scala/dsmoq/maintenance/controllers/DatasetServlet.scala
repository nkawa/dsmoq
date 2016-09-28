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
import dsmoq.maintenance.services.DatasetService
import dsmoq.maintenance.services.ErrorDetail

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
      Forbidden(errorPage(message))
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
    resultAs(result) { (error, details) =>
      errorPage(error, details)
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
    resultAs(result) { (error, details) =>
      errorPage(error, details)
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
    resultAs(result) { (error, details) =>
      errorPage(error, details)
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
    resultAs(result) { (error, details) =>
      errorPage(error, details)
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
    resultAs(result) { (error, details) =>
      errorPage(error, details)
    }
  }

  post("/apply") {
    val result = for {
      _ <- DatasetService.applyChange(params, multiParams)
    } yield {
      SeeOther(searchUrl(params - "page"))
    }
    resultAs(result) { (error, details) =>
      errorPage(error, details)
    }
  }

  post("/acl/update/user/apply") {
    val result = for {
      _ <- DatasetService.applyChangeForAclUpdateUser(params)
    } yield {
      SeeOther(aclListUrl(params))
    }
    resultAs(result) { (error, details) =>
      errorPage(error, details)
    }
  }

  post("/acl/update/group/apply") {
    val result = for {
      _ <- DatasetService.applyChangeForAclUpdateGroup(params)
    } yield {
      SeeOther(aclListUrl(params))
    }
    resultAs(result) { (error, details) =>
      errorPage(error, details)
    }
  }

  post("/acl/add/user/apply") {
    val result = for {
      _ <- DatasetService.applyChangeForAclAddUser(params)
    } yield {
      SeeOther(aclListUrl(params))
    }
    resultAs(result) { (error, details) =>
      errorPage(error, details)
    }
  }

  post("/acl/add/group/apply") {
    val result = for {
      _ <- DatasetService.applyChangeForAclAddGroup(params)
    } yield {
      SeeOther(aclListUrl(params))
    }
    resultAs(result) { (error, details) =>
      errorPage(error, details)
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
   * エラーページを作成する。
   *
   * @param error エラーメッセージ
   * @param details エラーの詳細
   * @return エラーページのHTML
   */
  def errorPage(error: String, details: Seq[ErrorDetail] = Seq.empty): String = {
    val backUrl = Option(request.getHeader("Referer")).getOrElse("/")
    ssp(
      "util/error",
      "error" -> error,
      "details" -> details,
      "backUrl" -> backUrl
    )
  }

  /**
   * 検索画面のURLを作成する。
   *
   * @param params クエリパラメータ
   * @return 検索画面のURL
   */
  def searchUrl(params: Map[String, String]): String = {
    val condition = SearchCondition.fromMap(params)
    url("/", condition.toMap)
  }

  /**
   * アクセス権一覧画面のURLを作成する。
   *
   * @param params クエリパラメータ
   * @return アクセス権一覧画面のURL
   */
  def aclListUrl(params: Map[String, String]): String = {
    val condition = SearchCondition.fromMap(params)
    val param = SearchAclsParameter.fromMap(params)
    url("/acl", param.toMap ++ condition.toMap)
  }
}
