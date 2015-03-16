package dsmoq.statistics

import java.util.UUID

import dsmoq.AppConf
import org.joda.time._
import dsmoq.persistence._
import scalikejdbc.DB
import scalikejdbc._
import scalikejdbc.config.DBs
import PostgresqlHelper._

object Main extends App {
  val n = DateTime.now
  val now = new DateTime(n.getYearOfCentury(), n.getMonthOfYear(), 1, 0, 0)
  DBs.setupAll()
  DB localTx { implicit s =>
    val d = Dataset.d
    val f = File.f
    var date = withSQL {
      select
        .from(Dataset as d)
        .where
          .isNull(d.deletedBy)
          .and
          .isNull(d.deletedAt)
          .orderBy(d.createdBy)
          .limit(1)
    }.map(_.jodaDateTime("created_by")).single().apply() match {
      case Some(date) => new DateTime(date.getYearOfCentury(), date.getMonthOfYear(), 1, 0, 0)
      case None => {
        val one_month_past = now.minusMonths(1)
        new DateTime(one_month_past.getYearOfCentury(), one_month_past.getMonthOfYear(), 1, 0, 0)
      }
    }

    while (date.compareTo(now) < 0) {
      if (! hasStatistics(date)) {
        Statistics.create(
          id = UUID.randomUUID().toString,
          targetMonth = date,
          datasetCount = countDatasets(Some(date)),
          realSize = countRealSize(Some(date)),
          compressedSize = countFullSize(Some(date)),
          s3Size = countS3Size(Some(date)),
          localSize = countLocalSize(Some(date)),
          createdBy = AppConf.systemUserId,
          createdAt = now,
          updatedBy = AppConf.systemUserId,
          updatedAt = now
        )
      }
      date = date.minusMonths(-1)
    }
  }
  DBs.closeAll()

  def hasStatistics(from: DateTime)(implicit s: DBSession): Boolean = {
      val st = Statistics.s
      val count = withSQL {
        select(sqls"count(1)")
          .from(Statistics as st)
          .where
            .eq(st.targetMonth, from)
      }.map(_.long(1)).single.apply.get
      count > 0
  }

  def countDatasets(from: Option[DateTime])(implicit s: DBSession) = {
    val d = Dataset.d
    withSQL {
      select(sqls"count(1)")
        .from(Dataset as d)
        .where.map { x =>
        if (from.isEmpty) {
          x.isNull(d.deletedAt)
          .and
          .isNull(d.deletedBy)
        } else {
          x.ge(d.createdBy, from.get)
          .and
          .lt(d.createdBy, from.get.minusMonths(-1))
          .and
          .isNull(d.deletedAt)
          .and
          .isNull(d.deletedBy)
        }
      }
    }.map(_.long(1)).single.apply.get
  }

  def countFullSize(from: Option[DateTime])(implicit s: DBSession) = {
    val d = Dataset.d
    withSQL {
      select(sqls.count(d.filesSize))
        .from(Dataset as d)
        .where.map { x =>
        if (from.isEmpty) {
          x.isNull(d.deletedAt)
            .and
            .isNull(d.deletedBy)
        } else {
          x.ge(d.createdBy, from.get)
            .and
            .lt(d.createdBy, from.get.minusMonths(-1))
            .and
            .isNull(d.deletedAt)
            .and
            .isNull(d.deletedBy)
        }
      }
    }.map(_.long(1)).single.apply.get
  }

  def countRealSize(from: Option[DateTime])(implicit s: DBSession) = {
    val d = Dataset.d
    val f = File.f
    withSQL {
      select(sqls.count(f.realSize))
        .from(Dataset as d)
        .innerJoin(File as f).on(f.datasetId, d.id)
        .where.map { x =>
        if (from.isEmpty) {
          x.isNull(f.deletedAt)
            .and
            .isNull(f.deletedBy)
            .and
            .isNull(d.deletedAt)
            .and
            .isNull(d.deletedBy)
        } else {
          x.ge(d.createdBy, from.get)
            .and
            .lt(d.createdBy, from.get.minusMonths(-1))
            .and
            .isNull(f.deletedAt)
            .and
            .isNull(f.deletedBy)
            .and
            .isNull(d.deletedAt)
            .and
            .isNull(d.deletedBy)
        }
      }
    }.map(_.long(1)).single.apply.get
  }

  def countS3Size(from: Option[DateTime])(implicit s: DBSession) = {
    val d = Dataset.d
    withSQL {
      select(sqls.count(d.filesSize))
        .from(Dataset as d)
        .where.map { x =>
        if (from.isEmpty) {
          x.isNull(d.deletedAt)
            .and
            .isNull(d.deletedBy)
            .and
            .eq(d.s3State, 1)
        } else {
          x.ge(d.createdBy, from.get)
            .and
            .lt(d.createdBy, from.get.minusMonths(-1))
            .and
            .isNull(d.deletedAt)
            .and
            .isNull(d.deletedBy)
            .and
            .eq(d.s3State, 1)
        }
      }
    }.map(_.long(1)).single.apply.get
  }

  def countLocalSize(from: Option[DateTime])(implicit s: DBSession) = {
    val d = Dataset.d
    withSQL {
      select(sqls.count(d.filesSize))
        .from(Dataset as d)
        .where.map { x =>
        if (from.isEmpty) {
          x.isNull(d.deletedAt)
            .and
            .isNull(d.deletedBy)
            .and
            .eq(d.localState, 1)
        } else {
          x.ge(d.createdBy, from.get)
            .and
            .lt(d.createdBy, from.get.minusMonths(-1))
            .and
            .isNull(d.deletedAt)
            .and
            .isNull(d.deletedBy)
            .and
            .eq(d.localState, 1)
        }
      }
    }.map(_.long(1)).single.apply.get
  }
}
