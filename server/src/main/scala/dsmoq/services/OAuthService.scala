package dsmoq.services

import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeRequestUrl, GoogleAuthorizationCodeFlow}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson.JacksonFactory
import dsmoq.{AppConf, persistence, OAuthConf}
import com.google.api.services.oauth2.Oauth2
import org.joda.time.DateTime
import java.util.UUID
import scalikejdbc._, SQLInterpolation._
import com.google.api.services.oauth2.model.Userinfoplus
import scala.util.{Failure, Try, Success}

object OAuthService {
  def getAuthenticationUrl(location: String) = {
    new GoogleAuthorizationCodeRequestUrl(
      OAuthConf.clientId,
      OAuthConf.callbackUrl,
      OAuthConf.scopes)
      .setState(location)
      .toURL.toString
  }
  
  def loginWithGoogle(authenticationCode: String) = {
    try {
      // 認証トークンからアクセストークン取得
      val flow = new GoogleAuthorizationCodeFlow(
        new NetHttpTransport(),
        new JacksonFactory(),
        OAuthConf.clientId,
        OAuthConf.clientSecret,
        OAuthConf.scopes)
      val tokenResponse = flow.newTokenRequest(authenticationCode)
        .setRedirectUri(OAuthConf.callbackUrl).execute();
      val credential = flow.createAndStoreCredential(tokenResponse, null);

      val oauth2 = new Oauth2.Builder(
        credential.getTransport,
        credential.getJsonFactory,
        credential)
        .setApplicationName(OAuthConf.applicationName).build()
      val googleUser = oauth2.userinfo().get().execute()

      // FIXME try~catch実装含め実装見直し(変数名も)
      val result = DB readOnly {
        implicit s =>
          val u = persistence.User.u
          val gu = persistence.GoogleUser.gu

          withSQL {
            select(u.result.*)
              .from(persistence.User as u)
              .innerJoin(persistence.GoogleUser as gu).on(u.id, gu.userId)
              .where
              .eq(gu.googleId, googleUser.getId)
          }
            .map(persistence.User(u.resultName)).single.apply
            .map(x => dsmoq.services.data.User(x))
      }

      // ユーザーがなければユーザー作成
      val user = result match {
        case Some(x) => x
        case None => createUser(googleUser)
      }
      Success(user)
    } catch {
      case e: RuntimeException => Failure(e)
    }
  }

  private def createUser(googleUser: Userinfoplus) = {
    val username = googleUser.getEmail.split('@')(0)
    val timestamp = DateTime.now()
    val dbUser = DB localTx { implicit s =>
      // insert users
      val u = persistence.User.create(
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
        userId = u.id,
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
          userId = u.id,
          googleId = googleUser.getId,
          createdBy = AppConf.systemUserId,
          createdAt = timestamp,
          updatedBy = AppConf.systemUserId,
          updatedAt = timestamp
      )
      // insert groups
      val g = persistence.Group.create(
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
        groupId = g.id,
        userId = u.id,
        role = 1,
        status = 1,
        createdBy = AppConf.systemUserId,
        createdAt = timestamp,
        updatedBy = AppConf.systemUserId,
        updatedAt = timestamp
      )
      u
    }
    dsmoq.services.data.User(dbUser)
  }
}
