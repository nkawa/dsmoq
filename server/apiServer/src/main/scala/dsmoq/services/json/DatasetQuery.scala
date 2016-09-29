package dsmoq.services.json

/**
 * カスタムクエリ情報を返却するためのJSON型
 *
 * @param id カスタムクエリID
 * @param name クエリ名
 * @param query 検索条件
 */
case class DatasetQuery(
  id: String,
  name: String,
  query: SearchDatasetCondition
)
