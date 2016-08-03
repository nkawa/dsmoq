package dsmoq.services

/**
 * LocalあるいはS3の保存先変更定数定義体
 */
object MoveToStatus {

  /**
   * S3に保存先を変更することを表す定数値
   */
  val S3 = 0

  /**
   * Localに保存先を変更することを表す定数値
   */
  val LOCAL = 1
}
