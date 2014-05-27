package dsmoq.controllers

import org.scalatra.ScalatraServlet
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeRequestUrl, GoogleAuthorizationCodeFlow}
import com.google.api.client.json.jackson.JacksonFactory
import java.util
import com.google.api.services.oauth2.Oauth2
import java.util.UUID
import org.joda.time.DateTime

// あとで消す
import scalikejdbc._, SQLInterpolation._
import dsmoq.{AppConf, persistence}

class MockController extends ScalatraServlet with SessionTrait {
  get("/") {
    <html>
      <body>
        <h1>File Upload Test</h1>
        <h2>Login ※NOT Ajax</h2>
        <form action="../api/signin" method="post">
          <input type="text" name="id" value="test" />
          <input type="text" name="password" value="foo" />
          <input type="submit" value="Login" />
        </form>

        <h2>File Upload</h2>
        <form action="../api/datasets" method="post" enctype="multipart/form-data">
          <p>File to upload: <input type="file" name="file[]" /></p>
          <p>File to upload: <input type="file" name="file[]" /></p>
          <p><input type="submit" value="Upload" /></p>
        </form>

        <h2>Google Account OAuth 2.0</h2>
        <form action="/mock/login_google" method="get">
          <input type="hidden" name="path" value="/mock" />
          <p><input type="submit" value="Login" /></p>
        </form>

        <h2>File Upload(Dataset File Add)</h2>
        <form action="../api/datasets/564eb87b-5c97-49d1-b3c6-4a1f83ae179e/files" method="post" enctype="multipart/form-data">
          <p>File to upload: <input type="file" name="file[]" /></p>
          <p>File to upload: <input type="file" name="file[]" /></p>
          <p><input type="submit" value="Upload" /></p>
        </form>

        <h2>File Upload(Dataset File Modify)</h2>
        <form action="../api/datasets/564eb87b-5c97-49d1-b3c6-4a1f83ae179e/files/e167cc73-f702-4a95-8c64-5b93f79c39ed" method="post" enctype="multipart/form-data">
          <p>File to upload: <input type="file" name="file" /></p>
          <p><input type="submit" value="Upload" /></p>
        </form>

        <h2>Image Upload(Dataset Image Add)</h2>
        <form action="../api/datasets/564eb87b-5c97-49d1-b3c6-4a1f83ae179e/images" method="post" enctype="multipart/form-data">
          <p>File to upload: <input type="file" name="image" /></p>
          <p>File to upload: <input type="file" name="image" /></p>
          <p><input type="submit" value="Upload" /></p>
        </form>

        <h2>Image Upload(Group Image Add)</h2>
        <form action="../api/groups/c78aabf4-d08e-4508-a25f-f1acfd6ae074/images" method="post" enctype="multipart/form-data">
          <p>File to upload: <input type="file" name="image" /></p>
          <p>File to upload: <input type="file" name="image" /></p>
          <p><input type="submit" value="Upload" /></p>
        </form>
      </body>
    </html>
  }

  get("/login_google") {
    // TODO パラメーターチェック(必要なら)、エラー処理
    println(params("path"))

//    val clientId = "770034855439.apps.googleusercontent.com"
//    val callbackUrl = "http://localhost:8080/mock/callback"
//    val scopes = util.Arrays.asList("https://www.googleapis.com/auth/plus.me", "profile", "email")

    val userBackUri = params("path")

//    val url = new GoogleAuthorizationCodeRequestUrl(clientId, callbackUrl, scopes).setState(userBackUri)
    val url = new GoogleAuthorizationCodeRequestUrl(AppConf.clientId, AppConf.callbackUrl, AppConf.scopes).setState(userBackUri)
    redirect(url.toURL.toString)
  }

  get("/callback") {
    // TODO パラメーターチェック(必要ならCSRFチェック)、エラー処理
    // 認証拒否された時、DB接続エラー時、APIコールでエラー時etc
    println(params)

     // 固有設定系はファイルに避ける予定
//    val clientId = "770034855439.apps.googleusercontent.com";
//    val clientSecret = "stTAYEg6CVW6pj7Mab3SgoGm";
//    val callbackUrl = "http://localhost:8080/mock/callback";
//    val scopes = util.Arrays.asList("https://www.googleapis.com/auth/plus.me", "profile", "email")
//    val applicationName = "COI Data Store"


    val userRedirectUri = params("state")
    val authenticationCode = params("code")

    // get access token
    val flow = new GoogleAuthorizationCodeFlow(new NetHttpTransport(), new JacksonFactory(), AppConf.clientId, AppConf.clientSecret, AppConf.scopes);
    val tokenResponse = flow.newTokenRequest(authenticationCode).setRedirectUri(AppConf.callbackUrl).execute();
    val credential = flow.createAndStoreCredential(tokenResponse, null);

    // call google api : get user information (name & e-mail)
    val oauth2 = new Oauth2.Builder(credential.getTransport, credential.getJsonFactory, credential).setApplicationName(AppConf.applicationName).build()
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
          mailAddress = "",
          description = "",
          isGuest = false,
          isDeleted = false
        )
      case None =>
        // ユーザー作成
        val timestamp = DateTime.now()
        val aaa = DB localTx {
          implicit s =>
            // uuid case helperがなかったので暫定的にModel修正(自動生成時に何とかなるのか？)
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
        dsmoq.services.data.User(aaa, user.getEmail)
    }

    // セッション作成
    clearSession()
    setUserInfoToSession(bbb)

    // 元いたページに戻す
    redirect(userRedirectUri)
//    redirect("/mock/finish")
//    redirect("/mock")
//    redirect("/datasets/list")
  }

  get("/finish") {
    <html>
      <body>
        <h1>OAuth Finished.</h1>
      </body>
    </html>
  }
}
