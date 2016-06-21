package dsmoq.services

import dsmoq.persistence
import dsmoq.services.json.TaskData._
import scalikejdbc._

import scala.util.{Failure, Try, Success}

object TaskService {

  def getStatus(taskId: String) = {
    try {
      DB readOnly { implicit s =>
        val task = persistence.Task.find(taskId).get
        Success(TaskStatus(task.status, task.createdBy, task.createdAt.toString()))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }
}
