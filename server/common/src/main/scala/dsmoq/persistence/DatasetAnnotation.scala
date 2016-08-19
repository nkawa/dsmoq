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
  deletedAt: Option[DateTime] = None
) {

  def save()(implicit session: DBSession = DatasetAnnotation.autoSession): DatasetAnnotation = {
    DatasetAnnotation.save(this)(session)
  }

  def destroy()(implicit session: DBSession = DatasetAnnotation.autoSession): Unit = {
    DatasetAnnotation.destroy(this)(session)
  }

}

object DatasetAnnotation extends SQLSyntaxSupport[DatasetAnnotation] {

  override val tableName = "dataset_annotations"

  override val columns = Seq(
    "id", "dataset_id", "annotation_id", "data",
    "created_by", "created_at",
    "updated_by", "updated_at",
    "deleted_by", "deleted_at"
  )

  def apply(da: ResultName[DatasetAnnotation])(rs: WrappedResultSet): DatasetAnnotation = DatasetAnnotation(
    id = rs.string(da.id),
    datasetId = rs.string(da.datasetId),
    annotationId = rs.string(da.annotationId),
    data = rs.string(da.data),
    createdBy = rs.string(da.createdBy),
    createdAt = rs.timestamp(da.createdAt).toJodaDateTime,
    updatedBy = rs.string(da.updatedBy),
    updatedAt = rs.timestamp(da.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(da.deletedBy),
    deletedAt = rs.timestampOpt(da.deletedAt).map(_.toJodaDateTime)
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
    deletedAt: Option[DateTime] = None
  )(implicit session: DBSession = autoSession): DatasetAnnotation = {
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
      deletedAt = deletedAt
    )
  }

  def save(entity: DatasetAnnotation)(implicit session: DBSession = autoSession): DatasetAnnotation = {
    withSQL {
      update(DatasetAnnotation).set(
        column.id -> sqls.uuid(entity.id),
        column.datasetId -> sqls.uuid(entity.datasetId),
        column.annotationId -> sqls.uuid(entity.annotationId),
        column.data -> entity.data,
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

  def destroy(entity: DatasetAnnotation)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(DatasetAnnotation).where.eq(column.id, sqls.uuid(entity.id)) }.update.apply()
  }

}
