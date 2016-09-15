package dsmoq.maintenance.data.file

import org.joda.time.DateTime

/**
 * ファイルの検索結果を表すケースクラス
 */
case class SearchResultFile(
  datasetName: String,
  id: String,
  name: String,
  size: Long,
  createdBy: Option[String],
  createdAt: DateTime,
  updatedAt: DateTime,
  deletedAt: Option[DateTime]
)
