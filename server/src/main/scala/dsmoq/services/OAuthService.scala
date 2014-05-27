package dsmoq.services

import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeRequestUrl, GoogleAuthorizationCodeFlow}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson.JacksonFactory
import dsmoq.{AppConf, persistence}
import com.google.api.services.oauth2.Oauth2
import org.joda.time.DateTime
import java.util.UUID
import scalikejdbc._, SQLInterpolation._
import com.google.api.services.oauth2.model.Userinfoplus
import scala.util.{Failure, Success}

object OAuthService {
  def getAuthenticationUrl(location: String) = {
    new GoogleAuthorizationCodeRequestUrl(
      AppConf.clientId,
      AppConf.callbackUrl,
      AppConf.scopes)
      .setState(location)
      .toURL.toString
  }
  
  def loginWithGoogle(authenticationCode: String) = {
    try {
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
      val googleUser = oauth2.userinfo().get().execute()

      DB localTx { implicit s =>
        val u = persistence.User.u
        val gu = persistence.GoogleUser.gu

        val coiUser = withSQL {
          select(u.result.*)
            .from(persistence.User as u)
            .innerJoin(persistence.GoogleUser as gu).on(u.id, gu.userId)
            .where
            .eq(gu.googleId, googleUser.getId)
        }
        .map(persistence.User(u.resultName)).single().apply
        .map(x => dsmoq.services.data.User(x, googleUser.getEmail))

        // ユーザーがなければユーザー作成
        val user = coiUser match {
          case Some(x) => x
          case None => createUser(googleUser)
        }
        Success(user)
      }
    } catch {
      case e: RuntimeException => Failure(e)
    }
  }

  private def createUser(googleUser: Userinfoplus)(implicit s: DBSession) = {
    val username = googleUser.getEmail.split('@')(0)
    val timestamp = DateTime.now()

    val user = persistence.User.create(
      id = UUID.randomUUID.toString,
      name = username,
      fullname = googleUser.getName,
      organization = "",
      title = "",
      description = "Google Apps User",
      imageId = AppConf.defaultDatasetImageId,
      createdBy = AppConf.systemUserId,
      createdAt = timestamp,
      updatedBy = AppConf.systemUserId,
      updatedAt = timestamp
    )
    // insert mail_addresses
    persistence.MailAddress.create(
      id = UUID.randomUUID.toString,
      userId = user.id,
      address = googleUser.getEmail,
      status = 1,
      createdBy = AppConf.systemUserId,
      createdAt = timestamp,
      updatedBy = AppConf.systemUserId,
      updatedAt = timestamp
    )
    // insert google_users
    persistence.GoogleUser.create(
        id = UUID.randomUUID.toString,
        userId = user.id,
        googleId = googleUser.getId,
        createdBy = AppConf.systemUserId,
        createdAt = timestamp,
        updatedBy = AppConf.systemUserId,
        updatedAt = timestamp
    )
    // insert groups
    val group = persistence.Group.create(
      id = UUID.randomUUID.toString,
      name = username,
      description = "",
      groupType = 1,
      createdBy = AppConf.systemUserId,
      createdAt = timestamp,
      updatedBy = AppConf.systemUserId,
      updatedAt = timestamp
    )
    // insert members
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

    dsmoq.services.data.User(user, googleUser.getEmail)
  }
}
