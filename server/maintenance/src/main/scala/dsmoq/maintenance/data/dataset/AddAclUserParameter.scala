package dsmoq.maintenance.data.dataset

/**
 * アクセス権追加(ユーザー)のパラメータを表すクラス
 */
case class AddAclUserParameter(
  datasetId: Option[String],
  userName: Option[String],
  accessLevel: AccessLevel
) {
  override def toString: String = {
    s"datasetId=${datasetId},userName=${userName},accessLevel=${accessLevel.toString}"
  }
}

/**
 * アクセス権追加(ユーザー)のパラメータを表すクラスのコンパニオンオブジェクト
 */
object AddAclUserParameter {

  /**
   * マップ値からパラメータを作成する。
   *
   * @param map マップ値
   * @return パラメータ
   */
  def fromMap(map: Map[String, String]): AddAclUserParameter = {
    AddAclUserParameter(
      datasetId = map.get("datasetId"),
      userName = map.get("userName"),
      accessLevel = AccessLevel(map.get("accessLevel"))
    )
  }
}
