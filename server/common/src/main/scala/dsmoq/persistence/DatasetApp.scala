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

case class DatasetApp(
  id: String,
  datasetId: String,
  appId: String,
  isPrimary: Boolean,
  createdBy: String,
  createdAt: DateTime,
  updatedBy: String,
  updatedAt: DateTime,
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None
) {

  def save()(implicit session: DBSession = DatasetApp.autoSession): DatasetApp = DatasetApp.save(this)(session)

  def destroy()(implicit session: DBSession = DatasetApp.autoSession): Unit = DatasetApp.destroy(this)(session)

}

object DatasetApp extends SQLSyntaxSupport[DatasetApp] {

  override val tableName = "dataset_apps"

  override val columns = Seq(
    "id", "dataset_id", "app_id", "is_primary",
    "created_by", "created_at",
    "updated_by", "updated_at",
    "deleted_by", "deleted_at"
  )

  def apply(v: SyntaxProvider[DatasetApp])(rs: WrappedResultSet): DatasetApp = apply(v.resultName)(rs)

  def apply(v: ResultName[DatasetApp])(rs: WrappedResultSet): DatasetApp = DatasetApp(
    id = rs.string(v.id),
    appId = rs.string(v.appId),
    datasetId = rs.string(v.datasetId),
    isPrimary = rs.boolean(v.isPrimary),
    createdBy = rs.string(v.createdBy),
    createdAt = rs.timestamp(v.createdAt).toJodaDateTime,
    updatedBy = rs.string(v.updatedBy),
    updatedAt = rs.timestamp(v.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(v.deletedBy),
    deletedAt = rs.timestampOpt(v.deletedAt).map(_.toJodaDateTime)
  )

  val v = DatasetApp.syntax("v")

  //val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[DatasetApp] = {
    withSQL {
      select.from(DatasetApp as v).where.eq(v.id, sqls.uuid(id))
    }.map(DatasetApp(v.resultName)).single.apply()
  }

  def findAll()(implicit session: DBSession = autoSession): List[DatasetApp] = {
    withSQL(select.from(DatasetApp as v)).map(DatasetApp(v.resultName)).list.apply()
  }

  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(DatasetApp as v)).map(rs => rs.long(1)).single.apply().get
  }

  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[DatasetApp] = {
    withSQL {
      select.from(DatasetApp as v).where.append(sqls"${where}")
    }.map(DatasetApp(v.resultName)).list.apply()
  }

  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL {
      select(sqls"count(1)").from(DatasetApp as v).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }

  def create(
    id: String,
    datasetId: String,
    appId: String,
    isPrimary: Boolean,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None
  )(implicit session: DBSession = autoSession): DatasetApp = {
    withSQL {
      insert.into(DatasetApp).columns(
        column.id,
        column.datasetId,
        column.appId,
        column.isPrimary,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
        sqls.uuid(id),
        sqls.uuid(datasetId),
        sqls.uuid(appId),
        isPrimary,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(sqls.uuid),
        deletedAt
      )
    }.update.apply()

    DatasetApp(
      id = id,
      datasetId = datasetId,
      appId = appId,
      isPrimary = isPrimary,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt
    )
  }

  def save(entity: DatasetApp)(implicit session: DBSession = autoSession): DatasetApp = {
    withSQL {
      update(DatasetApp as v).set(
        v.id -> entity.id,
        v.datasetId -> entity.datasetId,
        v.appId -> entity.appId,
        v.isPrimary -> entity.isPrimary,
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

  def destroy(entity: DatasetApp)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(DatasetApp).where.eq(column.id, entity.id) }.update.apply()
  }

}

