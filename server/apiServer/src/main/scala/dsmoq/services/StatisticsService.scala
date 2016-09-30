package dsmoq.services

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.joda.time.DateTime

import dsmoq.persistence
import dsmoq.services.json.StatisticsData.StatisticsDetail
import scalikejdbc.DB
import scalikejdbc.select
import scalikejdbc.withSQL

/**
 * 統計データの処理を取り扱うサービスクラス
 */
object StatisticsService {

  /**
   * 統計データを取得する。
   *
   * @param from 取得開始日時
   * @param to 取得終了日時
   * @return
   *        Success(Seq[StatisticsDetail]) 取得成功時、統計情報
   *        Failure(NullPointerException) 引数がnullの場合
   */
  def getStatistics(from: Option[DateTime], to: Option[DateTime]): Try[Seq[StatisticsDetail]] = {
    Try {
      CheckUtil.checkNull(from, "from")
      CheckUtil.checkNull(to, "to")
      DB.readOnly { implicit s =>
        val now = DateTime.now
        // 2015/10/22 -> 2015/10/1
        val from_ = from.map(x => new DateTime(x.getYear, x.getMonthOfYear, 1, 0, 0))
        // 2015/11/23 -> 2015/12/1
        val to_ = to.map(x => new DateTime(x.getYear, x.getMonthOfYear, 1, 0, 0))
        val sta = persistence.Statistics.s
        if (from.isDefined && to_.isDefined) {
          withSQL {
            select
              .from(persistence.Statistics as sta)
              .where
              .ge(sta.targetMonth, from_)
              .and
              .lt(sta.targetMonth, to_)
              .and
              .eq(sta.statisticsType, 1)
          }.map(persistence.Statistics(sta)).list.apply().map { st =>
            StatisticsDetail(
              dataset_amount = st.datasetCount,
              real_size = st.realSize,
              local_size = st.localSize,
              s3_size = st.s3Size,
              total_size = st.compressedSize
            )
          }
        } else {
          withSQL {
            select
              .from(persistence.Statistics as sta)
              .where
              .eq(sta.statisticsType, 1)
              .orderBy(sta.createdAt).desc
              .limit(1)
          }.map(persistence.Statistics(sta)).list.apply().map { st =>
            StatisticsDetail(
              dataset_amount = st.datasetCount,
              real_size = st.realSize,
              local_size = st.localSize,
              s3_size = st.s3Size,
              total_size = st.compressedSize
            )
          }
        }
      }
    }
  }
}
