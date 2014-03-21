package dsmoq.persistence

import scalikejdbc._
import scalikejdbc.SQLInterpolation._
import org.joda.time.{DateTime}
import PostgresqlHelper._

case class File(
  id: String,
  datasetId: String,
  name: String, 
  description: String, 
  createdBy: String,
  createdAt: DateTime, 
  updatedBy: String,
  updatedAt: DateTime, 
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None) {

  def save()(implicit session: DBSession = File.autoSession): File = File.save(this)(session)

  def destroy()(implicit session: DBSession = File.autoSession): Unit = File.destroy(this)(session)

}
      

object File extends SQLSyntaxSupport[File] {

  override val tableName = "files"

  override val columns = Seq("id", "dataset_id", "name", "description", "created_by", "created_at", "updated_by", "updated_at", "deleted_by", "deleted_at")

  def apply(f: ResultName[File])(rs: WrappedResultSet): File = new File(
    id = rs.string(f.id),
    datasetId = rs.string(f.datasetId),
    name = rs.string(f.name),
    description = rs.string(f.description),
    createdBy = rs.string(f.createdBy),
    createdAt = rs.timestamp(f.createdAt).toDateTime,
    updatedBy = rs.string(f.updatedBy),
    updatedAt = rs.timestamp(f.updatedAt).toDateTime,
    deletedBy = rs.stringOpt(f.deletedBy),
    deletedAt = rs.timestampOpt(f.deletedAt).map(_.toDateTime)
  )
      
  val f = File.syntax("f")

  //val autoSession = AutoSession

  def find(id: Any)(implicit session: DBSession = autoSession): Option[File] = {
    withSQL { 
      select.from(File as f).where.eq(f.id, id)
    }.map(File(f.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[File] = {
    withSQL(select.from(File as f)).map(File(f.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(File as f)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[File] = {
    withSQL { 
      select.from(File as f).where.append(sqls"${where}")
    }.map(File(f.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL { 
      select(sqls"count(1)").from(File as f).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    id: String,
    datasetId: String,
    name: String,
    description: String,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None)(implicit session: DBSession = autoSession): File = {
    withSQL {
      insert.into(File).columns(
        column.id,
        column.datasetId,
        column.name,
        column.description,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
        sqls.uuid(id),
        sqls.uuid(datasetId),
        name,
        description,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(x => sqls.uuid(x)),
        deletedAt
      )
    }.update.apply()

    File(
      id = id,
      datasetId = datasetId,
      name = name,
      description = description,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt)
  }

  def save(entity: File)(implicit session: DBSession = autoSession): File = {
    withSQL { 
      update(File as f).set(
        f.id -> entity.id,
        f.datasetId -> entity.datasetId,
        f.name -> entity.name,
        f.description -> entity.description,
        f.createdBy -> entity.createdBy,
        f.createdAt -> entity.createdAt,
        f.updatedBy -> entity.updatedBy,
        f.updatedAt -> entity.updatedAt,
        f.deletedBy -> entity.deletedBy,
        f.deletedAt -> entity.deletedAt
      ).where.eq(f.id, entity.id)
    }.update.apply()
    entity 
  }
        
  def destroy(entity: File)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(File).where.eq(column.id, entity.id) }.update.apply()
  }
        
}
