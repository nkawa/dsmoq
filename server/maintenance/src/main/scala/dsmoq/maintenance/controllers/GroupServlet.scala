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
      Forbidden(ssp("util/error", "error" -> message))
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
    resultAs(result) { error =>
      ssp("util/error", "error" -> error)
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
    resultAs(result) { error =>
      ssp("util/error", "error" -> error)
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
    resultAs(result) { error =>
      ssp("util/error", "error" -> error)
    }
  }

  post("/apply") {
    val condition = SearchCondition.fromMap(params)
    val param = UpdateParameter.fromMap(multiParams)
    val result = for {
      _ <- params.get("update") match {
        case Some("logical_delete") => GroupService.applyLogicalDelete(param)
        case Some("rollback_logical_delete") => GroupService.applyRollbackLogicalDelete(param)
        case Some("physical_delete") => GroupService.applyPhysicalDelete(param)
        case _ => Success(())
      }
    } yield {
      SeeOther(searchUrl(condition.toMap))
    }
    resultAs(result) { error =>
      ssp("util/error", "error" -> error)
    }
  }

  post("/member/update/apply") {
    val condition = SearchCondition.fromMap(params)
    val param = UpdateMemberParameter.fromMap(params)
    val result = for {
      _ <- params.get("update") match {
        case Some("update") => GroupService.applyUpdateMember(param)
        case Some("delete") => GroupService.applyDeleteMember(param)
        case _ => Success(())
      }
    } yield {
      SeeOther(memberListUrl(param.groupId, condition.toMap))
    }
    resultAs(result) { error =>
      ssp("util/error", "error" -> error)
    }
  }

  post("/member/add/apply") {
    val condition = SearchCondition.fromMap(params)
    val param = AddMemberParameter.fromMap(params)
    val result = for {
      _ <- params.get("update") match {
        case Some("add") => GroupService.applyAddMember(param)
        case _ => Success(())
      }
    } yield {
      SeeOther(memberListUrl(param.groupId, condition.toMap))
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
   * 検索画面のURLを作成する。
   *
   * @param params クエリパラメータ
   * @return 検索画面のURL
   */
  def searchUrl(params: Map[String, String]): String = {
    url("/", params)
  }

  /**
   * メンバー一覧画面のURLを作成する。
   *
   * @param groupId グループID
   * @param params クエリパラメータ
   * @return メンバー一覧画面のURL
   */
  def memberListUrl(groupId: Option[String], params: Map[String, String]): String = {
    val fullParams = groupId match {
      case Some(groupId) => params + ("groupId" -> groupId)
      case None => params
    }
    url("/member", fullParams)
  }
}
