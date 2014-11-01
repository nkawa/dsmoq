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

object GoogleAccountService {
  def getOAuthUrl(location: String) = {
    new GoogleAuthorizationCodeRequestUrl(AppConf.clientId, AppConf.callbackUrl, AppConf.scopes)
      .setState(location).toURL.toString
  }
  
  def loginWithGoogle(authenticationCode: String) = {
    try {
      val googleAccount = getGoogleAccount(authenticationCode)

      DB localTx { implicit s =>
        val u = persistence.User.u
        val gu = persistence.GoogleUser.gu

        val coiUser = withSQL {
          select(u.result.*)
            .from(persistence.User as u)
            .innerJoin(persistence.GoogleUser as gu).on(u.id, gu.userId)
            .where
            .eq(gu.googleId, googleAccount.getId)
        }
        .map(persistence.User(u.resultName)).single().apply
        .map(x => services.User(x, googleAccount.getEmail))

        // ユーザーがなければユーザー作成
        val user = coiUser match {
          case Some(x) => x
          case None => createUser(googleAccount)
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
    val timestamp = DateTime.now()

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

    persistence.GoogleUser.create(
      id = UUID.randomUUID.toString,
      userId = user.id,
      googleId = googleAccount.getId,
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

    services.User(user, googleAccount.getEmail)
  }
}
