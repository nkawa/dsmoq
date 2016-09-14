package dsmoq.maintenance.views

/**
 * ページング部を表すケースクラス
 *
 * @param first 最初のページ
 * @param prev 1つ前のページ
 * @param pages 前後のページ
 * @param next 1つ後のページ
 * @param last 最後のページ
 */
case class Pagenation(
  first: Pagenation.Page,
  prev: Pagenation.Page,
  pages: Seq[Pagenation.Page],
  next: Pagenation.Page,
  last: Pagenation.Page
)

/**
 * ページング部を表すケースクラスのコンパニオンオブジェクト
 */
object Pagenation {
  /**
   * ページングの1ページを表すケースクラス
   *
   * @param num ページ番号
   * @param enabled ページが有効な場合true、無効な場合false
   */
  case class Page(
    num: Int,
    enabled: Boolean
  )

  /**
   * ページング部を作成する。
   *
   * @param page 現在のページ番号
   * @param limit 検索上限
   * @param total 検索結果の総件数
   * @return ページング部
   */
  def apply(page: Int, limit: Int, total: Int): Pagenation = {
    val lastPage = (total / limit) + math.min(total % limit, 1)
    val currentPage = math.max(math.min(page, lastPage), 1)
    val from = math.max(currentPage + math.min(-2, -4 + lastPage - currentPage), 1)
    val to = math.min(currentPage + math.max(5 - currentPage, 2), lastPage)
    Pagenation(
      Page(1, currentPage > 1),
      Page(currentPage - 1, currentPage > 1),
      (from to to).map { p =>
        Page(p, p != currentPage)
      },
      Page(currentPage + 1, currentPage < lastPage),
      Page(lastPage, currentPage < lastPage)
    )
  }
}
