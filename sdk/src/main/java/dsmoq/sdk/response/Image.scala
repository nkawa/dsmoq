package dsmoq.sdk.response

case class Image (
  private val id: String,
  private val url: String
) {
  def getId = id
  def getUrl = url
}
