package dsmoq.maintenance.data.file

import org.scalatra.util.MultiMap

/**
 * ファイル更新のパラメータを表すクラス
 */
case class UpdateParameter(
  targets: Seq[String]
) {
  override def toString: String = {
    s"targets=${targets.toString}"
  }
}

/**
 * ファイル更新のパラメータを表すクラスのコンパニオンオブジェクト
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
