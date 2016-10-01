package dsmoq.maintenance.data.dataset

/**
 * アクセス権更新(ユーザー)画面のパラメータを表すクラス
 */
case class SearchAclUserParameter(
  datasetId: Option[String],
  userId: Option[String]
) {
  override def toString: String = {
    s"datasetId=${datasetId},userId=${userId}"
  }
}

/**
 * アクセス権更新(ユーザー)画面のパラメータを表すクラスのコンパニオンオブジェクト
 */
object SearchAclUserParameter {

  /**
   * マップ値からパラメータを作成する。
   *
   * @param map マップ値
   * @return パラメータ
   */
  def fromMap(map: Map[String, String]): SearchAclUserParameter = {
    SearchAclUserParameter(
      datasetId = map.get("datasetId"),
      userId = map.get("userId")
    )
  }
}
