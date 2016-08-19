package dsmoq.persistence

import org.joda.time.DateTime

import PostgresqlHelper.PgSQLSyntaxType
import scalikejdbc.DBSession
import scalikejdbc.ResultName
import scalikejdbc.ResultName
import scalikejdbc.SQLSyntax
import scalikejdbc.SQLSyntaxSupport
import scalikejdbc.SQLSyntaxSupport
import scalikejdbc.SyntaxProvider
import scalikejdbc.SyntaxProvider
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

case class ApiKey(
  id: String,
  userId: String,
  apiKey: String,
  secretKey: String,
  permission: Int,
  createdBy: String,
  createdAt: DateTime,
  updatedBy: String,
  updatedAt: DateTime,
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None
) {

  def save()(implicit session: DBSession = ApiKey.autoSession): ApiKey = ApiKey.save(this)(session)

  def destroy()(implicit session: DBSession = ApiKey.autoSession): Unit = ApiKey.destroy(this)(session)

}

object ApiKey extends SQLSyntaxSupport[ApiKey] {

  override val tableName = "api_key"

  override val columns = Seq(
    "id", "user_id",
    "api_key", "secret_key", "permission",
    "created_by", "created_at",
    "updated_by", "updated_at",
    "deleted_by", "deleted_at"
  )

  def apply(ak: SyntaxProvider[ApiKey])(rs: WrappedResultSet): ApiKey = apply(ak.resultName)(rs)
  def apply(ak: ResultName[ApiKey])(rs: WrappedResultSet): ApiKey = ApiKey(
    id = rs.string(ak.id),
    userId = rs.string(ak.userId),
    apiKey = rs.string(ak.apiKey),
    secretKey = rs.string(ak.secretKey),
    permission = rs.int(ak.permission),
    createdBy = rs.string(ak.createdBy),
    createdAt = rs.timestamp(ak.createdAt).toJodaDateTime,
    updatedBy = rs.string(ak.updatedBy),
    updatedAt = rs.timestamp(ak.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(ak.deletedBy),
    deletedAt = rs.timestampOpt(ak.deletedAt).map(_.toJodaDateTime)
  )

  val ak = ApiKey.syntax("ak")

  //override val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[ApiKey] = {
    withSQL {
      select.from(ApiKey as ak).where.eq(ak.id, sqls.uuid(id))
    }.map(ApiKey(ak.resultName)).single.apply()
  }

  def findAll()(implicit session: DBSession = autoSession): List[ApiKey] = {
    withSQL(select.from(ApiKey as ak)).map(ApiKey(ak.resultName)).list.apply()
  }

  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(ApiKey as ak)).map(rs => rs.long(1)).single.apply().get
  }

  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[ApiKey] = {
    withSQL {
      select.from(ApiKey as ak).where.append(sqls"${where}")
    }.map(ApiKey(ak.resultName)).list.apply()
  }

  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL {
      select(sqls"count(1)").from(ApiKey as ak).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }

  def create(
    id: String,
    userId: String,
    apiKey: String,
    secretKey: String,
    permission: Int,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None)(implicit session: DBSession = autoSession): ApiKey = {
    withSQL {
      insert.into(ApiKey).columns(
        column.id,
        column.userId,
        column.apiKey,
        column.secretKey,
        column.permission,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
        sqls.uuid(id),
        sqls.uuid(userId),
        apiKey,
        secretKey,
        permission,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(sqls.uuid),
        deletedAt
      )
    }.update.apply()

    ApiKey(
      id = id,
      userId = userId,
      apiKey = apiKey,
      secretKey = secretKey,
      permission = permission,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt
    )
  }

  def save(entity: ApiKey)(implicit session: DBSession = autoSession): ApiKey = {
    withSQL {
      update(ApiKey).set(
        column.id -> sqls.uuid(entity.id),
        column.userId -> sqls.uuid(entity.userId),
        column.apiKey -> entity.apiKey,
        column.secretKey -> entity.secretKey,
        column.permission -> entity.permission,
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

  def destroy(entity: ApiKey)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(ApiKey).where.eq(column.id, sqls.uuid(entity.id)) }.update.apply()
  }

}
