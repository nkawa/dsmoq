package dsmoq.maintenance.data.dataset

/**
 * データセットアクセス権更新画面を描画するための情報
 */
case class AclUpdateData(
  datasetId: String,
  datasetName: String,
  ownership: SearchResultOwnership
)
