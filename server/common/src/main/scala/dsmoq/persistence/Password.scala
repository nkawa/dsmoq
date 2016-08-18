package dsmoq.persistence

import org.joda.time.DateTime

import dsmoq.persistence.PostgresqlHelper.PgSQLSyntaxType
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

case class Password(
    id: String,
    userId: String,
    hash: String,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None) {

  def save()(implicit session: DBSession = Password.autoSession): Password = Password.save(this)(session)

  def destroy()(implicit session: DBSession = Password.autoSession): Unit = Password.destroy(this)(session)

}

object Password extends SQLSyntaxSupport[Password] {

  override val tableName = "passwords"

  override val columns = Seq(
    "id", "user_id", "hash",
    "created_by", "created_at",
    "updated_by", "updated_at",
    "deleted_by", "deleted_at"
  )

  def apply(p: ResultName[Password])(rs: WrappedResultSet): Password = Password(
    id = rs.string(p.id),
    userId = rs.string(p.userId),
    hash = rs.string(p.hash),
    createdBy = rs.string(p.createdBy),
    createdAt = rs.timestamp(p.createdAt).toJodaDateTime,
    updatedBy = rs.string(p.updatedBy),
    updatedAt = rs.timestamp(p.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(p.deletedBy),
    deletedAt = rs.timestampOpt(p.deletedAt).map(_.toJodaDateTime)
  )

  val p = Password.syntax("p")

  //val autoSession = AutoSession

  def find(id: Any)(implicit session: DBSession = autoSession): Option[Password] = {
    withSQL {
      select.from(Password as p).where.eq(p.id, id)
    }.map(Password(p.resultName)).single.apply()
  }

  def findAll()(implicit session: DBSession = autoSession): List[Password] = {
    withSQL(select.from(Password as p)).map(Password(p.resultName)).list.apply()
  }

  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(Password as p)).map(rs => rs.long(1)).single.apply().get
  }

  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[Password] = {
    withSQL {
      select.from(Password as p).where.append(sqls"${where}")
    }.map(Password(p.resultName)).list.apply()
  }

  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL {
      select(sqls"count(1)").from(Password as p).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }

  def create(
    id: String,
    userId: String,
    hash: String,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None)(implicit session: DBSession = autoSession): Password = {
    withSQL {
      insert.into(Password).columns(
        column.id,
        column.userId,
        column.hash,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
          sqls.uuid(id),
          sqls.uuid(userId),
          hash,
          sqls.uuid(createdBy),
          createdAt,
          sqls.uuid(updatedBy),
          updatedAt,
          deletedBy.map(sqls.uuid),
          deletedAt
        )
    }.update.apply()

    Password(
      id = id,
      userId = userId,
      hash = hash,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt)
  }

  def save(entity: Password)(implicit session: DBSession = autoSession): Password = {
    withSQL {
      update(Password as p).set(
        p.id -> entity.id,
        p.userId -> entity.userId,
        p.hash -> entity.hash,
        p.createdBy -> entity.createdBy,
        p.createdAt -> entity.createdAt,
        p.updatedBy -> entity.updatedBy,
        p.updatedAt -> entity.updatedAt,
        p.deletedBy -> entity.deletedBy,
        p.deletedAt -> entity.deletedAt
      ).where.eq(p.id, entity.id)
    }.update.apply()
    entity
  }

  def destroy(entity: Password)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(Password).where.eq(column.id, entity.id) }.update.apply()
  }

}
