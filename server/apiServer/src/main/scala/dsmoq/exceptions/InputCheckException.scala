package dsmoq.exceptions

/**
 * 入力チェックに違反した場合に送出する例外
 *
 * @param target 対象名
 * @param message エラーメッセージ
 * @param isUrlParam 対象がURLパラメータか否か
 */
class InputCheckException(
  val target: String,
  val message: String,
  val isUrlParam: Boolean
) extends IllegalArgumentException(message)
