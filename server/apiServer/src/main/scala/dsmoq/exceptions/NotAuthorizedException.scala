package dsmoq.exceptions

/**
 * 認証失敗時に送出する例外
 *
 * @param message エラーメッセージ
 */
class NotAuthorizedException(message: String) extends Exception(message)
