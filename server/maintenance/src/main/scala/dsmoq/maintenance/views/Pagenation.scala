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
   * @param offset 検索オフセット
   * @param enabled ページが有効な場合true、無効な場合false
   */
  case class Page(
    num: Int,
    offset: Int,
    enabled: Boolean
  )

  /**
   * ページング部を作成する。
   *
   * @param offset 現在のページの検索オフセット
   * @param limit 検索上限
   * @param total 検索結果の総件数
   * @return ページング部
   */
  def apply(offset: Int, limit: Int, total: Int): Pagenation = {
    val page = pageOf(offset, limit)
    val lastOffset = lastOffsetOf(offset, limit, total)
    val lastPage = pageOf(lastOffset, limit)
    val from = minPageOf(offset, limit, total)
    val to = maxPageOf(offset, limit, total)
    Pagenation(
      Page(1, 0, page > 1),
      Page(page - 1, math.max(0, offset - limit), page > 1),
      (from to to).map { p =>
        Page(p, math.max(0, offset + limit * (p - page)), p != page)
      },
      Page(page + 1, offset + limit, page < lastPage),
      Page(lastPage, lastOffset, page < lastPage)
    )
  }

  /**
   * 検索オフセットからページ番号を取得する。
   *
   * @param offset 現在のページの検索オフセット
   * @param limit 検索上限
   * @return ページ番号
   */
  def pageOf(offset: Int, limit: Int): Int = {
    math.ceil(math.max(offset, 0D) / limit).toInt + 1
  }

  /**
   * 検索オフセットから最後のページ番号を取得する。
   *
   * @param offset 現在のページの検索オフセット
   * @param limit 検索上限
   * @param total 検索結果の総件数
   * @return 最後のページ番号
   */
  def lastOffsetOf(offset: Int, limit: Int, total: Int): Int = {
    offset + limit * ((total - offset - 1) / limit)
  }

  /**
   * 検索オフセットから前後ページの最初のページ番号を取得する。
   *
   * @param offset 現在のページの検索オフセット
   * @param limit 検索上限
   * @param total 検索結果の総件数
   * @return 前後ページの最初のページ番号
   */
  def minPageOf(offset: Int, limit: Int, total: Int): Int = {
    val lastOffset = lastOffsetOf(offset, limit, total)
    val minPageOffset = math.max(0, math.min(offset - limit * 2, lastOffset - limit * 4))
    pageOf(minPageOffset, limit)
  }

  /**
   * 検索オフセットから前後ページの最後のページ番号を取得する。
   *
   * @param offset 現在のページの検索オフセット
   * @param limit 検索上限
   * @param total 検索結果の総件数
   * @return 前後ページの最後のページ番号
   */
  def maxPageOf(offset: Int, limit: Int, total: Int): Int = {
    val lastOffset = lastOffsetOf(offset, limit, total)
    val maxPageOffset = math.min(math.max(offset + limit * 2, limit * 4), lastOffset)
    pageOf(maxPageOffset, limit)
  }
}
