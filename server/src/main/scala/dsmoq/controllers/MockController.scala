package dsmoq.controllers

import org.scalatra.ScalatraServlet
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeRequestUrl, GoogleAuthorizationCodeFlow}
import com.google.api.client.json.jackson.JacksonFactory
import java.util
import com.google.api.services.oauth2.Oauth2


class MockController extends ScalatraServlet {
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

        <form action="/mock/login_google" method="get">
          <input type="hidden" name="path" value="/mock" />
          <p><input type="submit" value="Login" /></p>
        </form>
      </body>
    </html>
  }

  get("/login_google") {
    // TODO パラメーターチェック(必要なら)、エラー処理
    println(params("path"))

    val clientId = "770034855439.apps.googleusercontent.com";
    val callbackUrl = "http://localhost:8080/mock/callback";
    val scopes = util.Arrays.asList("https://www.googleapis.com/auth/plus.me", "profile", "email")
    val userBackUri = params("path")

    val url = new GoogleAuthorizationCodeRequestUrl(clientId, callbackUrl, scopes).setState(userBackUri)
    redirect(url.toURL.toString)
  }

  get("/callback") {
    // TODO パラメーターチェック(必要ならCSRFチェック)、エラー処理
    println(params)

     // 固有設定系はファイルに避ける予定
    val clientId = "770034855439.apps.googleusercontent.com";
    val clientSecret = "stTAYEg6CVW6pj7Mab3SgoGm";
    val callbackUrl = "http://localhost:8080/mock/callback";
    val scopes = util.Arrays.asList("https://www.googleapis.com/auth/plus.me", "profile", "email")
    val applicationName = "testproj"

    val userRedirectUri = params("state")
    val authenticationCode = params("code")

    // get access token
    val flow = new GoogleAuthorizationCodeFlow(new NetHttpTransport(), new JacksonFactory(), clientId, clientSecret, scopes);
    val tokenResponse = flow.newTokenRequest(authenticationCode).setRedirectUri(callbackUrl).execute();
    val credential = flow.createAndStoreCredential(tokenResponse, null);

    // call google api : get user information (name & e-mail)
    val oauth2 = new Oauth2.Builder(credential.getTransport, credential.getJsonFactory, credential).setApplicationName(applicationName).build()
    val user = oauth2.userinfo().get().execute()
    println("name:" + user.getName)
    println("email:" + user.getEmail)
    println("id:" + user.getId)

    // ユーザーがなければユーザー作成、ユーザー情報を引きセッション作成etc

//    redirect(userRedirectUri)
    redirect("/mock/finish")
  }

  get("/finish") {
    <html>
      <body>
        <h1>OAuth Finished.</h1>
      </body>
    </html>
  }
}
