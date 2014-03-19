package dsmoq.models

import scalikejdbc._
import scalikejdbc.SQLInterpolation._
import org.joda.time.{DateTime}
import PostgresqlHelper._

case class Dataset(
  id: String,
  name: String, 
  description: String, 
  licenseId: Any, 
  defaultAccessLevel: Int, 
  createdAt: DateTime, 
  updatedAt: DateTime, 
  deletedAt: Option[DateTime] = None, 
  createdBy: Any, 
  updatedBy: Any, 
  deletedBy: Option[Any] = None) {

  def save()(implicit session: DBSession = Dataset.autoSession): Dataset = Dataset.save(this)(session)

  def destroy()(implicit session: DBSession = Dataset.autoSession): Unit = Dataset.destroy(this)(session)

}
      

object Dataset extends SQLSyntaxSupport[Dataset] {

  override val tableName = "datasets"

  override val columns = Seq("id", "name", "description", "license_id", "default_access_level", "created_at", "updated_at", "deleted_at", "created_by", "updated_by", "deleted_by")

  def apply(d: ResultName[Dataset])(rs: WrappedResultSet): Dataset = new Dataset(
    id = rs.string(d.id),
    name = rs.string(d.name),
    description = rs.string(d.description),
    licenseId = rs.any(d.licenseId),
    defaultAccessLevel = rs.int(d.defaultAccessLevel),
    createdAt = rs.timestamp(d.createdAt).toDateTime,
    updatedAt = rs.timestamp(d.updatedAt).toDateTime,
    deletedAt = rs.timestampOpt(d.deletedAt).map(_.toDateTime),
    createdBy = rs.any(d.createdBy),
    updatedBy = rs.any(d.updatedBy),
    deletedBy = rs.anyOpt(d.deletedBy)
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
    licenseId: Any,
    defaultAccessLevel: Int,
    createdAt: DateTime,
    updatedAt: DateTime,
    deletedAt: Option[DateTime] = None,
    createdBy: Any,
    updatedBy: Any,
    deletedBy: Option[Any] = None)(implicit session: DBSession = autoSession): Dataset = {
    withSQL {
      insert.into(Dataset).columns(
        column.id,
        column.name,
        column.description,
        column.licenseId,
        column.defaultAccessLevel,
        column.createdAt,
        column.updatedAt,
        column.deletedAt,
        column.createdBy,
        column.updatedBy,
        column.deletedBy
      ).values(
        id,
        name,
        description,
        licenseId,
        defaultAccessLevel,
        createdAt,
        updatedAt,
        deletedAt,
        createdBy,
        updatedBy,
        deletedBy
      )
    }.update.apply()

    Dataset(
      id = id,
      name = name,
      description = description,
      licenseId = licenseId,
      defaultAccessLevel = defaultAccessLevel,
      createdAt = createdAt,
      updatedAt = updatedAt,
      deletedAt = deletedAt,
      createdBy = createdBy,
      updatedBy = updatedBy,
      deletedBy = deletedBy)
  }

  def save(entity: Dataset)(implicit session: DBSession = autoSession): Dataset = {
    withSQL { 
      update(Dataset as d).set(
        d.id -> entity.id,
        d.name -> entity.name,
        d.description -> entity.description,
        d.licenseId -> entity.licenseId,
        d.defaultAccessLevel -> entity.defaultAccessLevel,
        d.createdAt -> entity.createdAt,
        d.updatedAt -> entity.updatedAt,
        d.deletedAt -> entity.deletedAt,
        d.createdBy -> entity.createdBy,
        d.updatedBy -> entity.updatedBy,
        d.deletedBy -> entity.deletedBy
      ).where.eq(d.id, entity.id)
    }.update.apply()
    entity 
  }
        
  def destroy(entity: Dataset)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(Dataset).where.eq(column.id, entity.id) }.update.apply()
  }
        
}
