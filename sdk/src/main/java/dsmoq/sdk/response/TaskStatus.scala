package dsmoq.sdk.response

case class TaskStatus(private val status: Int) {
  def getStatus = status
}
