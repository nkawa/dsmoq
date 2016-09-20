package dsmoq.maintenance.data.group

/**
 * グループメンバー一覧画面、グループメンバー追加画面のパラメータを表すクラス
 */
case class SearchMembersParameter(
  groupId: Option[String]
) {
  override def toString: String = {
    s"groupId=${groupId}"
  }
}

/**
 * グループメンバー一覧画面、グループメンバー追加画面のパラメータを表すクラスのコンパニオンオブジェクト
 */
object SearchMembersParameter {

  /**
   * マップ値からパラメータを作成する。
   *
   * @param map マップ値
   * @return パラメータ
   */
  def fromMap(map: Map[String, String]): SearchMembersParameter = {
    SearchMembersParameter(
      groupId = map.get("groupId")
    )
  }
}
