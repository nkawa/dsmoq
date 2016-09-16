package dsmoq.services

import java.util.ResourceBundle
import java.util.UUID

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.joda.time.DateTime
import org.json4s.DefaultFormats
import org.json4s.Formats
import org.json4s.jackson.JsonMethods
import org.json4s.jvalue2extractable
import org.json4s.string2JsonInput

import dsmoq.ResourceNames
import dsmoq.exceptions.NotFoundException
import dsmoq.persistence
import dsmoq.persistence.PostgresqlHelper.PgConditionSQLBuilder
import dsmoq.persistence.PostgresqlHelper.PgSQLSyntaxType
import dsmoq.services.json.DatasetQuery
import dsmoq.services.json.SearchDatasetCondition
import dsmoq.services.json.SearchDatasetConditionSerializer
import scalikejdbc.ConditionSQLBuilder
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SelectSQLBuilder
import scalikejdbc.delete
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef
import scalikejdbc.scalikejdbcSQLSyntaxToStringImplicitDef
import scalikejdbc.select
import scalikejdbc.sqls
import scalikejdbc.withSQL

/**
 * カスタムクエリの処理を取り扱うサービスクラス
 * @param resource ResourceBundleのインスタンス
 */
class QueryService(resource: ResourceBundle) {
  protected implicit val jsonFormats: Formats = DefaultFormats + SearchDatasetConditionSerializer

  def getDatasetQueries(user: User): Try[Seq[DatasetQuery]] = {
    if (user.isGuest) {
      return Success(Seq.empty)
    }
    Try {
      DB.readOnly { implicit s =>
        val q = persistence.CustomQuery.q
        withSQL {
          select(q.result.*)
            .from(persistence.CustomQuery as q)
            .where
            .eqUuid(q.userId, user.id)
            .orderBy(q.createdAt.desc)
        }.map { rs =>
          val query = persistence.CustomQuery(q.resultName)(rs)
          val condition = JsonMethods.parse(query.query).extract[SearchDatasetCondition]
          DatasetQuery(
            id = query.id,
            name = query.name,
            query = condition
          )
        }.list.apply()
      }
    }
  }
  def createDatasetQuery(name: String, condition: SearchDatasetCondition, user: User): Try[DatasetQuery] = {
    Try {
      val conditionStr = JsonMethods.compact(JsonMethods.render(SearchDatasetCondition.toJson(condition)))
      DB.localTx { implicit s =>
        val timestamp = DateTime.now()
        val id = UUID.randomUUID.toString
        val query = persistence.CustomQuery.create(
          id = id,
          userId = user.id,
          name = name,
          query = conditionStr,
          createdBy = user.id,
          createdAt = timestamp
        )
        DatasetQuery(
          id = query.id,
          name = query.name,
          query = condition
        )
      }
    }
  }
  def getDatasetQuery(id: String, user: User): Try[DatasetQuery] = {
    Try {
      DB.readOnly { implicit s =>
        persistence.CustomQuery.find(id).filter(_.userId == user.id).map { query =>
          val condition = JsonMethods.parse(query.query).extract[SearchDatasetCondition]
          DatasetQuery(
            id = query.id,
            name = query.name,
            query = condition
          )
        }
      }
    }.flatMap {
      case Some(x) => Success(x)
      case None => Failure(new NotFoundException)
    }
  }

  def deleteDatasetQuery(id: String, user: User): Try[Unit] = {
    Try {
      DB.localTx { implicit s =>
        val q = persistence.CustomQuery.q
        withSQL {
          delete.from(persistence.CustomQuery as q)
            .where
            .eqUuid(q.id, id)
            .and
            .eqUuid(q.userId, user.id)
        }.update.apply()
      }
    }.flatMap {
      case 0 => Failure(new NotFoundException)
      case _ => Success(())
    }
  }
}
