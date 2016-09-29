package dsmoq.exceptions

/**
 * リソースが見つからなかった場合に送出する例外
 *
 * @param message エラーメッセージ
 */
class NotFoundException(message: String = "") extends RuntimeException(message)
