package dsmoq.maintenance.services

/**
 * サービス部で想定されたエラーが起きたことを表す例外
 *
 * @param message エラーメッセージ
 * @param withLogging ログ出力するか否か
 */
class ServiceException(
  message: String,
  val details: Seq[ErrorDetail] = Seq.empty,
  val withLogging: Boolean = true
) extends Exception(message)

/**
 * エラー内容の詳細
 * @param title エラーのタイトル
 * @param messages 列挙するメッセージのリスト
 */
case class ErrorDetail(title: String, messages: Seq[String])
