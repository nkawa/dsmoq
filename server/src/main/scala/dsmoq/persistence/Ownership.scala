package dsmoq.persistence

import scalikejdbc._
import scalikejdbc.SQLInterpolation._
import org.joda.time.{DateTime}
import PostgresqlHelper._

case class Ownership(
  id: String,
  datasetId: String,
  groupId: String,
  accessLevel: Int, 
  createdBy: String,
  createdAt: DateTime, 
  updatedBy: String,
  updatedAt: DateTime,
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None) {

  def save()(implicit session: DBSession = Ownership.autoSession): Ownership = Ownership.save(this)(session)

  def destroy()(implicit session: DBSession = Ownership.autoSession): Unit = Ownership.destroy(this)(session)

}
      

object Ownership extends SQLSyntaxSupport[Ownership] {

  override val tableName = "ownerships"

  override val columns = Seq("id", "dataset_id", "group_id", "access_level", "created_by", "created_at", "updated_by", "updated_at", "deleted_by", "deleted_at")

  def apply(o: ResultName[Ownership])(rs: WrappedResultSet): Ownership = new Ownership(
    id = rs.string(o.id),
    datasetId = rs.string(o.datasetId),
    groupId = rs.string(o.groupId),
    accessLevel = rs.int(o.accessLevel),
    createdBy = rs.string(o.createdBy),
    createdAt = rs.timestamp(o.createdAt).toDateTime,
    updatedBy = rs.string(o.updatedBy),
    updatedAt = rs.timestamp(o.updatedAt).toDateTime,
    deletedBy = rs.stringOpt(o.deletedBy),
    deletedAt = rs.timestampOpt(o.deletedAt).map(_.toDateTime)
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
    groupId: String,
    accessLevel: Int,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None)(implicit session: DBSession = autoSession): Ownership = {
    withSQL {
      insert.into(Ownership).columns(
        column.id,
        column.datasetId,
        column.groupId,
        column.accessLevel,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
        sqls.uuid(id),
        sqls.uuid(datasetId),
        sqls.uuid(groupId),
        accessLevel,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(sqls.uuid),
        deletedAt
      )
    }.update.apply()

    Ownership(
      id = id,
      datasetId = datasetId,
      groupId = groupId,
      accessLevel = accessLevel,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt)
  }

  def save(entity: Ownership)(implicit session: DBSession = autoSession): Ownership = {
    withSQL { 
      update(Ownership as o).set(
        o.id -> entity.id,
        o.datasetId -> entity.datasetId,
        o.groupId -> entity.groupId,
        o.accessLevel -> entity.accessLevel,
        o.createdBy -> entity.createdBy,
        o.createdAt -> entity.createdAt,
        o.updatedBy -> entity.updatedBy,
        o.updatedAt -> entity.updatedAt,
        o.deletedBy -> entity.deletedBy,
        o.deletedAt -> entity.deletedAt
      ).where.eq(o.id, entity.id)
    }.update.apply()
    entity 
  }
        
  def destroy(entity: Ownership)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(Ownership).where.eq(column.id, entity.id) }.update.apply()
  }
        
}
