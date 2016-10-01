package dsmoq.exceptions

import dsmoq.services.User

/**
 * 権限チェックに違反した場合に送出する例外
 *
 * @param user 権限チェックに違反したユーザ
 * @param message エラーメッセージ
 */
class AccessDeniedException(message: String, val user: Option[User]) extends Exception(message)
