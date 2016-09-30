package dsmoq.exceptions

/**
 * 入力チェックに違反した場合に送出する例外
 *
 * @param errors エラーメッセージのリスト
 */
class InputValidationException(errors: Iterable[(String, String)]) extends RuntimeException {
  val validationErrors = errors

  /**
   * エラーメッセージを取得する
   */
  def getErrorMessage(): Iterable[InputValidationError] = {
    validationErrors.map {
      case (name, message) => {
        InputValidationError(
          name = name,
          message = message
        )
      }
    }
  }
}

/**
 * 入力チェックエラーのケースクラス
 *
 * @param name たいしょうのなまえ
 * @param message エラーメッセージ
 */
case class InputValidationError(
  name: String,
  message: String
)
