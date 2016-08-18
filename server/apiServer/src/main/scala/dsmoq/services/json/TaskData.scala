package dsmoq.services.json

object TaskData {
  case class TaskStatus(
    status: Int,
    createBy: String,
    createAt: String)
}
