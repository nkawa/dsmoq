package dsmoq.services

import dsmoq.persistence
import dsmoq.services.json.TaskData._
import scalikejdbc._
import dsmoq.persistence.PostgresqlHelper._


object TaskService {

  def getStatus(taskId: String, user: User): TaskStatus = {
    DB readOnly { implicit s =>
      val t = persistence.Task.t
      val status = withSQL {
        select(t.result.status)
        .from(persistence.Task as t)
        .where
        .eq(t.id, sqls.uuid(taskId))
      }.map(rs => rs.int(t.resultName.status)).single().apply().getOrElse(0)

      TaskStatus(status)
    }
  }
}
