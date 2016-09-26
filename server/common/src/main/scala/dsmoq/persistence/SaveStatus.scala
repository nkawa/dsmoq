package dsmoq.persistence

/**
 * LocalあるいはS3の保存状態定数定義体
 */
object SaveStatus {

  /**
   * データセットのファイルがS3またはローカルに保存されていないことを示す値
   */
  val NotSaved = 0

  /**
   * データセットのファイルがS3またはローカルに保存されていることを示す値
   */
  val Saved = 1

  /**
   * データセットのファイルをS3からローカル、またはローカルからS3に移動中であることを表す値
   */
  val Synchronizing = 2

  /**
   * データセットのファイルをS3またはローカルから削除中であることを表す値
   */
  val Deleting = 3
}
