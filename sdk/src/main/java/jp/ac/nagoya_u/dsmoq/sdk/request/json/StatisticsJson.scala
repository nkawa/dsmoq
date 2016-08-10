package jp.ac.nagoya_u.dsmoq.sdk.request.json

import org.joda.time.DateTime

private[request] case class StatisticsJson(
  from: Option[DateTime] = None,
  to: Option[DateTime] = None) extends Jsonable
