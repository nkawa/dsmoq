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

  /**
   * 指定したユーザが持つ、データセット検索のカスタムクエリを取得する。
   *
   * @param user ユーザ情報
   * @return ユーザが持つカスタムクエリ
   */
  def getDatasetQueries(user: User): Try[Seq[DatasetQuery]] = {
    CheckUtil.checkNull(user, "user")
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

  /**
   * データセット検索のカスタムクエリを作成する。
   *
   * @param name カスタムクエリの名前
   * @param condition データセット検索条件
   * @param user ユーザ情報
   * @return 作成されたカスタムクエリ
   */
  def createDatasetQuery(name: String, condition: SearchDatasetCondition, user: User): Try[DatasetQuery] = {
    CheckUtil.checkNull(name, "name")
    CheckUtil.checkNull(condition, "condition")
    CheckUtil.checkNull(user, "user")
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

  /**
   * データセット検索のカスタムクエリを取得する。
   *
   * @param id カスタムクエリID
   * @param user ユーザ情報
   * @return カスタムクエリ
   */
  def getDatasetQuery(id: String, user: User): Try[DatasetQuery] = {
    CheckUtil.checkNull(id, "id")
    CheckUtil.checkNull(user, "user")
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

  /**
   * データセット検索のカスタムクエリを削除する。
   *
   * @param id カスタムクエリID
   * @param user ユーザ情報
   * @return 処理結果
   */
  def deleteDatasetQuery(id: String, user: User): Try[Unit] = {
    CheckUtil.checkNull(id, "id")
    CheckUtil.checkNull(user, "user")
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
