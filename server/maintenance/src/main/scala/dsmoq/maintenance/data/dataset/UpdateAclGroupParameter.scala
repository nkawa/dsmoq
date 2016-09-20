package dsmoq.maintenance.data.dataset

/**
 * アクセス権更新(グループ)のパラメータを表すクラス
 */
case class UpdateAclGroupParameter(
  datasetId: Option[String],
  groupId: Option[String],
  accessLevel: AccessLevel
) {
  override def toString: String = {
    s"datasetId=${datasetId},groupId=${groupId},accessLevel=${accessLevel.toString}"
  }
}

/**
 * アクセス権更新(グループ)のパラメータを表すクラスのコンパニオンオブジェクト
 */
object UpdateAclGroupParameter {

  /**
   * マップ値からパラメータを作成する。
   *
   * @param map マップ値
   * @return パラメータ
   */
  def fromMap(map: Map[String, String]): UpdateAclGroupParameter = {
    UpdateAclGroupParameter(
      datasetId = map.get("datasetId"),
      groupId = map.get("groupId"),
      accessLevel = AccessLevel(map.get("accessLevel"))
    )
  }
}
