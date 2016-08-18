package dsmoq.services.json

object StatisticsData {
  case class StatisticsBase(
    statistics: Seq[StatisticsDetail])

  case class StatisticsDetail(
    dataset_amount: Long,
    real_size: Long,
    local_size: Long,
    s3_size: Long,
    total_size: Long)
}
