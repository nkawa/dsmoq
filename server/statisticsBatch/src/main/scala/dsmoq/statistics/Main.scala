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
  val now = DateTime.now()
  DBs.setupAll()
  DB readOnly { implicit s =>
    val st = Statistics.s
    val count = withSQL {
      select(sqls"count(1)")
        .from(Statistics as st)
        .where
          .eq(st.targetMonth, new DateTime(now.getYearOfCentury(), now.getMonthOfYear(), 1, 0, 0))
    }.map(_.long(1)).single.apply.get

    if (count > 0) {
      System.exit(0)
    }
  }
  DB localTx { implicit s =>
    val d = Dataset.d
    val f = File.f
    val datasets: Seq[Dataset] = withSQL {
      select
        .from(Dataset as d)
        .leftJoin(File as f).on(sqls.eq(d.id, f.datasetId).and.isNull(f.deletedBy))
        .where
          .isNull(d.deletedBy)
          .and
          .isNull(d.deletedAt)
    }.one(Dataset(d)).toMany(File.opt(f)).map((dataset, files) => dataset.copy(files = files)).list.apply()

    Statistics.create(
      id = UUID.randomUUID().toString,
      targetMonth = new DateTime(now.getYear(), now.getMonthOfYear(), 1, 0, 0),
      datasetCount = datasets.size,
      realSize = datasets.flatMap(_.files).foldLeft(0L)(_ + _.realSize),
      compressedSize = datasets.foldLeft(0L)(_ + _.filesSize),
      s3Size = datasets.filter(_.s3State == 1).foldLeft(0L)(_ + _.filesSize),
      localSize = datasets.filter(_.localState == 1).foldLeft(0L)(_ + _.filesSize),
      createdBy = AppConf.systemUserId,
      createdAt = now,
      updatedBy = AppConf.systemUserId,
      updatedAt = now
    )
  }
  DBs.closeAll()
}
