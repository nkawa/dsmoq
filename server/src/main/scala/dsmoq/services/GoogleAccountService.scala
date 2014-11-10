package dsmoq.services

import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeRequestUrl, GoogleAuthorizationCodeFlow}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson.JacksonFactory
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

      DB localTx { implicit s =>
        val timestamp = DateTime.now

        // ユーザーの存在チェック
        val user = getUser(googleAccount) match {
          case Some(x) => x
          case None => createUser(googleAccount, timestamp)
        }

        // google_userの存在チェック
        getGoogleUser(user) match {
          case Some(x) =>
            if (x.googleId == null) {
              updateGoogleUser(x, googleAccount, timestamp)
            } else if (x.googleId != googleAccount.getId) {
              throw new Exception
            }
          case None => createGoogleUser(user, googleAccount, timestamp)
        }

        Success(services.User(user, googleAccount.getEmail)
        )
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

  private def getUser(googleAccount: Userinfoplus)(implicit s: DBSession) = {
    val u = persistence.User.u
    withSQL {
      select(u.result.*)
        .from(persistence.User as u)
        .where
        .eq(u.name, googleAccount.getEmail)
        .and
        .isNull(u.deletedAt)
    }
    .map(persistence.User(u.resultName)).single().apply
  }

  private def getGoogleUser(user: persistence.User)(implicit s: DBSession) = {
    val gu = persistence.GoogleUser.gu
    withSQL {
      select(gu.result.*)
        .from(persistence.GoogleUser as gu)
        .where
        .eq(gu.userId, sqls.uuid(user.id))
        .and
        .isNull(gu.deletedAt)
    }
    .map(persistence.GoogleUser(gu.resultName)).single().apply
  }

  private def createUser(googleAccount: Userinfoplus, timestamp: DateTime)(implicit s: DBSession) = {
    val user = persistence.User.create(
      id = UUID.randomUUID.toString,
      name = googleAccount.getEmail,
      fullname = googleAccount.getName,
      organization = "",
      title = "",
      description = "",
      imageId = AppConf.defaultDatasetImageId,
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
      role = 1,
      status = 1,
      createdBy = AppConf.systemUserId,
      createdAt = timestamp,
      updatedBy = AppConf.systemUserId,
      updatedAt = timestamp
    )

    user
  }

  private def createGoogleUser(user: persistence.User, googleAccount: Userinfoplus, timestamp: DateTime)(implicit s: DBSession) = {
    persistence.GoogleUser.create(
      id = UUID.randomUUID.toString,
      userId = user.id,
      googleId = googleAccount.getId,
      createdBy = AppConf.systemUserId,
      createdAt = timestamp,
      updatedBy = AppConf.systemUserId,
      updatedAt = timestamp
    )
  }

  private def updateGoogleUser(googleUser: persistence.GoogleUser, googleAccount: Userinfoplus, timestamp: DateTime)(implicit s: DBSession) = {
    withSQL {
      val gu = persistence.GoogleUser.column
      update(persistence.GoogleUser)
        .set(gu.googleId -> googleAccount.getId, gu.updatedAt -> timestamp, gu.updatedBy -> sqls.uuid(AppConf.systemUserId))
        .where
        .eq(gu.id, sqls.uuid(googleUser.id))
    }.update().apply
  }
}
