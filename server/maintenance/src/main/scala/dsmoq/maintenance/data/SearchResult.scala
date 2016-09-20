package dsmoq.maintenance.data

import org.joda.time.DateTime

/**
 * 検索結果を表すケースクラス
 *
 * @tparam T 検索結果の型
 * @param from 検索結果データの先頭の件番
 * @param to 検索結果データの末尾の件番
 * @param lastPage 検索結果データの最終ページ
 * @param total 検索結果の総件数
 * @param data 検索結果データ
 */
case class SearchResult[T](
  from: Int,
  to: Int,
  lastPage: Int,
  total: Int,
  data: Seq[T]
)
