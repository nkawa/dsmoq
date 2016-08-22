package dsmoq.apikeyweb

import com.typesafe.scalalogging.LazyLogging
import org.slf4j.MarkerFactory

/**
 * webアプリケーションのコントローラークラス。
 */
class MainServlet extends ApiKeyWebToolStack with LazyLogging {
  private val LOG_MARKER = MarkerFactory.getMarker("APIKEY_LOG")

  /**
   * 各パスを処理する前に実行する。
   */
  before() {
    logger.info(LOG_MARKER, "access: method = {}, path = {}", request.getMethod, request.getRequestURI)
  }

  /**
   * 各パスの処理後に実行する。
   */
  after() {
    logger.debug(LOG_MARKER, "access end: method = {}, path = {}", request.getMethod, request.getRequestURI)
  }

  /**
   * トップページを表示する。(get: /)
   */
  get("/") {
    contentType = "text/html"
    ssp("/index", "title" -> "APIキー発行ツール", "userID" -> "", "message" -> "")
  }

  /**
   * エラー情報付きのトップページを表示する。 (get: /error/:userid)
   * 存在しないユーザIDで検索した場合に表示。
   */
  get("/error/:userid") {
    val userID = params("userid")
    val msg = s"ユーザー $userID は存在しません。"
    logger.warn(LOG_MARKER, "{}", msg)

    contentType = "text/html"
    ssp("/index", "title" -> "APIキー発行ツール", "userID" -> userID, "message" -> msg)
  }

  /**
   *  エラー情報付きのトップページを表示する。(get: /error/no_name)
   *  ユーザIDを入力せずに検索した場合に表示。
   */
  get("/error/no_name") {
    val msg = "ユーザーIDが指定されていません。"
    logger.warn(LOG_MARKER, "{}", msg)

    contentType = "text/html"
    ssp("/index", "title" -> "APIキー発行ツール", "userID" -> "", "message" -> msg)
  }

  /**
   * 発行済みAPIキー一覧表示ページを表示する。 (get: /list)
   */
  get("/list") {
    val keyInfoList = ApiKeyManager.listKeys()

    contentType = "text/html"
    ssp("/list", "title" -> "発行済みAPIキー一覧表示", "keyInfoList" -> keyInfoList, "message" -> "")
  }

  /**
   * エラー情報付きの発行済みAPIキー一覧表示ページを表示する。 (get: /list/no_select)
   * 削除対象を指定せず無効化ボタンを押下した場合に表示。
   */
  get("/list/no_select") {
    val keyInfoList = ApiKeyManager.listKeys()
    val msg = "キーが未選択です。"
    logger.warn(LOG_MARKER, "{}", msg)

    contentType = "text/html"
    ssp("/list", "title" -> "発行済みAPIキー一覧表示", "keyInfoList" -> keyInfoList, "message" -> msg)
  }

  /**
   * 指定したユーザ名の検索結果(APIキー一覧)ページを表示する。 (get: /search_keys)
   * ユーザ名が指定されていない場合、get: /error/no_name に転送する。
   * 指定したユーザ名が見つからない場合、get: /error/:userid に転送する。
   */
  get("/search_keys") {
    val userID = params("user_id")
    if (userID.isEmpty) {
      logger.warn(LOG_MARKER, "not found user_id parameter.")
      redirect("/error/no_name")
    } else {
      ApiKeyManager.searchUserId(userID) match {
        case Some(u) =>
          val keyInfoList = ApiKeyManager.searchKeyFromName(userID)
          contentType = "text/html"
          ssp("/result_keys", "title" -> "検索結果", "userID" -> userID, "keyInfoList" -> keyInfoList)
        case None =>
          logger.warn(LOG_MARKER, "{} is not found.", userID)
          redirect(s"/error/$userID")
      }
    }
  }

  /**
   * 指定したユーザ名のAPIキーを発行する。 (post: /publish)
   * 発行後、発行したAPIキー情報を表示する。
   * ユーザ名が指定されていない場合、get: /error/no_name に転送する。
   * 指定したユーザ名が存在しない場合、get: /error/:userid に転送する。
   */
  post("/publish") {
    val userID = params("user_id")
    if (userID.isEmpty) {
      logger.warn(LOG_MARKER, "not found user_id parameter.")
      redirect("/error/no_name")
    } else {
      ApiKeyManager.publish(userID) match {
        case Some(k) =>
          contentType = "text/html"
          ssp("/result", "title" -> "発行済みAPIキー", "keyInfo" -> k)
        case _ =>
          logger.warn(LOG_MARKER, "{} is not found.", userID)
          redirect(s"/error/$userID")
      }
    }
  }

  /**
   * 指定したAPIキーを無効にする。 (post: /delete)
   * 無効にした後、get: /list に転送する。
   * APIキーが指定されていない場合、get: /list/no_select に転送する。
   */
  post("/delete") {
    try {
      val consumerKey = params("consumerKey")

      ApiKeyManager.deleteKey(consumerKey)
      contentType = "text/html"
      redirect("/list")
    } catch {
      case e: NoSuchElementException =>
        logger.warn(LOG_MARKER, "not selected target.")
        redirect("/list/no_select")
    }
  }

}
