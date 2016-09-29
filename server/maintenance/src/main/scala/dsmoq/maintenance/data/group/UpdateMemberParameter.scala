package dsmoq.maintenance.data.group

/**
 * メンバー更新のパラメータを表すクラス
 */
case class UpdateMemberParameter(
  groupId: Option[String],
  userId: Option[String],
  role: MemberRole
) {
  override def toString: String = {
    s"groupId=${groupId},userId=${userId},role=${role.toString}"
  }
}

/**
 * メンバー更新のパラメータを表すクラスのコンパニオンオブジェクト
 */
object UpdateMemberParameter {

  /**
   * マップ値からパラメータを作成する。
   *
   * @param map マップ値
   * @return パラメータ
   */
  def fromMap(map: Map[String, String]): UpdateMemberParameter = {
    UpdateMemberParameter(
      groupId = map.get("groupId"),
      userId = map.get("userId"),
      role = MemberRole(map.get("role"))
    )
  }
}
