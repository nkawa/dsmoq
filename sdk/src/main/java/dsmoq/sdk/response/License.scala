package dsmoq.sdk.response

case class License(private val id: String, private val name: String) {
  def getId = id
  def getNam = name
}
