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

case class Dataset(
  id: String,
  name: String,
  description: String,
  licenseId: String,
  filesCount: Int,
  filesSize: Long,
  createdBy: String,
  createdAt: DateTime,
  updatedBy: String,
  updatedAt: DateTime,
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None,
  localState: Int,
  s3State: Int,
  files: Seq[File] = Nil
) {

  def save()(implicit session: DBSession = Dataset.autoSession): Dataset = Dataset.save(this)(session)

  def destroy()(implicit session: DBSession = Dataset.autoSession): Unit = Dataset.destroy(this)(session)

}

object Dataset extends SQLSyntaxSupport[Dataset] {

  override val tableName = "datasets"

  override val columns = Seq(
    "id", "name", "description",
    "license_id", "files_count", "files_size",
    "created_by", "created_at",
    "updated_by", "updated_at",
    "deleted_by", "deleted_at",
    "local_state", "s3_state"
  )

  def apply(d: SyntaxProvider[Dataset])(rs: WrappedResultSet): Dataset = apply(d.resultName)(rs)

  def apply(d: ResultName[Dataset])(rs: WrappedResultSet): Dataset = Dataset(
    id = rs.string(d.id),
    name = rs.string(d.name),
    description = rs.string(d.description),
    licenseId = rs.string(d.licenseId),
    filesCount = rs.int(d.filesCount),
    filesSize = rs.long(d.filesSize),
    createdBy = rs.string(d.createdBy),
    createdAt = rs.timestamp(d.createdAt).toJodaDateTime,
    updatedBy = rs.string(d.updatedBy),
    updatedAt = rs.timestamp(d.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(d.deletedBy),
    deletedAt = rs.timestampOpt(d.deletedAt).map(_.toJodaDateTime),
    localState = rs.int(d.localState),
    s3State = rs.int(d.s3State)
  )

  val d = Dataset.syntax("d")

  //override val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[Dataset] = {
    withSQL {
      select.from(Dataset as d).where.eq(d.id, sqls.uuid(id))
    }.map(Dataset(d.resultName)).single.apply()
  }

  def findAll()(implicit session: DBSession = autoSession): List[Dataset] = {
    withSQL(select.from(Dataset as d)).map(Dataset(d.resultName)).list.apply()
  }

  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(Dataset as d)).map(rs => rs.long(1)).single.apply().get
  }

  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[Dataset] = {
    withSQL {
      select.from(Dataset as d).where.append(sqls"${where}")
    }.map(Dataset(d.resultName)).list.apply()
  }

  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL {
      select(sqls"count(1)").from(Dataset as d).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }

  def create(
    id: String,
    name: String,
    description: String,
    licenseId: String,
    filesCount: Int,
    filesSize: Long,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None,
    localState: Int,
    s3State: Int
  )(implicit session: DBSession = autoSession): Dataset = {
    withSQL {
      insert.into(Dataset).columns(
        column.id,
        column.name,
        column.description,
        column.licenseId,
        column.filesCount,
        column.filesSize,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt,
        column.localState,
        column.s3State
      ).values(
        sqls.uuid(id),
        name,
        description,
        sqls.uuid(licenseId),
        filesCount,
        filesSize,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(sqls.uuid),
        deletedAt,
        localState,
        s3State
      )
    }.update.apply()

    Dataset(
      id = id,
      name = name,
      description = description,
      licenseId = licenseId,
      filesCount = filesCount,
      filesSize = filesSize,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt,
      localState = localState,
      s3State = s3State
    )
  }

  def save(entity: Dataset)(implicit session: DBSession = autoSession): Dataset = {
    withSQL {
      update(Dataset).set(
        column.id -> sqls.uuid(entity.id),
        column.name -> entity.name,
        column.description -> entity.description,
        column.licenseId -> sqls.uuid(entity.licenseId),
        column.filesCount -> entity.filesCount,
        column.filesSize -> entity.filesSize,
        column.createdBy -> sqls.uuid(entity.createdBy),
        column.createdAt -> entity.createdAt,
        column.updatedBy -> sqls.uuid(entity.updatedBy),
        column.updatedAt -> entity.updatedAt,
        column.deletedBy -> entity.deletedBy.map(sqls.uuid),
        column.deletedAt -> entity.deletedAt,
        column.localState -> entity.localState,
        column.s3State -> entity.s3State
      ).where.eq(column.id, sqls.uuid(entity.id))
    }.update.apply()
    entity
  }

  def destroy(entity: Dataset)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(Dataset).where.eq(column.id, sqls.uuid(entity.id)) }.update.apply()
  }

}
