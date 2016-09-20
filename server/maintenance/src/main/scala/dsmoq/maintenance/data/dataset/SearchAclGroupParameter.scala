package dsmoq.maintenance.data.dataset

/**
 * アクセス権更新(グループ)画面のパラメータを表すクラス
 */
case class SearchAclGroupParameter(
  datasetId: Option[String],
  groupId: Option[String]
) {
  override def toString: String = {
    s"datasetId=${datasetId},groupId=${groupId}"
  }
}

/**
 * アクセス権更新(グループ)画面のパラメータを表すクラスのコンパニオンオブジェクト
 */
object SearchAclGroupParameter {

  /**
   * マップ値からパラメータを作成する。
   *
   * @param map マップ値
   * @return パラメータ
   */
  def fromMap(map: Map[String, String]): SearchAclGroupParameter = {
    SearchAclGroupParameter(
      datasetId = map.get("datasetId"),
      groupId = map.get("groupId")
    )
  }
}
