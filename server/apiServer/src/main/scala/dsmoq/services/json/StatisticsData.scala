package dsmoq.services.json

/**
 * Statistics系APIのレスポンスに使用するJSON型を取りまとめるオブジェクト
 */
object StatisticsData {
  /**
   * 統計情報を返却するためのJSON型
   *
   * @param statistics 統計情報一覧
   */
  case class StatisticsBase(
    statistics: Seq[StatisticsDetail]
  )

  /**
   * 統計情報の詳細
   *
   * @param dataset_amount データセット件数
   * @param real_size 非圧縮総データサイズ
   * @param local_size ローカルのデータサイズ
   * @param s3_size S3上のデータサイズ
   * @param total_size 圧縮総データサイズ
   */
  case class StatisticsDetail(
    dataset_amount: Long,
    real_size: Long,
    local_size: Long,
    s3_size: Long,
    total_size: Long
  )
}
