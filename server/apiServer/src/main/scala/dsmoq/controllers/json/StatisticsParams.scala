package dsmoq.controllers.json

import org.joda.time.DateTime

case class StatisticsParams(
  from: Option[DateTime] = None,
  to: Option[DateTime] = None)
