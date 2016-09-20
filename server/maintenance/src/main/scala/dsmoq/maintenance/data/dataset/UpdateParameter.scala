package dsmoq.maintenance.data.dataset

import org.scalatra.util.MultiMap

/**
 * データセット更新のパラメータを表すクラス
 */
case class UpdateParameter(
  targets: Seq[String]
) {
  override def toString: String = {
    s"targets=${targets.toString}"
  }
}

/**
 * データセット更新のパラメータを表すクラスのコンパニオンオブジェクト
 */
object UpdateParameter {

  /**
   * マップ値からパラメータを作成する。
   *
   * @param map マップ値
   * @return パラメータ
   */
  def fromMap(map: MultiMap): UpdateParameter = {
    UpdateParameter(
      targets = map("checked")
    )
  }
}
