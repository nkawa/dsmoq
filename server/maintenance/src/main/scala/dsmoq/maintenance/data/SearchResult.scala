package dsmoq.maintenance.data

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

/**
 * 検索結果を表すケースクラスのコンパニオンオブジェクト
 */
object SearchResult {

  /**
   * 検索結果を表すケースクラスを取得する。
   *
   * @tparam T 検索結果の型
   * @param offset 検索結果データの取得位置
   * @param limit 検索結果データの最大取得件数
   * @param total 検索結果の総件数
   * @param data 検索結果データ
   */
  def apply[T](offset: Int, limit: Int, total: Int, data: Seq[T]): SearchResult[T] = {
    SearchResult(
      from = offset + 1,
      to = offset + data.length,
      lastPage = (total / limit) + math.min(total % limit, 1),
      total = total,
      data = data
    )
  }
}
