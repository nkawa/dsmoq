package dsmoq.maintenance.services

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.maintenance.data.SearchResult
import dsmoq.maintenance.data.user.SearchCondition
import dsmoq.maintenance.data.user.SearchCondition.UserType
import dsmoq.maintenance.data.user.SearchResultUser
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

object UserService extends LazyLogging {
  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("MAINTENANCE_USER_LOG")

  def search(condition: SearchCondition): SearchResult[SearchResultUser] = {
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
    DB.readOnly { implicit s =>
      val total = withSQL {
        createSqlBase(select(sqls.count))
      }.map(_.int(1)).single.apply().getOrElse(0)
      val eles = withSQL {
        createSqlBase(select(u.result.*, ma.result.address))
          .orderBy(u.createdAt)
          .offset(condition.offset)
          .limit(condition.limit)
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
        from = condition.offset + 1,
        to = condition.offset + eles.length,
        total = total,
        data = eles
      )
    }
  }

  def upperLikeQuery(column: SQLSyntax, value: String) = {
    sqls.upperLikeQuery(column, value)
  }

  def updateDisabled(originals: Seq[String], updates: Seq[String]): Try[Unit] = {
    DB.localTx { implicit s =>
      for {
        _ <- checkUserIds(originals ++ updates)
      } yield {
        val os = originals.toSet
        val us = updates.toSet
        val enabledTargets = os -- us
        val disabledTargets = us -- os
        val u = persistence.User.column
        withSQL {
          update(persistence.User)
            .set(u.disabled -> false)
            .where
            .in(u.id, enabledTargets.toSeq.map(sqls.uuid))
        }.update.apply()
        withSQL {
          update(persistence.User)
            .set(u.disabled -> true)
            .where
            .in(u.id, disabledTargets.toSeq.map(sqls.uuid))
        }.update.apply()
      }
    }
  }

  def checkUserIds(ids: Seq[String])(implicit s: DBSession): Try[Unit] = {
    val u = persistence.User.u
    val checks = ids.map { id =>
      val contains = withSQL {
        select(u.result.id)
          .from(persistence.User as u)
          .where
          .eq(u.id, sqls.uuid(id))
      }.map { rs =>
        rs.string(u.resultName.id)
      }.single.apply().isEmpty
      (id, contains)
    }
    val invalidIds = checks.filter(_._2).map(_._1)
    if (invalidIds.isEmpty) {
      Success(())
    } else {
      Failure(new ServiceException("存在しないIDが指定されました。"))
    }
  }
}
