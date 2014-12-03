package dsmoq.persistence

import scalikejdbc._
import org.joda.time.{DateTime}
import PostgresqlHelper._

case class TaskLog(
  id: String,
  taskId: String,
  logType: Int, 
  message: String, 
  createdBy: String,
  createdAt: DateTime) {

  def save()(implicit session: DBSession = TaskLog.autoSession): TaskLog = TaskLog.save(this)(session)

  def destroy()(implicit session: DBSession = TaskLog.autoSession): Unit = TaskLog.destroy(this)(session)

}
      

object TaskLog extends SQLSyntaxSupport[TaskLog] {

  override val tableName = "task_log"

  override val columns = Seq("id", "task_id", "log_type", "message", "created_by", "created_at")

  def apply(tl: SyntaxProvider[TaskLog])(rs: WrappedResultSet): TaskLog = apply(tl.resultName)(rs)
  def apply(tl: ResultName[TaskLog])(rs: WrappedResultSet): TaskLog = new TaskLog(
    id = rs.string(tl.id),
    taskId = rs.string(tl.taskId),
    logType = rs.int(tl.logType),
    message = rs.string(tl.message),
    createdBy = rs.string(tl.createdBy),
    createdAt = rs.timestamp(tl.createdAt).toJodaDateTime
  )
      
  val tl = TaskLog.syntax("tl")

  override val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[TaskLog] = {
    withSQL {
      select.from(TaskLog as tl).where.eq(tl.id, sqls.uuid(id))
    }.map(TaskLog(tl.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[TaskLog] = {
    withSQL(select.from(TaskLog as tl)).map(TaskLog(tl.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(TaskLog as tl)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[TaskLog] = {
    withSQL { 
      select.from(TaskLog as tl).where.append(sqls"${where}")
    }.map(TaskLog(tl.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL { 
      select(sqls"count(1)").from(TaskLog as tl).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    id: String,
    taskId: String,
    logType: Int,
    message: String,
    createdBy: String,
    createdAt: DateTime)(implicit session: DBSession = autoSession): TaskLog = {
    withSQL {
      insert.into(TaskLog).columns(
        column.id,
        column.taskId,
        column.logType,
        column.message,
        column.createdBy,
        column.createdAt
      ).values(
        sqls.uuid(id),
        sqls.uuid(taskId),
        logType,
        message,
        sqls.uuid(createdBy),
        createdAt
      )
    }.update.apply()

    TaskLog(
      id = id,
      taskId = taskId,
      logType = logType,
      message = message,
      createdBy = createdBy,
      createdAt = createdAt)
  }

  def save(entity: TaskLog)(implicit session: DBSession = autoSession): TaskLog = {
    withSQL {
      update(TaskLog).set(
        column.id -> entity.id,
        column.taskId -> entity.taskId,
        column.logType -> entity.logType,
        column.message -> entity.message,
        column.createdBy -> entity.createdBy,
        column.createdAt -> entity.createdAt
      ).where.eq(column.id, entity.id)
    }.update.apply()
    entity
  }
        
  def destroy(entity: TaskLog)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(TaskLog).where.eq(column.id, entity.id) }.update.apply()
  }
        
}
