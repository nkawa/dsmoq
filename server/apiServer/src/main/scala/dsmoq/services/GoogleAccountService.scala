package dsmoq.services

import java.util.ResourceBundle
import java.util.UUID

import scala.collection.JavaConversions.asScalaBuffer
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.joda.time.DateTime
import org.slf4j.MarkerFactory

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson.JacksonFactory
import com.google.api.services.oauth2.Oauth2
import com.google.api.services.oauth2.model.Userinfoplus
import com.typesafe.scalalogging.LazyLogging

import dsmoq.AppConf
import dsmoq.ResourceNames
import dsmoq.exceptions.AccessDeniedException
import dsmoq.persistence
import dsmoq.persistence.GoogleUser
import dsmoq.persistence.GroupMemberRole
import dsmoq.persistence.PostgresqlHelper.PgSQLSyntaxType
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.select
import scalikejdbc.sqls
import scalikejdbc.update
import scalikejdbc.withSQL

/**
 * GoogleAccountを取り扱うサービスクラス
 *
 * @param resource リソースバンドルのインスタンス
 */
class GoogleAccountService(resource: ResourceBundle) extends LazyLogging {

  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("AUTH_LOG")

  def getOAuthUrl(location: String): String = {
    new GoogleAuthorizationCodeRequestUrl(
      AppConf.clientId,
      AppConf.callbackUrl,
      AppConf.scopes
    ).setState(location).toURL.toString
  }

  /**
   * GoogleアカウントでOAuth認証を行います。
   *
   * @param authenticationCode 認証コード
   * @return
   *        Success(Googleアカウント情報) 成功時
   *        Failure(AccessDeniedException) 設定された正規表現とメールアドレスがマッチしない場合
   */
  def loginWithGoogle(authenticationCode: String): Try[User] = {
    try {
      val googleAccount = getGoogleAccount(authenticationCode)
      val accountMailaddr = googleAccount.getEmail()

      logger.info(
        LOG_MARKER,
        "Login request... : [id] = {}, [email] = {}",
        googleAccount.getId,
        googleAccount.getEmail
      )

      // 設定された正規表現とメールアドレスがマッチするか
      var matched = AppConf.allowedMailaddrs.exists(accountMailaddr.matches(_))
      if (!matched) {
        // 設定された正規表現とメールアドレスがマッチしない場合
        logger.error(
          LOG_MARKER,
          "Login failed: access denied. [id] = {}, [email] = {}",
          googleAccount.getId,
          googleAccount.getEmail
        )
        throw new AccessDeniedException(resource.getString(ResourceNames.INVALID_EMAIL_FORMAT))
      }

      logger.info(
        LOG_MARKER,
        "Allowed address: [id] = {}, [email] = {}",
        googleAccount.getId,
        googleAccount.getEmail
      )
      getUser(googleAccount)
    } catch {
      case e: AccessDeniedException => {
        logger.error(LOG_MARKER, "Login failed: error occurred.", e)
        Failure(e)
      }
      case t: Throwable => {
        logger.error(LOG_MARKER, "Login failed: error occurred.", t)
        Failure(t)
      }
    }
  }

  def getUser(googleAccount: Userinfoplus): Try[User] = {
    Try {
      DB.localTx { implicit s =>
        val u = persistence.User.u
        val gu = persistence.GoogleUser.gu

        val googleUser = withSQL {
          select(u.result.*)
            .from(persistence.User as u)
            .innerJoin(persistence.GoogleUser as gu).on(u.id, gu.userId)
            .where
            .eq(gu.googleId, googleAccount.getId)
        }
          .map(persistence.User(u.resultName)).single.apply()
          .map(x => User(x, googleAccount.getEmail))

        val user = googleUser match {
          case Some(x) => {
            updateUser(x, googleAccount)
            x
          }
          case None => {
            // ユーザー登録バッチで追加されたユーザーかチェック
            val importUser = withSQL {
              select(u.result.*)
                .from(persistence.User as u)
                .innerJoin(persistence.GoogleUser as gu).on(u.id, gu.userId)
                .where
                .isNull(gu.googleId)
                .and
                .eq(u.name, googleAccount.getEmail)
            }
              .map(persistence.User(u.resultName)).single.apply()
              .map(x => User(x, googleAccount.getEmail))

            importUser match {
              case None => {
                createUser(googleAccount)
              }
              case Some(x) => {
                updateGoogleUser(x, googleAccount)
                x
              }
            }
          }
        }
        logger.info(
          LOG_MARKER,
          "Login successed: [id] = {}, [email] = {}",
          googleAccount.getId,
          googleAccount.getEmail
        )
        user
      }
    }
  }

