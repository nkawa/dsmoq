package dsmoq.maintenance.data.dataset

import org.joda.time.DateTime

/**
 * データセットの検索結果を表すケースクラス
 */
case class SearchResultDataset(
  id: String,
  name: String,
  description: String,
  owners: Seq[String],
  numOfFiles: Int,
  createdAt: DateTime,
  updatedAt: DateTime,
  deletedAt: Option[DateTime]
)
