package dsmoq.persistence

import scalikejdbc._
import scalikejdbc.SQLInterpolation._
import org.joda.time.{DateTime}
import PostgresqlHelper._

case class DatasetAnnotation(
  id: String,
  datasetId: String,
  annotationId: String,
  data: String, 
  createdBy: String,
  createdAt: DateTime, 
  updatedBy: String,
  updatedAt: DateTime, 
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None) {

  def save()(implicit session: DBSession = DatasetAnnotation.autoSession): DatasetAnnotation = DatasetAnnotation.save(this)(session)

  def destroy()(implicit session: DBSession = DatasetAnnotation.autoSession): Unit = DatasetAnnotation.destroy(this)(session)

}
      

object DatasetAnnotation extends SQLSyntaxSupport[DatasetAnnotation] {

  override val tableName = "dataset_annotations"

  override val columns = Seq("id", "dataset_id", "annotation_id", "data", "created_by", "created_at", "updated_by", "updated_at", "deleted_by", "deleted_at")

  def apply(da: ResultName[DatasetAnnotation])(rs: WrappedResultSet): DatasetAnnotation = new DatasetAnnotation(
    id = rs.string(da.id),
    datasetId = rs.string(da.datasetId),
    annotationId = rs.string(da.annotationId),
    data = rs.string(da.data),
    createdBy = rs.string(da.createdBy),
    createdAt = rs.timestamp(da.createdAt).toDateTime,
    updatedBy = rs.string(da.updatedBy),
    updatedAt = rs.timestamp(da.updatedAt).toDateTime,
    deletedBy = rs.stringOpt(da.deletedBy),
    deletedAt = rs.timestampOpt(da.deletedAt).map(_.toDateTime)
  )
      
  val da = DatasetAnnotation.syntax("da")

  //val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[DatasetAnnotation] = {
    withSQL { 
      select.from(DatasetAnnotation as da).where.eq(da.id, sqls.uuid(id))
    }.map(DatasetAnnotation(da.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[DatasetAnnotation] = {
    withSQL(select.from(DatasetAnnotation as da)).map(DatasetAnnotation(da.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(DatasetAnnotation as da)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[DatasetAnnotation] = {
    withSQL { 
      select.from(DatasetAnnotation as da).where.append(sqls"${where}")
    }.map(DatasetAnnotation(da.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL { 
      select(sqls"count(1)").from(DatasetAnnotation as da).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    id: String,
    datasetId: String,
    annotationId: String,
    data: String,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None)(implicit session: DBSession = autoSession): DatasetAnnotation = {
    withSQL {
      insert.into(DatasetAnnotation).columns(
        column.id,
        column.datasetId,
        column.annotationId,
        column.data,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
        sqls.uuid(id),
        sqls.uuid(datasetId),
        sqls.uuid(annotationId),
        data,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(sqls.uuid),
        deletedAt
      )
    }.update.apply()

    DatasetAnnotation(
      id = id,
      datasetId = datasetId,
      annotationId = annotationId,
      data = data,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt)
  }

  def save(entity: DatasetAnnotation)(implicit session: DBSession = autoSession): DatasetAnnotation = {
    withSQL { 
      update(DatasetAnnotation as da).set(
        da.id -> entity.id,
        da.datasetId -> entity.datasetId,
        da.annotationId -> entity.annotationId,
        da.data -> entity.data,
        da.createdBy -> entity.createdBy,
        da.createdAt -> entity.createdAt,
        da.updatedBy -> entity.updatedBy,
        da.updatedAt -> entity.updatedAt,
        da.deletedBy -> entity.deletedBy,
        da.deletedAt -> entity.deletedAt
      ).where.eq(da.id, entity.id)
    }.update.apply()
    entity 
  }
        
  def destroy(entity: DatasetAnnotation)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(DatasetAnnotation).where.eq(column.id, entity.id) }.update.apply()
  }
        
}