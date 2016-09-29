package dsmoq.maintenance.data.user

import org.scalatra.util.MultiMap

/**
 * ユーザー更新のパラメータを表すクラス
 */
case class UpdateParameter(
  originals: Seq[String],
  updates: Seq[String]
) {
  override def toString: String = {
    s"originals=${originals.toString},updates=${updates.toString}"
  }
}

/**
 * ユーザー更新のパラメータを表すクラスのコンパニオンオブジェクト
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
      originals = map.get("disabled.originals").getOrElse(Seq.empty),
      updates = map.get("disabled.updates").getOrElse(Seq.empty)
    )
  }
}
