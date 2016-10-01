package dsmoq.maintenance.data.group

import org.joda.time.DateTime

/**
 * グループの検索結果を表すケースクラス
 */
case class SearchResultGroup(
  id: String,
  name: String,
  description: String,
  managers: Seq[String],
  createdAt: DateTime,
  updatedAt: DateTime,
  deletedAt: Option[DateTime]
)
