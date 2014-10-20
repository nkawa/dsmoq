package dsmoq.persistence

import scalikejdbc._
import org.joda.time.{DateTime}
import PostgresqlHelper._

case class DatasetAccessLog(
  id: String,
  datasetId: String,
  createdBy: String,
  createdAt: DateTime) {

  def save()(implicit session: DBSession = DatasetAccessLog.autoSession): DatasetAccessLog = DatasetAccessLog.save(this)(session)

  def destroy()(implicit session: DBSession = DatasetAccessLog.autoSession): Unit = DatasetAccessLog.destroy(this)(session)

}
      

object DatasetAccessLog extends SQLSyntaxSupport[DatasetAccessLog] {

  override val tableName = "dataset_access_logs"

  override val columns = Seq("id", "dataset_id", "created_by", "created_at")

  def apply(dal: SyntaxProvider[DatasetAccessLog])(rs: WrappedResultSet): DatasetAccessLog = apply(dal.resultName)(rs)
  def apply(dal: ResultName[DatasetAccessLog])(rs: WrappedResultSet): DatasetAccessLog = new DatasetAccessLog(
    id = rs.get(dal.id),
    datasetId = rs.get(dal.datasetId),
    createdBy = rs.get(dal.createdBy),
    createdAt = rs.get(dal.createdAt)
  )
      
  val dal = DatasetAccessLog.syntax("dal")

  override val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[DatasetAccessLog] = {
    withSQL {
      select.from(DatasetAccessLog as dal).where.eqUuid(dal.id, id)
    }.map(DatasetAccessLog(dal.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[DatasetAccessLog] = {
    withSQL(select.from(DatasetAccessLog as dal)).map(DatasetAccessLog(dal.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(DatasetAccessLog as dal)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[DatasetAccessLog] = {
    withSQL { 
      select.from(DatasetAccessLog as dal).where.append(sqls"${where}")
    }.map(DatasetAccessLog(dal.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL { 
      select(sqls"count(1)").from(DatasetAccessLog as dal).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    id: String,
    datasetId: String,
    createdBy: String,
    createdAt: DateTime)(implicit session: DBSession = autoSession): DatasetAccessLog = {
    withSQL {
      insert.into(DatasetAccessLog).columns(
        column.id,
        column.datasetId,
        column.createdBy,
        column.createdAt
      ).values(
        sqls.uuid(id),
        sqls.uuid(datasetId),
        sqls.uuid(createdBy),
        createdAt
      )
    }.update.apply()

    DatasetAccessLog(
      id = id,
      datasetId = datasetId,
      createdBy = createdBy,
      createdAt = createdAt)
  }

  def save(entity: DatasetAccessLog)(implicit session: DBSession = autoSession): DatasetAccessLog = {
    withSQL {
      update(DatasetAccessLog).set(
        column.id -> sqls.uuid(entity.id),
        column.datasetId -> sqls.uuid(entity.datasetId),
        column.createdBy ->  sqls.uuid(entity.createdBy),
        column.createdAt -> entity.createdAt
      ).where.eqUuid(column.id, entity.id)
    }.update.apply()
    entity
  }
        
  def destroy(entity: DatasetAccessLog)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(DatasetAccessLog).where.eqUuid(column.id, entity.id) }.update.apply()
  }
        
}
