package jp.ac.nagoya_u.dsmoq.sdk.response

case class Response[A] (private val status: String, private val data: A) {
  def getStatus = status
  def getData = data
}
