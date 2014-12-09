package dsmoq.services.json

import org.joda.time.DateTime

object TaskData {
  case class TaskStatus(
    status: Int,
    createBy: String,
    createAt: String
  )
}
