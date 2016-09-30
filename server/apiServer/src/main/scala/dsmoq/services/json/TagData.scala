package dsmoq.services.json

/**
 * Tag系APIのレスポンスに使用するJSON型を取りまとめるオブジェクト
 */
object TagData {
  /**
   * タグ情報を返却するためのJSON型
   *
   * @param tag タグ名
   * @param color タグの表示色
   */
  case class TagDetail(
    tag: String,
    color: String
  )
}
