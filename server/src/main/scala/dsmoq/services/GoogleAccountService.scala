package dsmoq.services

import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeRequestUrl, GoogleAuthorizationCodeFlow}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson.JacksonFactory
import dsmoq.persistence.GroupMemberRole
import dsmoq.{services, AppConf, persistence}
import com.google.api.services.oauth2.Oauth2
import org.joda.time.DateTime
import java.util.UUID
import scalikejdbc._
import com.google.api.services.oauth2.model.Userinfoplus
import scala.util.{Failure, Success}
import dsmoq.persistence.PostgresqlHelper._

object GoogleAccountService {
  def getOAuthUrl(location: String) = {
    new GoogleAuthorizationCodeRequestUrl(AppConf.clientId, AppConf.callbackUrl, AppConf.scopes)
      .setState(location).toURL.toString
  }
  
  def loginWithGoogle(authenticationCode: String) = {
    try {
      val googleAccount = getGoogleAccount(authenticationCode)
      getUser(googleAccount)
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  def getUser(googleAccount: Userinfoplus) = {
    try {
      DB localTx { implicit s =>
        val u = persistence.User.u
        val gu = persistence.GoogleUser.gu

        val googleUser = withSQL {
          select(u.result.*)
            .from(persistence.User as u)
            .innerJoin(persistence.GoogleUser as gu).on(u.id, gu.userId)
            .where
            .eq(gu.googleId, googleAccount.getId)
        }
          .map(persistence.User(u.resultName)).single().apply
          .map(x => services.User(x, googleAccount.getEmail))

        val user = googleUser match {
          case Some(x) =>
            updateUser(x, googleAccount)
            x
          case None =>
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
              .map(persistence.User(u.resultName)).single().apply
              .map(x => services.User(x, googleAccount.getEmail))

            importUser match {
              case Some(x) =>
                updateGoogleUser(x, googleAccount)
                x
              case None => createUser(googleAccount)
            }
        }
        Success(user)
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  private def getGoogleAccount(authenticationCode: String) = {
    // 認証トークンからアクセストークン取得
    val flow = new GoogleAuthorizationCodeFlow(
      new NetHttpTransport(),
      new JacksonFactory(),
      AppConf.clientId,
      AppConf.clientSecret,
      AppConf.scopes)
    val tokenResponse = flow.newTokenRequest(authenticationCode)
      .setRedirectUri(AppConf.callbackUrl).execute()
    val credential = flow.createAndStoreCredential(tokenResponse, null)

    // Google APIを使用してユーザー情報取得
    val oauth2 = new Oauth2.Builder(
      credential.getTransport,
      credential.getJsonFactory,
      credential)
      .setApplicationName(AppConf.applicationName).build()

    oauth2.userinfo().get().execute()
  }

  private def createUser(googleAccount: Userinfoplus)(implicit s: DBSession) = {
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

    services.User(user, googleAccount.getEmail)
  }

  private def updateUser(user: services.User, googleAccount: Userinfoplus)(implicit s: DBSession) = {
    val timestamp = DateTime.now
    withSQL {
      val u = persistence.User.column
      update(persistence.User)
        .set(u.name -> googleAccount.getEmail, u.updatedAt -> timestamp, u.updatedBy -> sqls.uuid(AppConf.systemUserId))
        .where
        .eq(u.id, sqls.uuid(user.id))
    }.update().apply
  }

  private def getGoogleUser(user: services.User)(implicit s: DBSession) = {
    val gu = persistence.GoogleUser.gu
    withSQL {
      select(gu.result.*)
        .from(persistence.GoogleUser as gu)
        .where
        .eq(gu.userId, sqls.uuid(user.id))
        .and
        .isNull(gu.deletedAt)
    }
    .map(persistence.GoogleUser(gu.resultName)).single().apply.get
  }

  private def updateGoogleUser(user: services.User, googleAccount: Userinfoplus)(implicit s: DBSession) = {
    val googleUser = getGoogleUser(user)

    val timestamp = DateTime.now
    withSQL {
      val gu = persistence.GoogleUser.column
      update(persistence.GoogleUser)
        .set(gu.googleId -> googleAccount.getId, gu.updatedAt -> timestamp, gu.updatedBy -> sqls.uuid(AppConf.systemUserId))
        .where
        .eq(gu.id, sqls.uuid(googleUser.id))
    }.update().apply
  }
}
