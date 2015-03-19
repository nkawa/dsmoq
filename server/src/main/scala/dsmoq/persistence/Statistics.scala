package dsmoq.persistence

import scalikejdbc._
import org.joda.time.{DateTime}
import PostgresqlHelper._

case class Statistics(
  id: String,
  targetMonth: DateTime, 
  datasetCount: Long, 
  realSize: Long, 
  compressedSize: Long, 
  s3Size: Long, 
  localSize: Long,
  statisticsType: Int,
  createdBy: String,
  createdAt: DateTime, 
  updatedBy: String,
  updatedAt: DateTime, 
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None) {

  def save()(implicit session: DBSession = Statistics.autoSession): Statistics = Statistics.save(this)(session)

  def destroy()(implicit session: DBSession = Statistics.autoSession): Unit = Statistics.destroy(this)(session)

}
      

object Statistics extends SQLSyntaxSupport[Statistics] {

  override val tableName = "statistics"

  override val columns = Seq("id", "target_month", "dataset_count", "real_size", "compressed_size", "s3_size", "local_size", "created_by", "created_at", "updated_by", "updated_at", "deleted_by", "deleted_at", "statistics_type")

  def apply(s: SyntaxProvider[Statistics])(rs: WrappedResultSet): Statistics = apply(s.resultName)(rs)
  def apply(s: ResultName[Statistics])(rs: WrappedResultSet): Statistics = new Statistics(
    id = rs.string(s.id),
    targetMonth = rs.timestamp(s.targetMonth).toJodaDateTime,
    datasetCount = rs.long(s.datasetCount),
    realSize = rs.long(s.realSize),
    compressedSize = rs.long(s.compressedSize),
    s3Size = rs.long(s.s3Size),
    localSize = rs.long(s.localSize),
    statisticsType = rs.int(s.statisticsType),
    createdBy = rs.string(s.createdBy),
    createdAt = rs.timestamp(s.createdAt).toJodaDateTime,
    updatedBy = rs.string(s.updatedBy),
    updatedAt = rs.timestamp(s.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(s.deletedBy),
    deletedAt = rs.timestampOpt(s.deletedAt).map(_.toJodaDateTime)
  )
      
  val s = Statistics.syntax("s")

  //override val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[Statistics] = {
    withSQL {
      select.from(Statistics as s).where.eq(s.id, sqls.uuid(id))
    }.map(Statistics(s.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[Statistics] = {
    withSQL(select.from(Statistics as s)).map(Statistics(s.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(Statistics as s)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[Statistics] = {
    withSQL { 
      select.from(Statistics as s).where.append(sqls"${where}")
    }.map(Statistics(s.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL { 
      select(sqls"count(1)").from(Statistics as s).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    id: String,
    targetMonth: DateTime,
    datasetCount: Long,
    realSize: Long,
    compressedSize: Long,
    s3Size: Long,
    localSize: Long,
    statisticsType: Int,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None)(implicit session: DBSession = autoSession): Statistics = {
    withSQL {
      insert.into(Statistics).columns(
        column.id,
        column.targetMonth,
        column.datasetCount,
        column.realSize,
        column.compressedSize,
        column.s3Size,
        column.localSize,
        column.statisticsType,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
        sqls.uuid(id),
        targetMonth,
        datasetCount,
        realSize,
        compressedSize,
        s3Size,
        localSize,
        statisticsType,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(sqls.uuid),
        deletedAt
      )
    }.update.apply()

    Statistics(
      id = id,
      targetMonth = targetMonth,
      datasetCount = datasetCount,
      realSize = realSize,
      compressedSize = compressedSize,
      s3Size = s3Size,
      localSize = localSize,
      statisticsType = statisticsType,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt)
  }

  def save(entity: Statistics)(implicit session: DBSession = autoSession): Statistics = {
    withSQL {
      update(Statistics).set(
        column.id -> sqls.uuid(entity.id),
        column.targetMonth -> entity.targetMonth,
        column.datasetCount -> entity.datasetCount,
        column.realSize -> entity.realSize,
        column.compressedSize -> entity.compressedSize,
        column.s3Size -> entity.s3Size,
        column.localSize -> entity.localSize,
        column.statisticsType -> entity.statisticsType,
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
        
  def destroy(entity: Statistics)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(Statistics).where.eq(column.id, sqls.uuid(entity.id)) }.update.apply()
  }
        
}
