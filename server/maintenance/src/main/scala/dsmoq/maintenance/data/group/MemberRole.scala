package dsmoq.maintenance.data.group

import dsmoq.persistence

/**
 * メンバーロールを表すクラス
 */
sealed trait MemberRole {
  def toDBValue(): Int
}

/**
 * メンバーロールを表すクラスのコンパニオンオブジェクト
 */
object MemberRole {

  /**
   * メンバーロール：Member
   */
  case object Member extends MemberRole {
    def toDBValue(): Int = persistence.GroupMemberRole.Member
  }

  /**
   * メンバーロール：Manager
   */
  case object Manager extends MemberRole {
    def toDBValue(): Int = persistence.GroupMemberRole.Manager
  }

  /**
   * メンバーロール：Deny
   */
  case object Deny extends MemberRole {
    def toDBValue(): Int = persistence.GroupMemberRole.Deny
  }

  /**
   * オプショナルな文字列からメンバーロールを取得する。
   *
   * @param str オプショナル文字列
   * @return メンバーロール
   */
  def apply(str: Option[String]): MemberRole = {
    str match {
      case Some("member") => Member
      case Some("manager") => Manager
      case _ => Deny
    }
  }

  /**
   * メンバーロールのDB値からメンバーロールを取得する。
   *
   * @param role メンバーロール(DB値)
   * @return メンバーロール
   */
  def apply(role: Int): MemberRole = {
    role match {
      case persistence.GroupMemberRole.Member => Member
      case persistence.GroupMemberRole.Manager => Manager
      case _ => Deny
    }
  }
}
