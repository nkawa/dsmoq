package dsmoq.services.json

import org.joda.time.DateTime

object TaskData {
  case class Task(id: String, status: Int, createdBy: String, createdAt: DateTime, updatedBy: String, updatedAt: DateTime)
  case class DatasetTask(datasets: List[Task])
  case class TaskStatus(status: Int)
}
