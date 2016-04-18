package dsmoq.apikeyweb

class MainServlet extends ApiKeyWebToolStack {
  get("/") {
    contentType = "text/html"
    ssp("/index", "userName" -> "", "message" -> "")
  }

  get("/error") {
    redirect("/")
  }

  get("/error/:username") {
    val userName = params("username")
    val msg = s"ユーザ $userName は存在しません。"

    contentType = "text/html"
    ssp("/index", "userName" -> userName, "message" -> msg)
  }

  get("/list") {
    val keyInfoList = ApiKeyManager.listKeys()

    contentType = "text/html"
    ssp("/list", "keyInfoList" -> keyInfoList)
  }

  get("/search_keys") {
    val userName = params("user_name")
    if (userName.isEmpty) {
      redirect("/")
    } else {
      ApiKeyManager.searchUserId(userName) match {
        case Some(u) =>
          val keyInfoList = ApiKeyManager.searchKeyFromName(userName)
          contentType = "text/html"
          ssp("/result_keys", "userName" -> userName, "keyInfoList" -> keyInfoList)
        case None =>
          redirect(s"/error/$userName")
      }
    }
  }

  post("/publish") {
    val userName = params("user_name")

    ApiKeyManager.publish(userName) match {
      case Some(k) =>
        contentType = "text/html"
        ssp("/result", "keyInfo" -> k)
      case _ =>
        redirect(s"/error/$userName")
    }
  }

  post("/delete") {
    val consumerKey = params("consumerKey")

    ApiKeyManager.deleteKey(consumerKey)
    contentType = "text/html"
    redirect("/list")
  }

}
