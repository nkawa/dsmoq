package dsmoq.maintenance.services

import java.util.UUID

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.apache.commons.codec.digest.DigestUtils
import org.joda.time.DateTime
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.maintenance.AppConfig
import dsmoq.maintenance.data.apikey.SearchResultApiKey
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
 * APIキー処理サービス
 */
object ApiKeyService extends LazyLogging {
  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("MAINTENANCE_APIKEY_LOG")

  /**
   * 登録されているAPIキーの一覧を取得する。
   *
   * @return APIキーの一覧
   */
  def list(): Seq[SearchResultApiKey] = {
    logger.info(LOG_MARKER, Util.createLogMessage("ApiKeyService", "list"))
    val u = persistence.User.u
    val ak = persistence.ApiKey.ak
    DB.readOnly { implicit s =>
      withSQL {
        select(ak.result.*, u.result.*)
          .from(persistence.ApiKey as ak)
          .innerJoin(persistence.User as u).on(u.id, ak.userId)
          .where
          .eq(u.disabled, false)
          .and
          .isNull(ak.deletedAt)
      }.map { rs =>
        val key = persistence.ApiKey(ak.resultName)(rs)
        val user = persistence.User(u.resultName)(rs)
        SearchResultApiKey(
          id = key.id,
          apiKey = key.apiKey,
          secretKey = key.secretKey,
          createdAt = key.createdAt,
          updatedAt = key.updatedAt,
          userId = user.id,
          userName = user.name,
          userDisabled = user.disabled
        )
      }.list.apply()
    }
  }

  /**
   * 指定されたAPIキーを無効化する。
   *
   * @param id APIキーのID
   * @return 処理結果、ID不正時Failure(ServiceException)
   */
  def disable(id: Option[String]): Try[Unit] = {
    logger.info(LOG_MARKER, Util.createLogMessage("ApiKeyService", "disable", Map("id" -> id)))
    id.filter(!_.isEmpty).map { id =>
      DB.localTx { implicit s =>
        for {
          _ <- Util.checkUuid(id)
          _ <- checkId(id)
          _ <- execDisable(id)
        } yield {
          ()
        }
      }
    }.getOrElse {
      Failure(new ServiceException("キーが未選択です。"))
    }
  }

  /**
   * 指定されたIDが有効化を確認する。
   *
   * @param id APIキーのID
   * @return 処理結果、ID不正時Failure(ServiceException)
   */
  def checkId(id: String)(implicit s: DBSession): Try[Unit] = {
    Try {
      val ak = persistence.ApiKey.ak
      val u = persistence.User.u
      withSQL {
        select(ak.result.id)
          .from(persistence.ApiKey as ak)
          .innerJoin(persistence.User as u).on(u.id, ak.userId)
          .where
          .eq(ak.id, sqls.uuid(id))
          .and
          .eq(u.disabled, false)
          .and
          .isNull(ak.deletedAt)
      }.map { rs =>
        rs.string(ak.resultName.id)
      }.single.apply().isDefined
    }.flatMap { contains =>
      if (contains) {
        Success(())
      } else {
        Failure(new ServiceException("無効なAPIキーが指定されました。"))
      }
    }
  }

  /**
   * 指定されたAPIキーを無効化する。
   *
   * @param id APIキーのID
   * @return 処理結果
   */
  def execDisable(id: String)(implicit s: DBSession): Try[Unit] = {
    Try {
      val timestamp = DateTime.now()
      val systemUserId = AppConfig.systemUserId
      val ak = persistence.ApiKey.column
      withSQL {
        update(persistence.ApiKey)
          .set(
            ak.deletedBy -> sqls.uuid(systemUserId),
            ak.deletedAt -> timestamp,
            ak.updatedBy -> sqls.uuid(systemUserId),
            ak.updatedAt -> timestamp
          )
          .where
          .eq(ak.id, sqls.uuid(id))
      }.update.apply()
    }
  }

  /**
   * 指定されたユーザ名を持つユーザにAPIキーを発行する。
   *
   * @param userName ユーザ名
   * @return 発行したAPIキーID、ユーザ名不正時Failure(ServiceException)
   */
  def add(userName: Option[String]): Try[String] = {
    logger.info(LOG_MARKER, Util.createLogMessage("ApiKeyService", "add", Map("userName" -> userName)))
    userName.filter(!_.isEmpty).map { userName =>
      DB.localTx { implicit s =>
        for {
          user <- getUserByName(userName)
          key <- createApiKey(user.id)
        } yield {
          key.id
        }
      }
    }.getOrElse {
      Failure(new ServiceException("ユーザーが指定されていません。"))
    }
  }

  /**
   * ユーザ名からユーザ情報を取得する。
   *
   * @param userName ユーザ名
   * @return ユーザ情報、ユーザ名不正時Failure(ServiceException)
   */
  def getUserByName(userName: String)(implicit s: DBSession): Try[persistence.User] = {
    Try {
      val u = persistence.User.u
      withSQL {
        select(u.result.*)
          .from(persistence.User as u)
          .where
          .eq(u.name, userName)
          .and
          .eq(u.disabled, false)
      }.map { rs =>
        persistence.User(u.resultName)(rs)
      }.single.apply()
    }.flatMap {
      case None => Failure(new ServiceException("無効なユーザーが指定されました。"))
      case Some(user) => Success(user)
    }
  }

  /**
   * 指定されたユーザにAPIキーを発行する。
   *
   * @param userId ユーザID
   * @return 発行したAPIキー
   */
  def createApiKey(userId: String)(implicit s: DBSession): Try[persistence.ApiKey] = {
    Try {
      val u = persistence.User.u
      val timestamp = DateTime.now()
      val systemUserId = AppConfig.systemUserId
      val apiKey = DigestUtils.sha256Hex(UUID.randomUUID().toString)
      val secretKey = DigestUtils.sha256Hex(UUID.randomUUID().toString + apiKey)
      persistence.ApiKey.create(
        id = UUID.randomUUID().toString,
        userId = userId,
        apiKey = apiKey,
        secretKey = secretKey,
        permission = 3,
        createdBy = systemUserId,
        createdAt = timestamp,
        updatedBy = systemUserId,
        updatedAt = timestamp
      )
    }
  }
}
