package dsmoq.services.json

/**
 * 画像情報を返却するためのJSON型
 *
 * @param id 画像ID
 * @param url 画像URL
 */
case class Image(
  id: String,
  url: String
)
