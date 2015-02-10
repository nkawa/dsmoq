package dsmoq.persistence

import scalikejdbc._
import org.joda.time.{DateTime}
import PostgresqlHelper._

case class ZipedFiles(
  id: String,
  fileId: String,
  name: String, 
  description: String, 
  fileSize: Long, 
  createdBy: String,
  createdAt: DateTime, 
  updatedBy: String,
  updatedAt: DateTime, 
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None) {

  def save()(implicit session: DBSession = ZipedFiles.autoSession): ZipedFiles = ZipedFiles.save(this)(session)

  def destroy()(implicit session: DBSession = ZipedFiles.autoSession): Unit = ZipedFiles.destroy(this)(session)

}
      

object ZipedFiles extends SQLSyntaxSupport[ZipedFiles] {

  override val tableName = "ziped_files"

  override val columns = Seq("id", "file_id", "name", "description", "file_size", "created_by", "created_at", "updated_by", "updated_at", "deleted_by", "deleted_at")

  def apply(zf: SyntaxProvider[ZipedFiles])(rs: WrappedResultSet): ZipedFiles = apply(zf.resultName)(rs)
  def apply(zf: ResultName[ZipedFiles])(rs: WrappedResultSet): ZipedFiles = new ZipedFiles(
    id = rs.string(zf.id),
    fileId = rs.string(zf.fileId),
    name = rs.get(zf.name),
    description = rs.get(zf.description),
    fileSize = rs.get(zf.fileSize),
    createdBy = rs.string(zf.createdBy),
    createdAt = rs.timestamp(zf.createdAt).toJodaDateTime,
    updatedBy = rs.string(zf.updatedBy),
    updatedAt = rs.timestamp(zf.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(zf.deletedBy),
    deletedAt = rs.timestampOpt(zf.deletedAt).map(_.toJodaDateTime)
  )
      
  val zf = ZipedFiles.syntax("zf")

  override val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[ZipedFiles] = {
    withSQL {
      select.from(ZipedFiles as zf).where.eq(zf.id, sqls.uuid(id))
    }.map(ZipedFiles(zf.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[ZipedFiles] = {
    withSQL(select.from(ZipedFiles as zf)).map(ZipedFiles(zf.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(ZipedFiles as zf)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[ZipedFiles] = {
    withSQL { 
      select.from(ZipedFiles as zf).where.append(sqls"${where}")
    }.map(ZipedFiles(zf.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL { 
      select(sqls"count(1)").from(ZipedFiles as zf).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    id: String,
    fileId: String,
    name: String,
    description: String,
    fileSize: Long,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None)(implicit session: DBSession = autoSession): ZipedFiles = {
    withSQL {
      insert.into(ZipedFiles).columns(
        column.id,
        column.fileId,
        column.name,
        column.description,
        column.fileSize,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
        sqls.uuid(id),
        sqls.uuid(fileId),
        name,
        description,
        fileSize,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(sqls.uuid),
        deletedAt
      )
    }.update.apply()

    ZipedFiles(
      id = id,
      fileId = fileId,
      name = name,
      description = description,
      fileSize = fileSize,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt)
  }

  def save(entity: ZipedFiles)(implicit session: DBSession = autoSession): ZipedFiles = {
    withSQL {
      update(ZipedFiles).set(
        column.id -> sqls.uuid(entity.id),
        column.fileId -> sqls.uuid(entity.fileId),
        column.name -> entity.name,
        column.description -> entity.description,
        column.fileSize -> entity.fileSize,
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
        
  def destroy(entity: ZipedFiles)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(ZipedFiles).where.eq(column.id, sqls.uuid(entity.id)) }.update.apply()
  }
        
}