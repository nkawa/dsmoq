package dsmoq.persistence

import org.joda.time.DateTime

import PostgresqlHelper.PgConditionSQLBuilder
import PostgresqlHelper.PgSQLSyntaxType
import scalikejdbc.DBSession
import scalikejdbc.ResultName
import scalikejdbc.ResultName
import scalikejdbc.SQLSyntax
import scalikejdbc.SQLSyntaxSupport
import scalikejdbc.SQLSyntaxSupport
import scalikejdbc.WrappedResultSet
import scalikejdbc.convertJavaSqlTimestampToConverter
import scalikejdbc.delete
import scalikejdbc.insert
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef
import scalikejdbc.scalikejdbcSQLSyntaxToStringImplicitDef
import scalikejdbc.select
import scalikejdbc.sqls
import scalikejdbc.update
import scalikejdbc.withSQL

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

  override val columns = Seq(
    "id", "user_id", "address", "status",
    "created_by", "created_at",
    "updated_by", "updated_at",
    "deleted_by", "deleted_at"
  )

  def apply(ma: ResultName[MailAddress])(rs: WrappedResultSet): MailAddress = MailAddress(
    id = rs.string(ma.id),
    userId = rs.string(ma.userId),
    address = rs.string(ma.address),
    status = rs.int(ma.status),
    createdBy = rs.string(ma.createdBy),
    createdAt = rs.timestamp(ma.createdAt).toJodaDateTime,
    updatedBy = rs.string(ma.updatedBy),
    updatedAt = rs.timestamp(ma.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(ma.deletedBy),
    deletedAt = rs.timestampOpt(ma.deletedAt).map(_.toJodaDateTime)
  )

  val ma = MailAddress.syntax("ma")

  //val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[MailAddress] = {
    withSQL {
      select.from(MailAddress as ma).where.eq(ma.id, sqls.uuid(id))
    }.map(MailAddress(ma.resultName)).single.apply()
  }

  def findByUserId(id: String)(implicit session: DBSession = autoSession): Option[MailAddress] = {
    withSQL {
      select.from(MailAddress as ma).where.eq(ma.userId, sqls.uuid(id))
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
      update(MailAddress).set(
        column.id -> sqls.uuid(entity.id),
        column.userId -> sqls.uuid(entity.userId),
        column.address -> entity.address,
        column.status -> entity.status,
        column.createdBy -> sqls.uuid(entity.createdBy),
        column.createdAt -> entity.createdAt,
        column.updatedBy -> sqls.uuid(entity.updatedBy),
        column.updatedAt -> entity.updatedAt,
        column.deletedBy -> entity.deletedBy.map(sqls.uuid),
        column.deletedAt -> entity.deletedAt
      ).where.eqUuid(column.id, entity.id)
    }.update.apply()
    entity
  }

  def destroy(entity: MailAddress)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(MailAddress).where.eq(column.id, entity.id) }.update.apply()
  }

}
