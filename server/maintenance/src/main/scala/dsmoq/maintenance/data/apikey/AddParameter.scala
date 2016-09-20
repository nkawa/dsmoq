package dsmoq.maintenance.data.apikey

/**
 * APIキー追加のパラメータを表すクラス
 */
case class AddParameter(
  userName: Option[String]
) {
  override def toString: String = {
    s"userName=${userName}"
  }
}

/**
 * APIキー追加のパラメータを表すクラスのコンパニオンオブジェクト
 */
object AddParameter {

  /**
   * マップ値からパラメータを作成する。
   *
   * @param map マップ値
   * @return パラメータ
   */
  def fromMap(map: Map[String, String]): AddParameter = {
    AddParameter(
      userName = map.get("name")
    )
  }
}
