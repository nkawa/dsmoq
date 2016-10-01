package dsmoq.maintenance.data.dataset

/**
 * データセットアクセス権一覧画面を描画するための情報
 */
case class AclListData(
  datasetId: String,
  datasetName: String,
  ownerships: Seq[SearchResultOwnership]
)
