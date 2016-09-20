package dsmoq.maintenance.data.dataset

import dsmoq.persistence

/**
 * アクセス権のオーナー種別を表すクラス
 */
sealed trait OwnerType

/**
 * アクセス権のオーナー種別を表すクラスのコンパニオンオブジェクト
 */
object OwnerType {

  /**
   * オーナー種別：User
   */
  case object User extends OwnerType {
    override def toString(): String = "ユーザー"
  }

  /**
   * オーナー種別：Group
   */
  case object Group extends OwnerType {
    override def toString(): String = "グループ"
  }

  /**
   * オーナー種別のDB値からオーナー種別を取得する。
   *
   * @param ownerType オーナー種別(DB値)
   * @return オーナー種別
   */
  def apply(ownerType: Int): OwnerType = {
    ownerType match {
      case persistence.OwnerType.Group => OwnerType.Group
      case _ => OwnerType.User
    }
  }
}
