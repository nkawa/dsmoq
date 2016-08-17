package jp.ac.nagoya_u.dsmoq.sdk.response

case class StatisticsDetail(
    private val dataset_amount: Long,
    private val real_size: Long,
    private val local_size: Long,
    private val s3_size: Long,
    private val total_size: Long) {
  def getDatasetAmount = dataset_amount
  def getRealSize = real_size
  def getLocalSize = local_size
  def getS3Size = s3_size
  def getTotalSize = total_size
}