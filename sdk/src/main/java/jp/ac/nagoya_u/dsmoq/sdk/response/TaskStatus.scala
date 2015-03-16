package jp.ac.nagoya_u.dsmoq.sdk.response

case class TaskStatus(private val status: Int) {
  def getStatus = status
}
