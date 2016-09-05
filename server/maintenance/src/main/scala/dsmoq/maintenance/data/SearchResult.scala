package dsmoq.maintenance.data

import org.joda.time.DateTime

case class SearchResult[T](
  from: Int,
  to: Int,
  total: Int,
  data: Seq[T]
)
