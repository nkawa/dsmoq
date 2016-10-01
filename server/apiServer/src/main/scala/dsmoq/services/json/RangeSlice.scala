package dsmoq.services.json

/**
 * 検索結果を返却するためのJSON型
 *
 * @param summary 検索結果のサマリ情報
 * @param results 検索結果
 */
case class RangeSlice[A](
  summary: RangeSliceSummary,
  results: Seq[A]
)

/**
 * 検索結果のサマリ情報
 *
 * @param total 総取得件数
 * @param count 取得件数
 * @param offset 取得位置
 */
case class RangeSliceSummary(
  total: Int,
  count: Int = 20,
  offset: Int = 0
)
