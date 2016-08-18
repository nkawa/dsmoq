package dsmoq.services.json

/**
 * Created by terurou on 2014/03/20.
 */
case class RangeSlice[A](
  summary: RangeSliceSummary,
  results: Seq[A])

case class RangeSliceSummary(
  total: Int,
  count: Int = 20,
  offset: Int = 0)
