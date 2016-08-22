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

case class AppVersion(
  id: String,
  appId: String,
  fileName: String,
  version: Int,
  createdBy: String,
  createdAt: DateTime,
  updatedBy: String,
  updatedAt: DateTime,
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None
) {

  def save()(implicit session: DBSession = AppVersion.autoSession): AppVersion = AppVersion.save(this)(session)

  def destroy()(implicit session: DBSession = AppVersion.autoSession): Unit = AppVersion.destroy(this)(session)

}

object AppVersion extends SQLSyntaxSupport[AppVersion] {

  override val tableName = "app_versions"

  override val columns = Seq(
    "id", "app_id", "file_name", "version",
    "created_by", "created_at",
    "updated_by", "updated_at",
    "deleted_by", "deleted_at"
  )

  def apply(v: SyntaxProvider[AppVersion])(rs: WrappedResultSet): AppVersion = apply(v.resultName)(rs)

  def apply(v: ResultName[AppVersion])(rs: WrappedResultSet): AppVersion = AppVersion(
    id = rs.string(v.id),
    appId = rs.string(v.appId),
    fileName = rs.string(v.fileName),
    version = rs.int(v.version),
    createdBy = rs.string(v.createdBy),
    createdAt = rs.timestamp(v.createdAt).toJodaDateTime,
    updatedBy = rs.string(v.updatedBy),
    updatedAt = rs.timestamp(v.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(v.deletedBy),
    deletedAt = rs.timestampOpt(v.deletedAt).map(_.toJodaDateTime)
  )

  val v = AppVersion.syntax("v")

  //val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[AppVersion] = {
    withSQL {
      select.from(AppVersion as v).where.eq(v.id, sqls.uuid(id))
    }.map(AppVersion(v.resultName)).single.apply()
  }

  def findAll()(implicit session: DBSession = autoSession): List[AppVersion] = {
    withSQL(select.from(AppVersion as v)).map(AppVersion(v.resultName)).list.apply()
  }

  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(AppVersion as v)).map(rs => rs.long(1)).single.apply().get
  }

  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[AppVersion] = {
    withSQL {
      select.from(AppVersion as v).where.append(sqls"${where}")
    }.map(AppVersion(v.resultName)).list.apply()
  }

  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL {
      select(sqls"count(1)").from(AppVersion as v).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }

  def create(
    id: String,
    appId: String,
    fileName: String,
    version: Int,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None
  )(implicit session: DBSession = autoSession): AppVersion = {
    withSQL {
      insert.into(AppVersion).columns(
        column.id,
        column.appId,
        column.fileName,
        column.version,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
        sqls.uuid(id),
        sqls.uuid(appId),
        fileName,
        version,
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(sqls.uuid),
        deletedAt
      )
    }.update.apply()

    AppVersion(
      id = id,
      appId = appId,
      fileName = fileName,
      version = version,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt
    )
  }

  def save(entity: AppVersion)(implicit session: DBSession = autoSession): AppVersion = {
    withSQL {
      update(AppVersion as v).set(
        v.id -> entity.id,
        v.appId -> entity.appId,
        v.fileName -> entity.fileName,
        v.version -> entity.version,
        v.createdBy -> entity.createdBy,
        v.createdAt -> entity.createdAt,
        v.updatedBy -> entity.updatedBy,
        v.updatedAt -> entity.updatedAt,
        v.deletedBy -> entity.deletedBy,
        v.deletedAt -> entity.deletedAt
      ).where.eq(v.id, entity.id)
    }.update.apply()
    entity
  }

  def destroy(entity: AppVersion)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(AppVersion).where.eq(column.id, entity.id) }.update.apply()
  }

}
