package dsmoq.persistence

import scalikejdbc._
import scalikejdbc.SQLInterpolation._
import org.joda.time.{DateTime}
import PostgresqlHelper._

case class DatasetImage(
  id: String, 
  datasetId: String, 
  imageId: String, 
  isPrimary: Boolean, 
  createdBy: String, 
  createdAt: DateTime, 
  updatedBy: String, 
  updatedAt: DateTime, 
  deletedBy: Option[String] = None, 
  deletedAt: Option[DateTime] = None) {

  def save()(implicit session: DBSession = DatasetImage.autoSession): DatasetImage = DatasetImage.save(this)(session)

  def destroy()(implicit session: DBSession = DatasetImage.autoSession): Unit = DatasetImage.destroy(this)(session)

}
      

object DatasetImage extends SQLSyntaxSupport[DatasetImage] {

  override val tableName = "dataset_images"

  override val columns = Seq("id", "dataset_id", "image_id", "is_primary", "created_by", "created_at", "updated_by", "updated_at", "deleted_by", "deleted_at")

  def apply(di: ResultName[DatasetImage])(rs: WrappedResultSet): DatasetImage = new DatasetImage(
    id = rs.string(di.id),
    datasetId = rs.string(di.datasetId),
    imageId = rs.string(di.imageId),
    isPrimary = rs.boolean(di.isPrimary),
    createdBy = rs.string(di.createdBy),
    createdAt = rs.timestamp(di.createdAt).toDateTime,
    updatedBy = rs.string(di.updatedBy),
    updatedAt = rs.timestamp(di.updatedAt).toDateTime,
    deletedBy = rs.stringOpt(di.deletedBy),
    deletedAt = rs.timestampOpt(di.deletedAt).map(_.toDateTime)
  )
      
  val di = DatasetImage.syntax("di")

  override val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[DatasetImage] = {
    withSQL { 
      select.from(DatasetImage as di).where.eq(di.id, sqls.uuid(id))
    }.map(DatasetImage(di.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[DatasetImage] = {
    withSQL(select.from(DatasetImage as di)).map(DatasetImage(di.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(DatasetImage as di)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[DatasetImage] = {
    withSQL { 
      select.from(DatasetImage as di).where.append(sqls"${where}")
    }.map(DatasetImage(di.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL { 
      select(sqls"count(1)").from(DatasetImage as di).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    id: String,
    datasetId: String,
    imageId: String,
    isPrimary: Boolean,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None)(implicit session: DBSession = autoSession): DatasetImage = {
    withSQL {
      insert.into(DatasetImage).columns(
        column.id,
        column.datasetId,
        column.imageId,
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
        sqls.uuid(imageId),
        isPrimary,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(sqls.uuid),
        deletedAt
      )
    }.update.apply()

    DatasetImage(
      id = id,
      datasetId = datasetId,
      imageId = imageId,
      isPrimary = isPrimary,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt)
  }

  def save(entity: DatasetImage)(implicit session: DBSession = autoSession): DatasetImage = {
    withSQL { 
      update(DatasetImage).set(
        column.id -> sqls.uuid(entity.id),
        column.datasetId -> sqls.uuid(entity.datasetId),
        column.imageId -> sqls.uuid(entity.imageId),
        column.isPrimary -> entity.isPrimary,
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
        
  def destroy(entity: DatasetImage)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(DatasetImage).where.eq(column.id, entity.id) }.update.apply()
  }
        
}
