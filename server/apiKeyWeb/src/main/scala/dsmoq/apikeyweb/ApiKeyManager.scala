package dsmoq.apikeyweb

import java.util.UUID

import dsmoq.persistence.PostgresqlHelper._
import dsmoq.persistence.{ ApiKey, User }
import org.apache.commons.codec.digest.DigestUtils
import org.joda.time.DateTime
import scalikejdbc._
import com.typesafe.scalalogging.LazyLogging
import org.slf4j.MarkerFactory

/**
 * APIキーの発行、削除、取得等の管理を行う。
 */
object ApiKeyManager extends LazyLogging {
  val systemUserId = "dccc110c-c34f-40ed-be2c-7e34a9f1b8f0"

  private val LOG_MARKER = MarkerFactory.getMarker("APIKEY_LOG")

  /**
   * APIキーとSecretキーを生成する。
   *
   * @param userName 生成対象のユーザ名 (DB:users.name)
   * @return 生成したAPIキー情報。対象のユーザが見つからない場合、None。
   */
  def publish(userName: String): Option[KeyInfo] = {
    logger.debug(LOG_MARKER, "publish: start : [userName] = {}", userName)
    DB localTx { implicit s =>
      val u = User.u
      val userId = withSQL {
        select(u.result.id).from(User as u).where.eq(u.name, userName)
      }.map(rs => rs.string(u.resultName.id)).single.apply
      logger.debug(LOG_MARKER, "publish: get user_id of userName: [userId] = {}", userId)

      val timestamp = DateTime.now
      val result: Option[KeyInfo] = userId match {
        case Some(uId) =>
          val apiKey = DigestUtils.sha256Hex(UUID.randomUUID().toString)
          val secretKey = DigestUtils.sha256Hex(UUID.randomUUID().toString + apiKey)
          logger.debug(LOG_MARKER, "publish: generate key : [apiKey] = {}, [secretKey] = {}", apiKey, secretKey)
          ApiKey.create(
            id = UUID.randomUUID().toString,
            userId = uId,
            apiKey = apiKey,
            secretKey = secretKey,
            permission = 3,
            createdBy = systemUserId,
            createdAt = timestamp,
            updatedBy = systemUserId,
            updatedAt = timestamp
          )
          logger.info(LOG_MARKER, "publish: create api_key : [userName] = {}, [userId] = {}, [apiKey] = {}, [secretKey] = {}",
            userName, userId, apiKey, secretKey)
          Option(KeyInfo(userName, apiKey, secretKey))
        case _ =>
          logger.info(LOG_MARKER, "publish: create get user_id of userName: [userId] = {}", userId)
          None
      }
      logger.debug(LOG_MARKER, "publish: end : create get user_id of userName: [result] = {}", result)
      result
    }
  }

  /**
   * 有効なAPIキー情報の一覧を取得する。
   *
   * @return APIキー情報のリスト
   */
  def listKeys(): List[KeyInfo] = {
    logger.debug(LOG_MARKER, "listKeys: start")
    DB readOnly { implicit s =>
      val ak = ApiKey.ak
      val u = User.u
      val result = withSQL {
        select(u.result.*, ak.result.apiKey, ak.result.secretKey)
          .from(ApiKey as ak)
          .innerJoin(User as u).on(ak.userId, u.id)
          .where
          .isNull(ak.deletedBy)
          .and
          .isNull(ak.deletedAt)
          .orderBy(u.name, ak.createdAt)
      }.map(rs => KeyInfo(rs.string(u.resultName.name), rs.string(ak.resultName.apiKey), rs.string(ak.resultName.secretKey)))
        .list
        .apply()
      logger.info(LOG_MARKER, "listKeys: [result size] = {}", result.size.toString)
      logger.debug(LOG_MARKER, "listKeys: end : [result] = {}", result)
      result
    }
  }

  /**
   * 指定したユーザ名のidを取得する。
   *
   * @param userName ユーザ名 (DB:users.name)
   * @return ユーザのid (DB:users.id)
   */
  def searchUserId(userName: String): Option[String] = {
    logger.debug(LOG_MARKER, "searchUserId: start : [userName] = {}", userName)
    DB readOnly { implicit s =>
      val u = User.u
      val result = withSQL {
        select(u.result.id).from(User as u).where.eq(u.name, userName)
      }.map(rs => rs.string(u.resultName.id)).single.apply
      logger.info(LOG_MARKER, "searchUserId: [result size] = {}", result.size.toString)
      logger.debug(LOG_MARKER, "searchUserId: end : [result] = {}", result.toString)
      result
    }
  }

  /**
   * 指定したユーザ名の有効なAPIキー情報の一覧を取得する。
   *
   * @param loginName ユーザ名 (DB:users.name)
   * @return APIキー情報のリスト
   */
  def searchKeyFromName(loginName: String): List[KeyInfo] = {
    logger.debug(LOG_MARKER, "searchKeyFromName: start : [loginName] = {}", loginName)
    DB readOnly { implicit s =>
      val ak = ApiKey.ak
      val u = User.u
      val result = withSQL {
        select(u.result.*, ak.result.apiKey, ak.result.secretKey)
          .from(ApiKey as ak)
          .innerJoin(User as u).on(ak.userId, u.id)
          .where
          .eq(u.name, loginName)
          .and
          .isNull(ak.deletedBy)
          .and
          .isNull(ak.deletedAt)
          .orderBy(ak.createdAt)
      }.map(rs => KeyInfo(rs.string(u.resultName.name), rs.string(ak.resultName.apiKey), rs.string(ak.resultName.secretKey)))
        .list
        .apply()
      logger.info(LOG_MARKER, "searchKeyFromName: [result size] = {}", result.size.toString)
      logger.debug(LOG_MARKER, "searchKeyFromName: end : [result] = {}", result)
      result
    }
  }

  /**
   * 指定したAPIキーを削除(無効化)する。
   *
   * @param key 削除対象のAPIキー (DB:api_key.api_key)
   * @return true:削除成功、false:削除対象がみつからない
   */
  def deleteKey(key: String): Boolean = {
    logger.debug(LOG_MARKER, "deleteKey: start : [key] = {}", key)
    DB localTx { implicit s =>
      val ak = ApiKey.ak
      val apiKey = withSQL {
        select.from(ApiKey as ak).where.eq(ak.apiKey, key)
      }.map(ApiKey(ak)).single.apply

      val timestamp = DateTime.now
      val result = apiKey match {
        case Some(k) =>
          val c = ApiKey.column
          withSQL {
            update(ApiKey)
              .set(c.deletedAt -> timestamp, c.deletedBy -> sqls.uuid(systemUserId))
              .where
              .eq(c.id, sqls.uuid(k.id))
          }.update().apply
          logger.info(LOG_MARKER, "deleteKey: set delete flag [key] = {}", key)
          true
        case _ =>
          logger.info(LOG_MARKER, "deleteKey: target not found [key] = {}", key)
          false
      }
      logger.debug(LOG_MARKER, "deleteKey: end : [result] = {}", result.toString)
      result
    }
  }

}

/**
 * APIキー情報
 */
case class KeyInfo(userID: String, consumerKey: String, secretKey: String)
