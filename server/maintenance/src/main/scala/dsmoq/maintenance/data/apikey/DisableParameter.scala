package dsmoq.maintenance.data.apikey

/**
 * APIキー無効化のパラメータを表すクラス
 */
case class DisableParameter(
  id: Option[String]
) {
  override def toString: String = {
    s"id=${id}"
  }
}

/**
 * APIキー無効化のパラメータを表すクラスのコンパニオンオブジェクト
 */
object DisableParameter {

  /**
   * マップ値からパラメータを作成する。
   *
   * @param map マップ値
   * @return パラメータ
   */
  def fromMap(map: Map[String, String]): DisableParameter = {
    DisableParameter(
      id = map.get("id")
    )
  }
}