  private def getGoogleAccount(authenticationCode: String): Userinfoplus = {
    // 認証トークンからアクセストークン取得
    val flow = new GoogleAuthorizationCodeFlow(
      new NetHttpTransport(),
      new JacksonFactory(),
      AppConf.clientId,
      AppConf.clientSecret,
      AppConf.scopes
    )
    val tokenResponse = flow
      .newTokenRequest(authenticationCode)
      .setRedirectUri(AppConf.callbackUrl)
      .execute()
    val credential = flow.createAndStoreCredential(tokenResponse, null)

    // Google APIを使用してユーザー情報取得
    val oauth2 = new Oauth2.Builder(
      credential.getTransport,
      credential.getJsonFactory,
      credential
    ).setApplicationName(AppConf.applicationName).build()

    oauth2.userinfo().get().execute()
  }

  private def createUser(googleAccount: Userinfoplus)(implicit s: DBSession): User = {
    val timestamp = DateTime.now

    val user = persistence.User.create(
      id = UUID.randomUUID.toString,
      name = googleAccount.getEmail,
      fullname = googleAccount.getName,
      organization = "",
      title = "",
      description = "",
      imageId = AppConf.defaultAvatarImageId,
      createdBy = AppConf.systemUserId,
      createdAt = timestamp,
      updatedBy = AppConf.systemUserId,
      updatedAt = timestamp
    )

    persistence.MailAddress.create(
      id = UUID.randomUUID.toString,
      userId = user.id,
      address = googleAccount.getEmail,
      status = 1,
      createdBy = AppConf.systemUserId,
      createdAt = timestamp,
      updatedBy = AppConf.systemUserId,
      updatedAt = timestamp
    )

    val group = persistence.Group.create(
      id = UUID.randomUUID.toString,
      name = googleAccount.getEmail,
      description = "",
      groupType = persistence.GroupType.Personal,
      createdBy = AppConf.systemUserId,
      createdAt = timestamp,
      updatedBy = AppConf.systemUserId,
      updatedAt = timestamp
    )

    persistence.Member.create(
      id = UUID.randomUUID.toString,
      groupId = group.id,
      userId = user.id,
      role = GroupMemberRole.Manager,
      status = 1,
      createdBy = AppConf.systemUserId,
      createdAt = timestamp,
      updatedBy = AppConf.systemUserId,
      updatedAt = timestamp
    )

    persistence.GoogleUser.create(
      id = UUID.randomUUID.toString,
      userId = user.id,
      googleId = googleAccount.getId,
      createdBy = AppConf.systemUserId,
      createdAt = timestamp,
      updatedBy = AppConf.systemUserId,
      updatedAt = timestamp
    )

    User(user, googleAccount.getEmail)
  }

  private def updateUser(user: User, googleAccount: Userinfoplus)(implicit s: DBSession): Int = {
    val timestamp = DateTime.now
    withSQL {
      val u = persistence.User.column
      update(persistence.User)
        .set(
          u.name -> googleAccount.getEmail,
          u.updatedAt -> timestamp,
          u.updatedBy -> sqls.uuid(AppConf.systemUserId)
        )
        .where
        .eq(u.id, sqls.uuid(user.id))
    }.update.apply()
  }

  private def getGoogleUser(user: User)(implicit s: DBSession): GoogleUser = {
    val gu = persistence.GoogleUser.gu
    withSQL {
      select(gu.result.*)
        .from(persistence.GoogleUser as gu)
        .where
        .eq(gu.userId, sqls.uuid(user.id))
        .and
        .isNull(gu.deletedAt)
    }
      .map(persistence.GoogleUser(gu.resultName)).single.apply().get
  }

  private def updateGoogleUser(user: User, googleAccount: Userinfoplus)(implicit s: DBSession): Int = {
    val googleUser = getGoogleUser(user)

    val timestamp = DateTime.now
    withSQL {
      val gu = persistence.GoogleUser.column
      update(persistence.GoogleUser)
        .set(
          gu.googleId -> googleAccount.getId,
          gu.updatedAt -> timestamp,
          gu.updatedBy -> sqls.uuid(AppConf.systemUserId)
        )
        .where
        .eq(gu.id, sqls.uuid(googleUser.id))
    }.update.apply()
  }
}
