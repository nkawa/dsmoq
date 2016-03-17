package dsmoq.persistence

import scalikejdbc._
import scalikejdbc.SQLInterpolation._
import org.joda.time.{DateTime}
import PostgresqlHelper._

case class License(
  id: String, 
  name: String, 
  displayOrder: Int, 
  createdBy: String, 
  createdAt: DateTime, 
  updatedBy: String, 
  updatedAt: DateTime, 
  deletedBy: Option[String] = None, 
  deletedAt: Option[DateTime] = None) {

  def save()(implicit session: DBSession = License.autoSession): License = License.save(this)(session)

  def destroy()(implicit session: DBSession = License.autoSession): Unit = License.destroy(this)(session)

}
      

object License extends SQLSyntaxSupport[License] {

  override val tableName = "licenses"

  override val columns = Seq("id", "name", "display_order", "created_by", "created_at", "updated_by", "updated_at", "deleted_by", "deleted_at")

  def apply(l: ResultName[License])(rs: WrappedResultSet): License = new License(
    id = rs.string(l.id),
    name = rs.string(l.name),
    displayOrder = rs.int(l.displayOrder),
    createdBy = rs.string(l.createdBy),
    createdAt = rs.timestamp(l.createdAt).toJodaDateTime,
    updatedBy = rs.string(l.updatedBy),
    updatedAt = rs.timestamp(l.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(l.deletedBy),
    deletedAt = rs.timestampOpt(l.deletedAt).map(_.toJodaDateTime)
  )
      
  val l = License.syntax("l")

  override val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[License] = {
    withSQL { 
      select.from(License as l).where.eq(l.id, sqls.uuid(id))
    }.map(License(l.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[License] = {
    withSQL(select.from(License as l)).map(License(l.resultName)).list.apply()
  }

  def findOrderedAll()(implicit session: DBSession = autoSession): List[License] = {
    withSQL(select.from(License as l).orderBy(l.displayOrder)).map(License(l.resultName)).list.apply()
  }
  
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(License as l)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[License] = {
    withSQL { 
      select.from(License as l).where.append(sqls"${where}")
    }.map(License(l.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL { 
      select(sqls"count(1)").from(License as l).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    id: String,
    name: String,
    displayOrder: Int,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None)(implicit session: DBSession = autoSession): License = {
    withSQL {
      insert.into(License).columns(
        column.id,
        column.name,
        column.displayOrder,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
        sqls.uuid(id),
        name,
        displayOrder,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(sqls.uuid),
        deletedAt
      )
    }.update.apply()

    License(
      id = id,
      name = name,
      displayOrder = displayOrder,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt)
  }

  def save(entity: License)(implicit session: DBSession = autoSession): License = {
    withSQL { 
      update(License).set(
        column.id -> sqls.uuid(entity.id),
        column.name -> entity.name,
        column.displayOrder -> entity.displayOrder,
        column.createdBy -> sqls.uuid(entity.createdBy),
        column.createdAt -> entity.createdAt,
        column.updatedBy -> sqls.uuid(entity.updatedBy),
        column.updatedAt -> entity.updatedAt,
        column.deletedBy -> entity.deletedBy.map(sqls.uuid),
        column.deletedAt -> entity.deletedAt
      ).where.eq(column.id, entity.id)
    }.update.apply()
    entity 
  }
        
  def destroy(entity: License)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(License).where.eq(column.id, entity.id) }.update.apply()
  }
        
}
