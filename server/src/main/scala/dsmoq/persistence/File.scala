package dsmoq.persistence

import scalikejdbc._
import scalikejdbc.SQLInterpolation._
import org.joda.time.{DateTime}
import PostgresqlHelper._

case class File(
  id: String,
  datasetId: String,
  name: String, 
  description: String, 
  fileType: Int, 
  fileMime: String, 
  fileSize: Long,
  isZip: Boolean,
  realSize: Long,
  createdBy: String,
  createdAt: DateTime, 
  updatedBy: String,
  updatedAt: DateTime, 
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None,
  zipedFiles: Seq[ZipedFiles] = Nil
) {

  def save()(implicit session: DBSession = File.autoSession): File = File.save(this)(session)

  def destroy()(implicit session: DBSession = File.autoSession): Unit = File.destroy(this)(session)

}
      

object File extends SQLSyntaxSupport[File] {

  override val tableName = "files"

  override val columns = Seq("id", "dataset_id", "name", "description", "file_type", "file_mime", "file_size", "is_zip", "real_size", "created_by", "created_at", "updated_by", "updated_at", "deleted_by", "deleted_at")

  def apply(f: ResultName[File])(rs: WrappedResultSet): File = new File(
    id = rs.string(f.id),
    datasetId = rs.string(f.datasetId),
    name = rs.string(f.name),
    description = rs.string(f.description),
    fileType = rs.int(f.fileType),
    fileMime = rs.string(f.fileMime),
    fileSize = rs.long(f.fileSize),
    isZip = rs.boolean(f.isZip),
    realSize = rs.long(f.realSize),
    createdBy = rs.string(f.createdBy),
    createdAt = rs.timestamp(f.createdAt).toJodaDateTime,
    updatedBy = rs.string(f.updatedBy),
    updatedAt = rs.timestamp(f.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(f.deletedBy),
    deletedAt = rs.timestampOpt(f.deletedAt).map(_.toJodaDateTime)
  )
      
  val f = File.syntax("f")

  override val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[File] = {
    withSQL { 
      select.from(File as f).where.eq(f.id, sqls.uuid(id))
    }.map(File(f.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[File] = {
    withSQL(select.from(File as f)).map(File(f.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(File as f)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[File] = {
    withSQL { 
      select.from(File as f).where.append(sqls"${where}")
    }.map(File(f.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL { 
      select(sqls"count(1)").from(File as f).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    id: String,
    datasetId: String,
    name: String,
    description: String,
    fileType: Int,
    fileMime: String,
    fileSize: Long,
    isZip: Boolean,
    realSize: Long,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None)(implicit session: DBSession = autoSession): File = {
    withSQL {
      insert.into(File).columns(
        column.id,
        column.datasetId,
        column.name,
        column.description,
        column.fileType,
        column.fileMime,
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
        sqls.uuid(datasetId),
        name,
        description,
        fileType,
        fileMime,
        fileSize,
        isZip,
        realSize,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(sqls.uuid),
        deletedAt
      )
    }.update.apply()

    File(
      id = id,
      datasetId = datasetId,
      name = name,
      description = description,
      fileType = fileType,
      fileMime = fileMime,
      fileSize = fileSize,
      isZip = isZip,
      realSize = realSize,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt)
  }

  def save(entity: File)(implicit session: DBSession = autoSession): File = {
    withSQL { 
      update(File).set(
        column.id -> sqls.uuid(entity.id),
        column.datasetId -> sqls.uuid(entity.datasetId),
        column.name -> entity.name,
        column.description -> entity.description,
        column.fileType -> entity.fileType,
        column.fileMime -> entity.fileMime,
        column.fileSize -> entity.fileSize,
        column.isZip -> entity.isZip,
        column.realSize -> entity.realSize,
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
        
  def destroy(entity: File)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(File).where.eq(column.id, sqls.uuid(entity.id)) }.update.apply()
  }

  def opt(f: SyntaxProvider[File])(rs: WrappedResultSet): Option[File] =
    rs.stringOpt(f.resultName.id).map(_ => File(f.resultName)(rs))
        
}
