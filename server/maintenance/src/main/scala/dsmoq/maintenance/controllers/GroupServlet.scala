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
import dsmoq.maintenance.data.group.SearchMemberParameter
import dsmoq.maintenance.data.group.SearchMembersParameter
import dsmoq.maintenance.data.group.SearchCondition
import dsmoq.maintenance.data.group.UpdateParameter
import dsmoq.maintenance.data.group.AddMemberParameter
import dsmoq.maintenance.data.group.UpdateMemberParameter
import dsmoq.maintenance.services.GroupService
import dsmoq.maintenance.services.ErrorDetail

/**
 * グループ処理系画面のサーブレット
 */
class GroupServlet extends ScalatraServlet with ScalateSupport with LazyLogging with CsrfTokenSupport {
  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("MAINTENANCE_GROUP_LOG")

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

  get("/member") {
    val condition = SearchCondition.fromMap(params)
    val result = for {
      data <- GroupService.getMemberListData(SearchMembersParameter.fromMap(params))
    } yield {
      Ok(
        ssp(
          "group/member/index",
          "condition" -> condition,
          "data" -> data
        )
      )
    }
    resultAs(result) {
      case (error, details) =>
        errorPage(error, details)
    }
  }

  get("/member/update") {
    val condition = SearchCondition.fromMap(params)
    val result = for {
      data <- GroupService.getMemberUpdateData(SearchMemberParameter.fromMap(params))
    } yield {
      Ok(
        ssp(
          "group/member/update",
          "condition" -> condition,
          "data" -> data,
          "csrfKey" -> csrfKey,
          "csrfToken" -> csrfToken
        )
      )
    }
    resultAs(result) {
      case (error, details) =>
        errorPage(error, details)
    }
  }

  get("/member/add") {
    val condition = SearchCondition.fromMap(params)
    val result = for {
      data <- GroupService.getMemberAddData(SearchMembersParameter.fromMap(params))
    } yield {
      Ok(
        ssp(
          "group/member/add",
          "condition" -> condition,
          "data" -> data,
          "csrfKey" -> csrfKey,
          "csrfToken" -> csrfToken
        )
      )
    }
    resultAs(result) {
      case (error, details) =>
        errorPage(error, details)
    }
  }

  post("/apply") {
    val result = for {
      _ <- GroupService.applyChange(params, multiParams)
    } yield {
      SeeOther(searchUrl(params - "page"))
    }
    resultAs(result) {
      case (error, details) =>
        errorPage(error, details)
    }
  }

  post("/member/update/apply") {
    val result = for {
      _ <- GroupService.applyChangeForMemberUpdate(params)
    } yield {
      SeeOther(memberListUrl(params))
    }
    resultAs(result) {
      case (error, details) =>
        errorPage(error, details)
    }
  }

  post("/member/add/apply") {
    val result = for {
      _ <- GroupService.applyChangeForMemberAdd(params)
    } yield {
      SeeOther(memberListUrl(params))
    }
    resultAs(result) {
      case (error, details) =>
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
    val result = GroupService.search(condition)
    ssp(
      "group/index",
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
   * メンバー一覧画面のURLを作成する。
   *
   * @param params クエリパラメータ
   * @return メンバー一覧画面のURL
   */
  def memberListUrl(params: Map[String, String]): String = {
    val condition = SearchCondition.fromMap(params)
    val param = SearchMembersParameter.fromMap(params)
    url("/member", param.toMap ++ condition.toMap)
  }
}
