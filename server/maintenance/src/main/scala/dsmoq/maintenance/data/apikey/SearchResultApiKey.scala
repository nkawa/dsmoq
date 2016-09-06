package dsmoq.maintenance.data.apikey

import org.joda.time.DateTime

/**
 * APIキーの検索結果を表すケースクラス
 */
case class SearchResultApiKey(
  id: String,
  apiKey: String,
  secretKey: String,
  createdAt: DateTime,
  updatedAt: DateTime,
  userId: String,
  userName: String,
  userDisabled: Boolean
)
