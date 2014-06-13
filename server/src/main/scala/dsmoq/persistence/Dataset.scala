package dsmoq.persistence

import scalikejdbc._
import scalikejdbc.SQLInterpolation._
import org.joda.time.{DateTime}
import PostgresqlHelper._

case class Dataset(
  id: String, 
  name: String, 
  description: String, 
  licenseId: String, 
  filesCount: Int, 
  filesSize: Long, 
  createdBy: String, 
  createdAt: DateTime, 
  updatedBy: String, 
  updatedAt: DateTime, 
  deletedBy: Option[String] = None, 
  deletedAt: Option[DateTime] = None) {

  def save()(implicit session: DBSession = Dataset.autoSession): Dataset = Dataset.save(this)(session)

  def destroy()(implicit session: DBSession = Dataset.autoSession): Unit = Dataset.destroy(this)(session)

}
      

object Dataset extends SQLSyntaxSupport[Dataset] {

  override val tableName = "datasets"

  override val columns = Seq("id", "name", "description", "license_id", "files_count", "files_size", "created_by", "created_at", "updated_by", "updated_at", "deleted_by", "deleted_at")

  def apply(d: ResultName[Dataset])(rs: WrappedResultSet): Dataset = new Dataset(
    id = rs.string(d.id),
    name = rs.string(d.name),
    description = rs.string(d.description),
    licenseId = rs.string(d.licenseId),
    filesCount = rs.int(d.filesCount),
    filesSize = rs.long(d.filesSize),
    createdBy = rs.string(d.createdBy),
    createdAt = rs.timestamp(d.createdAt).toDateTime,
    updatedBy = rs.string(d.updatedBy),
    updatedAt = rs.timestamp(d.updatedAt).toDateTime,
    deletedBy = rs.stringOpt(d.deletedBy),
    deletedAt = rs.timestampOpt(d.deletedAt).map(_.toDateTime)
  )
      
  val d = Dataset.syntax("d")

  //val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[Dataset] = {
    withSQL {
      select.from(Dataset as d).where.eq(d.id, sqls.uuid(id))
    }.map(Dataset(d.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[Dataset] = {
    withSQL(select.from(Dataset as d)).map(Dataset(d.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(Dataset as d)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[Dataset] = {
    withSQL { 
      select.from(Dataset as d).where.append(sqls"${where}")
    }.map(Dataset(d.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL { 
      select(sqls"count(1)").from(Dataset as d).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    id: String,
    name: String,
    description: String,
    licenseId: String,
    filesCount: Int,
    filesSize: Long,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None)(implicit session: DBSession = autoSession): Dataset = {
    withSQL {
      insert.into(Dataset).columns(
        column.id,
        column.name,
        column.description,
        column.licenseId,
        column.filesCount,
        column.filesSize,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
        sqls.uuid(id),
        name,
        description,
        sqls.uuid(licenseId),
        filesCount,
        filesSize,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(sqls.uuid),
        deletedAt
      )
    }.update.apply()

    Dataset(
      id = id,
      name = name,
      description = description,
      licenseId = licenseId,
      filesCount = filesCount,
      filesSize = filesSize,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt)
  }

  def save(entity: Dataset)(implicit session: DBSession = autoSession): Dataset = {
    withSQL { 
      update(Dataset as d).set(
        d.id -> entity.id,
        d.name -> entity.name,
        d.description -> entity.description,
        d.licenseId -> entity.licenseId,
        d.filesCount -> entity.filesCount,
        d.filesSize -> entity.filesSize,
        d.createdBy -> entity.createdBy,
        d.createdAt -> entity.createdAt,
        d.updatedBy -> entity.updatedBy,
        d.updatedAt -> entity.updatedAt,
        d.deletedBy -> entity.deletedBy,
        d.deletedAt -> entity.deletedAt
      ).where.eq(d.id, entity.id)
    }.update.apply()
    entity 
  }
        
  def destroy(entity: Dataset)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(Dataset).where.eq(column.id, entity.id) }.update.apply()
  }
        
}
