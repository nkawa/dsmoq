package dsmoq.persistence

import org.joda.time.DateTime

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

case class FileHistory(
  id: String,
  fileId: String,
  fileType: Int,
  fileMime: String,
  filePath: String,
  fileSize: Long,
  isZip: Boolean,
  realSize: Long,
  createdBy: String,
  createdAt: DateTime,
  updatedBy: String,
  updatedAt: DateTime,
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None
) {

  def save()(implicit session: DBSession = FileHistory.autoSession): FileHistory = FileHistory.save(this)(session)

  def destroy()(implicit session: DBSession = FileHistory.autoSession): Unit = FileHistory.destroy(this)(session)

}

object FileHistory extends SQLSyntaxSupport[FileHistory] {

  override val tableName = "file_histories"

  override val columns = Seq(
    "id", "file_id",
    "file_type", "file_mime", "file_path", "file_size",
    "is_zip", "real_size",
    "created_by", "created_at",
    "updated_by", "updated_at",
    "deleted_by", "deleted_at"
  )

  def apply(fh: ResultName[FileHistory])(rs: WrappedResultSet): FileHistory = FileHistory(
    id = rs.string(fh.id),
    fileId = rs.string(fh.fileId),
    fileType = rs.int(fh.fileType),
    fileMime = rs.string(fh.fileMime),
    filePath = rs.string(fh.filePath),
    fileSize = rs.long(fh.fileSize),
    isZip = rs.boolean(fh.isZip),
    realSize = rs.long(fh.realSize),
    createdBy = rs.string(fh.createdBy),
    createdAt = rs.timestamp(fh.createdAt).toJodaDateTime,
    updatedBy = rs.string(fh.updatedBy),
    updatedAt = rs.timestamp(fh.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(fh.deletedBy),
    deletedAt = rs.timestampOpt(fh.deletedAt).map(_.toJodaDateTime)
  )

  val fh = FileHistory.syntax("fh")

  //val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[FileHistory] = {
    withSQL {
      select.from(FileHistory as fh).where.eq(fh.id, sqls.uuid(id))
    }.map(FileHistory(fh.resultName)).single.apply()
  }

  def findAll()(implicit session: DBSession = autoSession): List[FileHistory] = {
    withSQL(select.from(FileHistory as fh)).map(FileHistory(fh.resultName)).list.apply()
  }

  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(FileHistory as fh)).map(rs => rs.long(1)).single.apply().get
  }

  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[FileHistory] = {
    withSQL {
      select.from(FileHistory as fh).where.append(sqls"${where}")
    }.map(FileHistory(fh.resultName)).list.apply()
  }

  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL {
      select(sqls"count(1)").from(FileHistory as fh).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }

  def create(
    id: String,
    fileId: String,
    fileType: Int,
    fileMime: String,
    filePath: String,
    fileSize: Long,
    isZip: Boolean,
    realSize: Long,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None
  )(implicit session: DBSession = autoSession): FileHistory = {
    withSQL {
      insert.into(FileHistory).columns(
        column.id,
        column.fileId,
        column.fileType,
        column.fileMime,
        column.filePath,
        column.fileSize,
        column.isZip,
        column.realSize,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
        sqls.uuid(id),
        sqls.uuid(fileId),
        fileType,
        fileMime,
        filePath,
        fileSize,
        isZip,
        realSize,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(x => sqls.uuid(x)),
        deletedAt
      )
    }.update.apply()

    FileHistory(
      id = id,
      fileId = fileId,
      fileType = fileType,
      fileMime = fileMime,
      filePath = filePath,
      fileSize = fileSize,
      isZip = isZip,
      realSize = realSize,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt
    )
  }

  def save(entity: FileHistory)(implicit session: DBSession = autoSession): FileHistory = {
    withSQL {
      update(FileHistory as fh).set(
        fh.id -> sqls.uuid(entity.id),
        fh.fileId -> sqls.uuid(entity.fileId),
        fh.fileType -> entity.fileType,
        fh.fileMime -> entity.fileMime,
        fh.filePath -> entity.filePath,
        fh.fileSize -> entity.fileSize,
        fh.isZip -> entity.isZip,
        fh.realSize -> entity.realSize,
        fh.createdBy -> sqls.uuid(entity.createdBy),
        fh.createdAt -> entity.createdAt,
        fh.updatedBy -> sqls.uuid(entity.updatedBy),
        fh.updatedAt -> entity.updatedAt,
        fh.deletedBy -> entity.deletedBy.map(sqls.uuid),
        fh.deletedAt -> entity.deletedAt
      ).where.eq(fh.id, sqls.uuid(entity.id))
    }.update.apply()
    entity
  }

  def destroy(entity: FileHistory)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(FileHistory).where.eq(column.id, entity.id) }.update.apply()
  }

}
