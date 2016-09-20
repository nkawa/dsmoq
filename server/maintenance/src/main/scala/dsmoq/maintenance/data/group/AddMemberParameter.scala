package dsmoq.maintenance.data.group

/**
 * メンバー追加のパラメータを表すクラス
 */
case class AddMemberParameter(
  groupId: Option[String],
  userName: Option[String],
  role: MemberRole
) {
  override def toString: String = {
    s"groupId=${groupId},userName=${userName},role=${role.toString}"
  }
}

/**
 * メンバー追加のパラメータを表すクラスのコンパニオンオブジェクト
 */
object AddMemberParameter {

  /**
   * マップ値からパラメータを作成する。
   *
   * @param map マップ値
   * @return パラメータ
   */
  def fromMap(map: Map[String, String]): AddMemberParameter = {
    AddMemberParameter(
      groupId = map.get("groupId"),
      userName = map.get("userName"),
      role = MemberRole(map.get("role"))
    )
  }
}
