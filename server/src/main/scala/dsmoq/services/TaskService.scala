package dsmoq.services

import dsmoq.persistence
import dsmoq.services.json.TaskData._
import scalikejdbc._
import dsmoq.persistence.PostgresqlHelper._

import scala.util.{Try, Success}


object TaskService {

  def getStatus(taskId: String, user: User): Try[TaskStatus] = {
    DB readOnly { implicit s =>
      val t = persistence.Task.t
      val task = withSQL {
        select
        .from(persistence.Task as t)
        .where
        .eq(t.id, sqls.uuid(taskId))
      }.map(persistence.Task(t)).single().apply().get

      Success(TaskStatus(task.status, task.createdBy, task.createdAt))
    }
  }
}
