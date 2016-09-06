package dsmoq.maintenance.services

/**
 * サービス部で想定されたエラーが起きたことを表す例外
 *
 * @param message エラーメッセージ
 */
class ServiceException(message: String) extends Exception(message)
