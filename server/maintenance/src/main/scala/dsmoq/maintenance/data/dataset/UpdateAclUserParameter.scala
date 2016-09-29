package dsmoq.maintenance.data.dataset

/**
 * アクセス権更新(ユーザー)のパラメータを表すクラス
 */
case class UpdateAclUserParameter(
  datasetId: Option[String],
  userId: Option[String],
  accessLevel: AccessLevel
) {
  override def toString: String = {
    s"datasetId=${datasetId},userId=${userId},accessLevel=${accessLevel.toString}"
  }
}

/**
 * アクセス権更新(ユーザー)のパラメータを表すクラスのコンパニオンオブジェクト
 */
object UpdateAclUserParameter {

  /**
   * マップ値からパラメータを作成する。
   *
   * @param map マップ値
   * @return パラメータ
   */
  def fromMap(map: Map[String, String]): UpdateAclUserParameter = {
    UpdateAclUserParameter(
      datasetId = map.get("datasetId"),
      userId = map.get("userId"),
      accessLevel = AccessLevel(map.get("accessLevel"))
    )
  }
}
