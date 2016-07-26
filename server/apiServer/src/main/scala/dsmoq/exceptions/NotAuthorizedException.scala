package dsmoq.exceptions

/**
 * ログイン失敗、ゲストアクセス不可能なAPIにアクセスした場合などに送出する例外
 *
 * @param message エラーメッセージ
 */
case class NotAuthorizedException(message: String = "") extends Exception(message)
