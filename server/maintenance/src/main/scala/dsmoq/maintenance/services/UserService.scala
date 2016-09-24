package dsmoq.maintenance.services

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.joda.time.DateTime
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.maintenance.AppConfig
import dsmoq.maintenance.data.SearchResult
import dsmoq.maintenance.data.user.SearchCondition
import dsmoq.maintenance.data.user.SearchCondition.UserType
import dsmoq.maintenance.data.user.SearchResultUser
import dsmoq.maintenance.data.user.UpdateParameter
import dsmoq.persistence
import dsmoq.persistence.PostgresqlHelper.PgConditionSQLBuilder
import dsmoq.persistence.PostgresqlHelper.PgSQLSyntaxType
import scalikejdbc.ConditionSQLBuilder
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SelectSQLBuilder
import scalikejdbc.SQLSyntax
import scalikejdbc.interpolation.Implicits.scalikejdbcSQLInterpolationImplicitDef
import scalikejdbc.interpolation.Implicits.scalikejdbcSQLSyntaxToStringImplicitDef
import scalikejdbc.select
import scalikejdbc.sqls
import scalikejdbc.update
import scalikejdbc.withSQL

/**
 * ユーザ処理サービス
 */
object UserService extends LazyLogging {
  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("MAINTENANCE_USER_LOG")

  /**
   * サービス名
   */
  val SERVICE_NAME = "UserService"

  /**
   * ユーザを検索する。
   *
   * @param condition 検索条件
   * @return 検索結果
   */
  def search(condition: SearchCondition): SearchResult[SearchResultUser] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "search", condition))
    val u = persistence.User.u
    val ma = persistence.MailAddress.ma
    def createSqlBase(select: SelectSQLBuilder[Unit]): ConditionSQLBuilder[Unit] = {
      select
        .from(persistence.User as u)
        .innerJoin(persistence.MailAddress as ma).on(u.id, ma.userId)
        .where(
          sqls.toAndConditionOpt(
            condition.userType match {
              case UserType.Enabled => Some(sqls.eq(u.disabled, false))
              case UserType.Disabled => Some(sqls.eq(u.disabled, true))
              case _ => None
            },
            if (condition.query.isEmpty) {
              None
            } else {
              sqls.toOrConditionOpt(
                Some(upperLikeQuery(u.name, condition.query)),
                Some(upperLikeQuery(u.fullname, condition.query)),
                Some(upperLikeQuery(u.organization, condition.query))
              )
            }
          )
        )
    }
    val limit = AppConfig.searchLimit
    val offset = (condition.page - 1) * limit
    DB.readOnly { implicit s =>
      val total = withSQL {
        createSqlBase(select(sqls.count))
      }.map(_.int(1)).single.apply().getOrElse(0)
      val records = withSQL {
        createSqlBase(select(u.result.*, ma.result.address))
          .orderBy(u.createdAt)
          .offset(offset)
          .limit(limit)
      }.map { rs =>
        val user = persistence.User(u.resultName)(rs)
        val address = rs.string(ma.resultName.address)
        SearchResultUser(
          id = user.id,
          name = user.name,
          fullname = user.fullname,
          mailAddress = address,
          organization = user.organization,
          title = user.title,
          description = user.description,
          createdAt = user.createdAt,
          updatedAt = user.updatedAt,
          disabled = user.disabled
        )
      }.list.apply()
      SearchResult(
        offset,
        limit,
        total,
        records
      )
    }
  }

  /**
   * ユーザを検索する。
   *
   * @param condition 検索条件
   * @return 検索結果
   */
  def upperLikeQuery(column: SQLSyntax, value: String): SQLSyntax = {
    sqls.upperLikeQuery(column, value)
  }

  /**
   * ユーザの無効化状態を更新する。
   *
   * @param param 入力パラメータ
   * @return 処理結果、存在しないIDが含まれていた場合 Failure(ServiceException)
   */
  def updateDisabled(param: UpdateParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "updateDisabled", param))
    val result = DB.localTx { implicit s =>
      for {
        _ <- checkChange(param.originals, param.updates)
        _ <- execUpdateDisabled(param.originals, param.updates)
      } yield {
        ()
      }
    }
    Util.withErrorLogging(logger, LOG_MARKER, result)
  }

  /**
   * ユーザーの無効化状態の変更があるかを確認する。
   *
   * @param originals 無効であったユーザ
   * @param updates 無効にするユーザ
   * @return 処理結果、無効化状態に変更がない場合 Failure(ServiceException)
   */
  def checkChange(originals: Seq[String], updates: Seq[String]): Try[Unit] = {
    val os = originals.toSet
    val us = updates.toSet
    val enabledTargets = os -- us
    val disabledTargets = us -- os
    if (enabledTargets.isEmpty && disabledTargets.isEmpty) {
      Failure(new ServiceException("ユーザーの無効化状態の変更がありません。"))
    } else {
      Success(())
    }
  }

  /**
   * ユーザの無効化状態を更新する。
   *
   * @param originals 無効であったユーザ
   * @param updates 無効にするユーザ
   * @param s DBセッション
   * @return 処理結果
   */
  def execUpdateDisabled(originals: Seq[String], updates: Seq[String])(implicit s: DBSession): Try[Unit] = {
    Try {
      val timestamp = DateTime.now()
      val systemUserId = AppConfig.systemUserId
      val os = originals.toSet
      val us = updates.toSet
      val enabledTargets = os -- us
      val disabledTargets = us -- os
      val u = persistence.User.column
      withSQL {
        update(persistence.User)
          .set(
            u.disabled -> false,
            u.updatedAt -> timestamp,
            u.updatedBy -> sqls.uuid(systemUserId)
          )
          .where
          .in(u.id, enabledTargets.toSeq.map(sqls.uuid))
      }.update.apply()
      withSQL {
        update(persistence.User)
          .set(
            u.disabled -> true,
            u.updatedAt -> timestamp,
            u.updatedBy -> sqls.uuid(systemUserId)
          )
          .where
          .in(u.id, disabledTargets.toSeq.map(sqls.uuid))
      }.update.apply()
    }
  }
}
