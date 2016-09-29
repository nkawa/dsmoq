package dsmoq.controllers.json

import org.joda.time.DateTime

/**
 * GET /api/statisticsのリクエストに使用するJSON型のケースクラス
 *
 * @param from 検索範囲の開始日時
 * @param to 検索範囲の終了日時
 */
case class StatisticsParams(
  from: Option[DateTime] = None,
  to: Option[DateTime] = None
)
