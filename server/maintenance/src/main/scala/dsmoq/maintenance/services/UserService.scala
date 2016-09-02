package dsmoq.maintenance.services

import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.maintenance.views.user.SearchCondition
import dsmoq.maintenance.views.user.SearchCondition.UserType
import dsmoq.maintenance.views.user.SearchResult
import dsmoq.maintenance.views.user.User
import dsmoq.persistence
import dsmoq.persistence.PostgresqlHelper.PgConditionSQLBuilder
import dsmoq.persistence.PostgresqlHelper.PgSQLSyntaxType
import scalikejdbc.ConditionSQLBuilder
import scalikejdbc.DB
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

  def search(condition: SearchCondition): SearchResult = {
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
        User(
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
}
