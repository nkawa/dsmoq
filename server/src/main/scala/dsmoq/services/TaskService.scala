package dsmoq.services

import dsmoq.services.json.TaskData._


object TaskService {

  def getDatasetTasks(datasetId: String, limit: Option[Int], user: User): DatasetTask = {
    DatasetTask(List())
  }

  def getStatus(taskId: String, user: User): TaskStatus = {
    TaskStatus(0)
  }
}
