package dsmoq.persistence

import org.joda.time.DateTime

import PostgresqlHelper.PgSQLSyntaxType
import scalikejdbc.AutoSession
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
  deletedAt: Option[DateTime] = None
) {

  def save()(implicit session: DBSession = Ownership.autoSession): Ownership = Ownership.save(this)(session)

  def destroy()(implicit session: DBSession = Ownership.autoSession): Unit = Ownership.destroy(this)(session)

}

object Ownership extends SQLSyntaxSupport[Ownership] {

  override val tableName = "ownerships"

  override val columns = Seq(
    "id", "dataset_id", "group_id", "access_level",
    "created_by", "created_at",
    "updated_by", "updated_at",
    "deleted_by", "deleted_at"
  )

  def apply(o: ResultName[Ownership])(rs: WrappedResultSet): Ownership = Ownership(
    id = rs.string(o.id),
    datasetId = rs.string(o.datasetId),
    groupId = rs.string(o.groupId),
    accessLevel = rs.int(o.accessLevel),
    createdBy = rs.string(o.createdBy),
    createdAt = rs.timestamp(o.createdAt).toJodaDateTime,
    updatedBy = rs.string(o.updatedBy),
    updatedAt = rs.timestamp(o.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(o.deletedBy),
    deletedAt = rs.timestampOpt(o.deletedAt).map(_.toJodaDateTime)
  )

  val o = Ownership.syntax("o")

  override val autoSession = AutoSession

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
    deletedAt: Option[DateTime] = None
  )(implicit session: DBSession = autoSession): Ownership = {
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
      deletedAt = deletedAt
    )
  }

  def save(entity: Ownership)(implicit session: DBSession = autoSession): Ownership = {
    withSQL {
      update(Ownership).set(
        column.id -> sqls.uuid(entity.id),
        column.datasetId -> sqls.uuid(entity.datasetId),
        column.groupId -> sqls.uuid(entity.groupId),
        column.accessLevel -> entity.accessLevel,
        column.createdBy -> sqls.uuid(entity.createdBy),
        column.createdAt -> entity.createdAt,
        column.updatedBy -> sqls.uuid(entity.updatedBy),
        column.updatedAt -> entity.updatedAt,
        column.deletedBy -> entity.deletedBy.map(sqls.uuid),
        column.deletedAt -> entity.deletedAt
      ).where.eq(column.id, sqls.uuid(entity.id))
    }.update.apply()
    entity
  }

  def destroy(entity: Ownership)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(Ownership).where.eq(column.id, entity.id) }.update.apply()
  }

}
