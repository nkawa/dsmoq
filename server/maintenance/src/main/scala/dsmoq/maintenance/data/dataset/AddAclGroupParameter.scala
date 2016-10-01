package dsmoq.maintenance.data.dataset

/**
 * アクセス権追加(グループ)のパラメータを表すクラス
 */
case class AddAclGroupParameter(
  datasetId: Option[String],
  groupName: Option[String],
  accessLevel: AccessLevel
) {
  override def toString: String = {
    s"datasetId=${datasetId},groupName=${groupName},accessLevel=${accessLevel.toString}"
  }
}

/**
 * アクセス権追加(グループ)のパラメータを表すクラスのコンパニオンオブジェクト
 */
object AddAclGroupParameter {

  /**
   * マップ値からパラメータを作成する。
   *
   * @param map マップ値
   * @return パラメータ
   */
  def fromMap(map: Map[String, String]): AddAclGroupParameter = {
    AddAclGroupParameter(
      datasetId = map.get("datasetId"),
      groupName = map.get("groupName"),
      accessLevel = AccessLevel(map.get("accessLevel"))
    )
  }
}
