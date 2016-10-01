package dsmoq.maintenance.data.group

/**
 * グループメンバー更新画面のパラメータを表すクラス
 */
case class SearchMemberParameter(
  groupId: Option[String],
  userId: Option[String]
) {
  override def toString: String = {
    s"groupId=${groupId},userId=${userId}"
  }
}

/**
 * グループメンバー更新画面のパラメータを表すクラスのコンパニオンオブジェクト
 */
object SearchMemberParameter {

  /**
   * マップ値からパラメータを作成する。
   *
   * @param map マップ値
   * @return パラメータ
   */
  def fromMap(map: Map[String, String]): SearchMemberParameter = {
    SearchMemberParameter(
      groupId = map.get("groupId"),
      userId = map.get("userId")
    )
  }
}
