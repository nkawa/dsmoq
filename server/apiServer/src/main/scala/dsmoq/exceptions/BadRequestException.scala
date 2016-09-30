package dsmoq.exceptions

/**
 * 内部チェックに違反した場合に送出する例外
 *
 * @param message エラーメッセージ
 */
class BadRequestException(message: String) extends RuntimeException(message)
