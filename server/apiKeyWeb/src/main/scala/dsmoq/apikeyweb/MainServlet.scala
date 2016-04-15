package dsmoq.apikeyweb

class MainServlet extends ApiKeyWebToolStack {
  val systemUserId = "dccc110c-c34f-40ed-be2c-7e34a9f1b8f0"

  get("/") {
    contentType = "text/html"
    ssp("/index", "userName" -> "", "message" -> "")
  }

  get("/error/:username") {
    val userName = params("username")
    val msg = "ユーザ \"%s\" は存在しません。".format(userName)

    contentType = "text/html"
    ssp("/index", "userName" -> userName, "message" -> msg)
  }

  get("/list") {
    val keyInfoList = ApiKeyManager.listKeys()

    contentType = "text/html"
    ssp("/list", "keyInfoList" -> keyInfoList)
  }

  post("/publish") {
    val userName = params("user_name")

    ApiKeyManager.publish(userName) match {
      case Some(k) =>
        contentType = "text/html"
        ssp("/result", "keyInfo" -> k)
      case _ =>
        redirect("/error/" + userName)
    }
  }

  post("/delete") {
    val consumerKey = params("consumerKey")

    ApiKeyManager.deleteKey(consumerKey)
    contentType = "text/html"
    redirect("/list")
  }

}
