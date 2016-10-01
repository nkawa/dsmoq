package dsmoq.services.json

/**
 * Task系APIのレスポンスに使用するJSON型を取りまとめるオブジェクト
 */
object TaskData {
  /**
   * タスク情報を返却するためのJSON型
   *
   * @param status タスクの実行ステータス
   * @param createBy 作成者
   * @param createAt 作成日時
   */
  case class TaskStatus(
    status: Int,
    createBy: String,
    createAt: String
  )
}
