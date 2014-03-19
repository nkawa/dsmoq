package dsmoq.models

import scalikejdbc._
import scalikejdbc.SQLInterpolation._
import org.joda.time.{DateTime}

case class Ownership(
  id: String,
  datasetId: String,
  ownerType: Int, 
  ownerId: String,
  accessLevel: Int, 
  createdAt: DateTime, 
  updatedAt: DateTime, 
  deletedAt: Option[DateTime] = None, 
  createdBy: String,
  updatedBy: String,
  deletedBy: Option[String] = None) {

  def save()(implicit session: DBSession = Ownership.autoSession): Ownership = Ownership.save(this)(session)

  def destroy()(implicit session: DBSession = Ownership.autoSession): Unit = Ownership.destroy(this)(session)

}
      

object Ownership extends SQLSyntaxSupport[Ownership] {

  override val tableName = "ownerships"

  override val columns = Seq("id", "dataset_id", "owner_type", "owner_id", "access_level", "created_at", "updated_at", "deleted_at", "created_by", "updated_by", "deleted_by")

  def apply(o: ResultName[Ownership])(rs: WrappedResultSet): Ownership = new Ownership(
    id = rs.string(o.id),
    datasetId = rs.string(o.datasetId),
    ownerType = rs.int(o.ownerType),
    ownerId = rs.string(o.ownerId),
    accessLevel = rs.int(o.accessLevel),
    createdAt = rs.timestamp(o.createdAt).toDateTime,
    updatedAt = rs.timestamp(o.updatedAt).toDateTime,
    deletedAt = rs.timestampOpt(o.deletedAt).map(_.toDateTime),
    createdBy = rs.string(o.createdBy),
    updatedBy = rs.string(o.updatedBy),
    deletedBy = rs.stringOpt(o.deletedBy)
  )
      
  val o = Ownership.syntax("o")

  //val autoSession = AutoSession

  def find(id: Any)(implicit session: DBSession = autoSession): Option[Ownership] = {
    withSQL { 
      select.from(Ownership as o).where.eq(o.id, id)
    }.map(Ownership(o.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[Ownership] = {
    withSQL(select.from(Ownership as o)).map(Ownership(o.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(Ownership as o)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[Ownership] = {
    withSQL { 
      select.from(Ownership as o).where.append(sqls"${where}")
    }.map(Ownership(o.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL { 
      select(sqls"count(1)").from(Ownership as o).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    id: String,
    datasetId: String,
    ownerType: Int,
    ownerId: String,
    accessLevel: Int,
    createdAt: DateTime,
    updatedAt: DateTime,
    deletedAt: Option[DateTime] = None,
    createdBy: String,
    updatedBy: String,
    deletedBy: Option[String] = None)(implicit session: DBSession = autoSession): Ownership = {
    withSQL {
      insert.into(Ownership).columns(
        column.id,
        column.datasetId,
        column.ownerType,
        column.ownerId,
        column.accessLevel,
        column.createdAt,
        column.updatedAt,
        column.deletedAt,
        column.createdBy,
        column.updatedBy,
        column.deletedBy
      ).values(
        id,
        datasetId,
        ownerType,
        ownerId,
        accessLevel,
        createdAt,
        updatedAt,
        deletedAt,
        createdBy,
        updatedBy,
        deletedBy
      )
    }.update.apply()

    Ownership(
      id = id,
      datasetId = datasetId,
      ownerType = ownerType,
      ownerId = ownerId,
      accessLevel = accessLevel,
      createdAt = createdAt,
      updatedAt = updatedAt,
      deletedAt = deletedAt,
      createdBy = createdBy,
      updatedBy = updatedBy,
      deletedBy = deletedBy)
  }

  def save(entity: Ownership)(implicit session: DBSession = autoSession): Ownership = {
    withSQL { 
      update(Ownership as o).set(
        o.id -> entity.id,
        o.datasetId -> entity.datasetId,
        o.ownerType -> entity.ownerType,
        o.ownerId -> entity.ownerId,
        o.accessLevel -> entity.accessLevel,
        o.createdAt -> entity.createdAt,
        o.updatedAt -> entity.updatedAt,
        o.deletedAt -> entity.deletedAt,
        o.createdBy -> entity.createdBy,
        o.updatedBy -> entity.updatedBy,
        o.deletedBy -> entity.deletedBy
      ).where.eq(o.id, entity.id)
    }.update.apply()
    entity 
  }
        
  def destroy(entity: Ownership)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(Ownership).where.eq(column.id, entity.id) }.update.apply()
  }
        
}
