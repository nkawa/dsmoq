package dsmoq.maintenance.views

object Pagenation {
  def pageOf(offset: Int, limit: Int): Int = {
    math.ceil(math.max(offset, 0D) / limit).toInt + 1
  }
  def minPageOf(offset: Int, limit: Int, total: Int): Int = {
    val minPageOffset = math.min(
      math.max(0, offset - limit * 2),
      math.max(0, total - limit * 5)
    )
    pageOf(minPageOffset, limit)
  }
  def maxPageOf(offset: Int, limit: Int, total: Int): Int = {
    val maxPageOffset = math.max(
      math.min(offset + limit * 2, total - limit),
      math.min(limit * 4, total - limit)
    )
    pageOf(maxPageOffset, limit)
  }
}
