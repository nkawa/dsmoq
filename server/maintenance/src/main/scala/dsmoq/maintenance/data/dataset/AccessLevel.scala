package dsmoq.maintenance.data.dataset

import dsmoq.persistence

/**
 * アクセス権のアクセスレベルを表すクラス
 */
sealed trait AccessLevel {
  def toDBValue(): Int
}

/**
 * アクセス権のアクセスレベルを表すクラスのコンパニオンオブジェクト
 */
object AccessLevel {

  /**
   * アクセスレベル：LimitedRead
   */
  case object LimitedRead extends AccessLevel {
    override def toString(): String = "Limited Read"
    def toDBValue(): Int = persistence.UserAccessLevel.LimitedRead
  }

  /**
   * アクセスレベル：FullRead
   */
  case object FullRead extends AccessLevel {
    override def toString(): String = "Full Read"
    def toDBValue(): Int = persistence.UserAccessLevel.FullPublic
  }

  /**
   * アクセスレベル：Owner
   */
  case object Owner extends AccessLevel {
    def toDBValue(): Int = persistence.UserAccessLevel.Owner
  }

  /**
   * アクセスレベル：Provider
   */
  case object Provider extends AccessLevel {
    def toDBValue(): Int = persistence.GroupAccessLevel.Provider
  }

  /**
   * アクセスレベル：Deny
   */
  case object Deny extends AccessLevel {
    def toDBValue(): Int = persistence.GroupAccessLevel.Deny
  }

  /**
   * オプショナルな文字列からアクセスレベルを取得する。
   *
   * @param str オプショナル文字列
   * @return アクセスレベル
   */
  def apply(str: Option[String]): AccessLevel = {
    str match {
      case Some("limitedRead") => LimitedRead
      case Some("fullRead") => FullRead
      case Some("owner") => Owner
      case Some("provider") => Provider
      case _ => Deny
    }
  }

  /**
   * オーナー種別とアクセスレベルのDB値からアクセスレベルを取得する。
   *
   * @param ownerType オーナー種別
   * @param accessLevel アクセスレベル(DB値)
   * @return アクセスレベル
   */
  def apply(ownerType: OwnerType, accessLevel: Int): AccessLevel = {
    (ownerType, accessLevel) match {
      case (OwnerType.User, persistence.UserAccessLevel.LimitedRead) => LimitedRead
      case (OwnerType.User, persistence.UserAccessLevel.FullPublic) => FullRead
      case (OwnerType.User, persistence.UserAccessLevel.Owner) => Owner
      case (OwnerType.Group, persistence.GroupAccessLevel.LimitedPublic) => LimitedRead
      case (OwnerType.Group, persistence.GroupAccessLevel.FullPublic) => FullRead
      case (OwnerType.Group, persistence.GroupAccessLevel.Provider) => Provider
      case _ => Deny
    }
  }
}
