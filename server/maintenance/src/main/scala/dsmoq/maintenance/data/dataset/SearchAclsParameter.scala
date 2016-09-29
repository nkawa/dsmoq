package dsmoq.maintenance.data.dataset

/**
 * アクセス権一覧画面、アクセス権追加画面のパラメータを表すクラス
 */
case class SearchAclsParameter(
  datasetId: Option[String]
) {

  def toMap: Map[String, String] = {
    Seq(
      datasetId.map("datasetId" -> _)
    ).flatten.toMap
  }

  override def toString: String = {
    s"datasetId=${datasetId}"
  }
}

/**
 * アクセス権一覧画面、アクセス権追加画面のパラメータを表すクラスのコンパニオンオブジェクト
 */
object SearchAclsParameter {

  /**
   * マップ値からパラメータを作成する。
   *
   * @param map マップ値
   * @return パラメータ
   */
  def fromMap(map: Map[String, String]): SearchAclsParameter = {
    SearchAclsParameter(
      datasetId = map.get("datasetId")
    )
  }
}
