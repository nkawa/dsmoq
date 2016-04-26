package dsmoq.apikeyweb

class MainServlet extends ApiKeyWebToolStack {
  get("/") {
    contentType = "text/html"
    ssp("/index", "title" -> "APIキー発行ツール", "userID" -> "", "message" -> "")
  }

  get("/error") {
    redirect("/")
  }

  get("/error/:userid") {
    val userID = params("userid")
    val msg = s"ユーザ $userID は存在しません。"

    contentType = "text/html"
    ssp("/index", "title" -> "APIキー発行ツール", "userID" -> userID, "message" -> msg)
  }

  get("/list") {
    val keyInfoList = ApiKeyManager.listKeys()

    contentType = "text/html"
    ssp("/list", "title" -> "発行済みAPIキー一覧表示", "keyInfoList" -> keyInfoList)
  }

  get("/search_keys") {
    val userID = params("user_id")
    if (userID.isEmpty) {
      redirect("/")
    } else {
      ApiKeyManager.searchUserId(userID) match {
        case Some(u) =>
          val keyInfoList = ApiKeyManager.searchKeyFromName(userID)
          contentType = "text/html"
          ssp("/result_keys", "title" -> "検索結果", "userID" -> userID, "keyInfoList" -> keyInfoList)
        case None =>
          redirect(s"/error/$userID")
      }
    }
  }

  post("/publish") {
    val userID = params("user_id")

    ApiKeyManager.publish(userID) match {
      case Some(k) =>
        contentType = "text/html"
        ssp("/result", "title" -> "発行済みAPIキー", "keyInfo" -> k)
      case _ =>
        redirect(s"/error/$userID")
    }
  }

  post("/delete") {
    val consumerKey = params("consumerKey")

    ApiKeyManager.deleteKey(consumerKey)
    contentType = "text/html"
    redirect("/list")
  }

}
