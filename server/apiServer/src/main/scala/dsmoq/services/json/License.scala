package dsmoq.services.json

/**
 * ライセンス情報を返却するためのJSON型
 *
 * @param id ライセンスID
 * @param name ライセンス名
 */
case class License(
  id: String,
  name: String
)
