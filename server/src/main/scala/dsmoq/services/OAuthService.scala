package dsmoq.services

import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeRequestUrl, GoogleAuthorizationCodeFlow}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson.JacksonFactory
import dsmoq.{AppConf, persistence, OAuthConf}
import com.google.api.services.oauth2.Oauth2
import org.joda.time.DateTime
import java.util.UUID
import scalikejdbc._, SQLInterpolation._

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
    // get access token
    val flow = new GoogleAuthorizationCodeFlow(new NetHttpTransport(), new JacksonFactory(), OAuthConf.clientId, OAuthConf.clientSecret, OAuthConf.scopes);
    val tokenResponse = flow.newTokenRequest(authenticationCode).setRedirectUri(OAuthConf.callbackUrl).execute();
    val credential = flow.createAndStoreCredential(tokenResponse, null);

    // call google api : get user information (name & e-mail)
    val oauth2 = new Oauth2.Builder(credential.getTransport, credential.getJsonFactory, credential).setApplicationName(OAuthConf.applicationName).build()
    val user = oauth2.userinfo().get().execute()
    println("debug:" + user)
    println("name:" + user.getName)
    println("email:" + user.getEmail)
    println("id:" + user.getId)

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
          google_users.google_user_id = ${user.getId}
      """.map(_.toMap()).single().apply()
    }

    val bbb = result match {
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
      case None =>
        // ユーザー作成
        val timestamp = DateTime.now()
        val aaa = DB localTx {
          implicit s =>
            val username = user.getEmail split '@'
            val u = persistence.User.create(
              id = UUID.randomUUID.toString,
              name = username(0),
              fullname = user.getName,
              organization = "",
              title = "",
              description = "Google Apps User",
              imageId = AppConf.defaultDatasetImageId,
              createdBy = AppConf.systemUserId,
              createdAt = timestamp,
              updatedBy = AppConf.systemUserId,
              updatedAt = timestamp
            )
            val mail = persistence.MailAddress.create(
              id = UUID.randomUUID.toString,
              userId = u.id,
              address = user.getEmail,
              status = 1,
              createdBy = AppConf.systemUserId,
              createdAt = timestamp,
              updatedBy = AppConf.systemUserId,
              updatedAt = timestamp
            )
            // やっつけ google_usersに保存
            sql"""
            INSERT INTO
              google_users(id, user_id, google_user_id, created_by, created_at, updated_by, updated_at)
            VALUES
              (UUID(${UUID.randomUUID.toString}), UUID(${u.id}), ${user.getId}, UUID(${AppConf.systemUserId}), ${timestamp}, UUID(${AppConf.systemUserId}), ${timestamp})
            """.update().apply()
            // groupの作成
            val g = persistence.Group.create(
              id = UUID.randomUUID.toString,
              name = username(0),
              description = "",
              groupType = 1,
              createdBy = AppConf.systemUserId,
              createdAt = timestamp,
              updatedBy = AppConf.systemUserId,
              updatedAt = timestamp
            )
            // memberの作成
            val m = persistence.Member.create(
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
        dsmoq.services.data.User(aaa)
    }
    bbb
  }
}
