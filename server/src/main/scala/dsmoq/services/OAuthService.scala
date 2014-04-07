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

    // デバッグ用
    println("debug:" + googleUser)
    println("name:" + googleUser.getName)
    println("email:" + googleUser.getEmail)
    println("id:" + googleUser.getId)

    // ユーザーがなければユーザー作成、ユーザー情報を引きセッション作成etc
    // やっつけ mail_addressesとはjoinしていない また、tryでくくる必要あり
    val result = DB readOnly { implicit session =>
      sql"""
        SELECT
          users.*
        FROM
          users
        INNER JOIN
          google_users
        ON
          users.id = google_users.user_id
        WHERE
          google_users.google_id = ${googleUser.getId}
      """.map(_.toMap()).single().apply()
    }

    val user = result match {
      case Some(x) =>
        // FIXME 暫定 あとで↑のSQLとともに直す
        dsmoq.services.data.User(
          id = result.get("id").toString,
          name = result.get("name").toString,
          fullname = result.get("fullname").toString,
          organization = result.get("organization").toString,
          title = result.get("title").toString,
          image = "",
          isGuest = false,
          isDeleted = false
        )
      case None => createUser(googleUser)
    }
    user
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
      // FIXME insert google_users
      sql"""
          INSERT INTO
            google_users(id, user_id, google_id, created_by, created_at, updated_by, updated_at)
          VALUES
            (UUID(${UUID.randomUUID.toString}), UUID(${u.id}), ${googleUser.getId}, UUID(${AppConf.systemUserId}), ${timestamp}, UUID(${AppConf.systemUserId}), ${timestamp})
          """.update().apply()
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
