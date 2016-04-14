package dsmoq.apikeyweb

class MainServlet extends ApiKeyWebToolStack {

  get("/") {
    contentType = "text/html"

    <html>
      <body>
        <h1>API発行ツール</h1>
        <form action="/publicsh" method="post">
          <p>
            <label>ユーザー名</label>
            <input name="user_name" type="text"/>
          </p>
          <p>
            <input type="submit" value="発行"/>
          </p>
        </form>
      </body>
    </html>
  }

  post("/publicsh") {
  }

}
