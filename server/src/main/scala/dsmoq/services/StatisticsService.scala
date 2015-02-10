package dsmoq.services

import dsmoq.persistence
import dsmoq.services.json.StatisticsData._
import org.joda.time.DateTime
import scalikejdbc.DB
import scalikejdbc._
import scala.util.{Success, Failure, Try}

object StatisticsService {

  def getStatistics(from: Option[DateTime], to: Option[DateTime]):Try[Seq[StatisticsDetail]] = {
    try {
      DB readOnly { implicit s =>
        val now = DateTime.now
        // 2015/10/22 -> 2015/10/1
        val from_ = from.map(x => new DateTime(x.getYear, x.getMonthOfYear, 1, 0, 0)).getOrElse(new DateTime(now.getYear, now.getMonthOfYear, 1, 0, 0).minusMonths(1))
        // 2015/11/23 -> 2015/12/1
        val to_ = to.map(x => new DateTime(x.getYear, x.getMonthOfYear, 1, 0, 0)).getOrElse(new DateTime(now.getYear, now.getMonthOfYear, 1, 0, 0))
        println(from_)
        println(to_)
        val sta = persistence.Statistics.s
        val stats = withSQL {
          select
            .from(persistence.Statistics as sta)
            .where
              .ge(sta.targetMonth, from_)
              .and
              .lt(sta.targetMonth, to_)
        }.map(persistence.Statistics(sta)).list.apply().map { st =>
          StatisticsDetail(
            dataset_amount = st.datasetCount,
            real_size = st.realSize,
            local_size = st.localSize,
            s3_size = st.s3Size,
            total_size = st.compressedSize
          )
        }
        Success(stats)
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }
}
