package dsmoq.maintenance.services

import java.security.MessageDigest
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import dsmoq.maintenance.AppConfig
import dsmoq.maintenance.data.localuser.CreateParameter
import dsmoq.persistence._
import org.joda.time.DateTime
import org.slf4j.MarkerFactory
import scalikejdbc.{ DB, DBSession }

import scala.util.Try

/**
 * ローカルユーザー生成サービス
 */
object LocalUserService extends LazyLogging {
  /**
   * ログマーカー
   */
  private val LOG_MARKER = MarkerFactory.getMarker("MAINTENANCE_LOCALUSER_LOG")

  /**
   * サービス名
   */
  private val SERVICE_NAME = LocalUserService.getClass.getSimpleName

  /**
   * デフォルトアイコンのID
   */
  private val defaultAvatarImageId = "8a981652-ea4d-48cf-94db-0ceca7d81aef"

  /**
   * 新規にユーザーを作成する
   *
   * @param param ユーザー情報
   * @return ユーザーID
   */
  def create(param: CreateParameter): Try[String] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "create"))
    import scalikejdbc.TxBoundary.Try._
    val result = DB.localTx { implicit session =>
      for {
        // ユーザーの作成
        userName <- Util.requireString(param.userName, "ユーザー")
        fullName <- Util.require(param.fullName, "フルネーム")
        organization <- Util.require(param.organization, "組織")
        title <- Util.require(param.title, "タイトル")
        description <- Util.require(param.description, "詳細")
        user <- createUser(userName, fullName, organization, title, description, defaultAvatarImageId)(session)

        // パスワードの追加
        password <- Util.requireString(param.password, "パスワード")
        _ <- addPassword(password, user)(session)

        // メールアドレスの追加
        mailAddress <- Util.require(param.mailAddress, "メールアドレス")
        _ <- addMailAddress(mailAddress, user)(session)

        // パーソナルグループの追加
        _ <- joinPersonalGroup(user)(session)
      } yield user.id
    }
    Util.withErrorLogging(logger, LOG_MARKER, result)
  }

  private def createUser(userName: String, fullName: String, organization: String, title: String, description: String,
    imageId: String)(implicit session: DBSession): Try[User] = Try {
    val timestamp = DateTime.now()
    val systemUserId = AppConfig.systemUserId

    User.create(
      id = UUID.randomUUID().toString,
      name = userName,
      fullname = fullName,
      organization = organization,
      title = title,
      description = description,
      imageId = imageId,
      createdBy = systemUserId,
      createdAt = timestamp,
      updatedBy = systemUserId,
      updatedAt = timestamp
    )(session)
  }

  private def addPassword(password: String, user: User)(implicit session: DBSession): Try[Unit] = Try {
    val timestamp = DateTime.now()
    val systemUserId = AppConfig.systemUserId

    def createPasswordHash(password: String) = {
      // TODO ApiServer部とロジックを共通化
      MessageDigest.getInstance("SHA-256").digest(password.getBytes("UTF-8")).map("%02x".format(_)).mkString
    }

    Password.create(
      id = UUID.randomUUID().toString,
      userId = user.id,
      hash = createPasswordHash(password),
      createdBy = systemUserId,
      createdAt = timestamp,
      updatedBy = systemUserId,
      updatedAt = timestamp
    )(session)
  }

  private def addMailAddress(mailAddress: String, user: User)(implicit session: DBSession): Try[Unit] = Try {
    val timestamp = DateTime.now()
    val systemUserId = AppConfig.systemUserId

    MailAddress.create(
      id = UUID.randomUUID().toString,
      userId = user.id,
      address = mailAddress,
      status = 1,
      createdBy = systemUserId,
      createdAt = timestamp,
      updatedBy = systemUserId,
      updatedAt = timestamp
    )(session)
  }

  private def joinPersonalGroup(user: User)(implicit session: DBSession): Try[Unit] = Try {
    val timestamp = DateTime.now()
    val systemUserId = AppConfig.systemUserId

    val group = Group.create(
      id = UUID.randomUUID.toString,
      name = user.name,
      description = "",
      groupType = GroupType.Personal,
      createdBy = systemUserId,
      createdAt = timestamp,
      updatedBy = systemUserId,
      updatedAt = timestamp
    )(session)

    Member.create(
      id = UUID.randomUUID.toString,
      groupId = group.id,
      userId = user.id,
      role = GroupMemberRole.Manager,
      status = 1,
      createdBy = systemUserId,
      createdAt = timestamp,
      updatedBy = systemUserId,
      updatedAt = timestamp
    )(session)
  }

}
