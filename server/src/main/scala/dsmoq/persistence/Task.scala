package dsmoq.persistence

import scalikejdbc._
import org.joda.time.{DateTime}
import PostgresqlHelper._

case class Task(
  id: String,
  taskType: Int, 
  parameter: String, 
  status: Int,
  executeAt: DateTime,
  createdBy: String,
  createdAt: DateTime, 
  updatedBy: String,
  updatedAt: DateTime) {

  def save()(implicit session: DBSession = Task.autoSession): Task = Task.save(this)(session)

  def destroy()(implicit session: DBSession = Task.autoSession): Unit = Task.destroy(this)(session)

}
      

object Task extends SQLSyntaxSupport[Task] {

  override val tableName = "tasks"

  override val columns = Seq("id", "task_type", "parameter", "status", "execute_at", "created_by", "created_at", "updated_by", "updated_at")

  def apply(t: SyntaxProvider[Task])(rs: WrappedResultSet): Task = apply(t.resultName)(rs)
  def apply(t: ResultName[Task])(rs: WrappedResultSet): Task = new Task(
    id = rs.string(t.id),
    taskType = rs.int(t.taskType),
    parameter = rs.string(t.parameter),
    status = rs.int(t.status),
    executeAt = rs.timestamp(t.executeAt).toJodaDateTime,
    createdBy = rs.string(t.createdBy),
    createdAt = rs.timestamp(t.createdAt).toJodaDateTime,
    updatedBy = rs.string(t.updatedBy),
    updatedAt = rs.timestamp(t.updatedAt).toJodaDateTime
  )
      
  val t = Task.syntax("t")

 // override val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[Task] = {
    withSQL {
      select.from(Task as t).where.eq(t.id, sqls.uuid(id))
    }.map(Task(t.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[Task] = {
    withSQL(select.from(Task as t)).map(Task(t.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(Task as t)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[Task] = {
    withSQL { 
      select.from(Task as t).where.append(sqls"${where}")
    }.map(Task(t.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL { 
      select(sqls"count(1)").from(Task as t).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    id: String,
    taskType: Int,
    parameter: String,
    status: Int,
    executeAt: DateTime,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime)(implicit session: DBSession = autoSession): Task = {
    withSQL {
      insert.into(Task).columns(
        column.id,
        column.taskType,
        column.parameter,
        column.status,
        column.executeAt,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt
      ).values(
        sqls.uuid(id),
        taskType,
        parameter,
        status,
        executeAt,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt
      )
    }.update.apply()

    Task(
      id = id,
      taskType = taskType,
      parameter = parameter,
      status = status,
      executeAt = executeAt,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt)
  }

  def save(entity: Task)(implicit session: DBSession = autoSession): Task = {
    withSQL {
      update(Task).set(
        column.id -> sqls.uuid(entity.id),
        column.taskType -> entity.taskType,
        column.parameter -> entity.parameter,
        column.status -> entity.status,
        column.executeAt -> entity.executeAt,
        column.createdBy -> sqls.uuid(entity.createdBy),
        column.createdAt -> entity.createdAt,
        column.updatedBy -> sqls.uuid(entity.updatedBy),
        column.updatedAt -> entity.updatedAt
      ).where.eq(column.id, sqls.uuid(entity.id))
    }.update.apply()
    entity
  }
        
  def destroy(entity: Task)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(Task).where.eq(column.id, sqls.uuid(entity.id)) }.update.apply()
  }
        
}
