package dsmoq.persistence

import scalikejdbc._
import scalikejdbc.SQLInterpolation._
import org.joda.time.{DateTime}
import PostgresqlHelper._

case class MailAddress(
  id: String,
  userId: String,
  address: String, 
  status: Int, 
  createdBy: String,
  createdAt: DateTime, 
  updatedBy: String,
  updatedAt: DateTime, 
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None) {

  def save()(implicit session: DBSession = MailAddress.autoSession): MailAddress = MailAddress.save(this)(session)

  def destroy()(implicit session: DBSession = MailAddress.autoSession): Unit = MailAddress.destroy(this)(session)

}
      

object MailAddress extends SQLSyntaxSupport[MailAddress] {

  override val tableName = "mail_addresses"

  override val columns = Seq("id", "user_id", "address", "status", "created_by", "created_at", "updated_by", "updated_at", "deleted_by", "deleted_at")

  def apply(ma: ResultName[MailAddress])(rs: WrappedResultSet): MailAddress = new MailAddress(
    id = rs.string(ma.id),
    userId = rs.string(ma.userId),
    address = rs.string(ma.address),
    status = rs.int(ma.status),
    createdBy = rs.string(ma.createdBy),
    createdAt = rs.timestamp(ma.createdAt).toDateTime,
    updatedBy = rs.string(ma.updatedBy),
    updatedAt = rs.timestamp(ma.updatedAt).toDateTime,
    deletedBy = rs.stringOpt(ma.deletedBy),
    deletedAt = rs.timestampOpt(ma.deletedAt).map(_.toDateTime)
  )
      
  val ma = MailAddress.syntax("ma")

  //val autoSession = AutoSession

  def find(id: Any)(implicit session: DBSession = autoSession): Option[MailAddress] = {
    withSQL { 
      select.from(MailAddress as ma).where.eq(ma.id, id)
    }.map(MailAddress(ma.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[MailAddress] = {
    withSQL(select.from(MailAddress as ma)).map(MailAddress(ma.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(MailAddress as ma)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[MailAddress] = {
    withSQL { 
      select.from(MailAddress as ma).where.append(sqls"${where}")
    }.map(MailAddress(ma.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL { 
      select(sqls"count(1)").from(MailAddress as ma).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    id: String,
    userId: String,
    address: String,
    status: Int,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None)(implicit session: DBSession = autoSession): MailAddress = {
    withSQL {
      insert.into(MailAddress).columns(
        column.id,
        column.userId,
        column.address,
        column.status,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
        sqls.uuid(id),
        sqls.uuid(userId),
        address,
        status,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(sqls.uuid),
        deletedAt
      )
    }.update.apply()

    MailAddress(
      id = id,
      userId = userId,
      address = address,
      status = status,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt)
  }

  def save(entity: MailAddress)(implicit session: DBSession = autoSession): MailAddress = {
    withSQL { 
      update(MailAddress as ma).set(
        ma.id -> entity.id,
        ma.userId -> entity.userId,
        ma.address -> entity.address,
        ma.status -> entity.status,
        ma.createdBy -> entity.createdBy,
        ma.createdAt -> entity.createdAt,
        ma.updatedBy -> entity.updatedBy,
        ma.updatedAt -> entity.updatedAt,
        ma.deletedBy -> entity.deletedBy,
        ma.deletedAt -> entity.deletedAt
      ).where.eq(ma.id, entity.id)
    }.update.apply()
    entity 
  }
        
  def destroy(entity: MailAddress)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(MailAddress).where.eq(column.id, entity.id) }.update.apply()
  }
        
}
