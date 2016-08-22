package dsmoq.exceptions

/**
 * 権限チェックに違反した場合に送出する例外
 *
 * @param message エラーメッセージ
 */
class AccessDeniedException(message: String) extends Exception(message)
