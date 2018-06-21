package dsmoq.statistics

import java.util.UUID

import dsmoq.persistence.{ Dataset, File, FileHistory, Statistics }
import org.joda.time.DateTime

import scalikejdbc.config.DBs
import scalikejdbc.{ DB, DBSession, delete, scalikejdbcSQLInterpolationImplicitDef, scalikejdbcSQLSyntaxToStringImplicitDef, select, withSQL }

object Main extends scala.App {
  val systemUserId = "dccc110c-c34f-40ed-be2c-7e34a9f1b8f0"

  val n = DateTime.now
  val now = new DateTime(n.getYear, n.getMonthOfYear(), 1, 0, 0)
  DBs.setupAll()
  DB localTx { implicit s =>
    val d = Dataset.d
    var date = withSQL {
      select
        .from(Dataset as d)
        .where
        .isNull(d.deletedBy)
        .and
        .isNull(d.deletedAt)
        .orderBy(d.createdBy)
        .limit(1)
    }.map(_.jodaDateTime(d.resultName.createdAt)).single().apply() match {
      case Some(date) => new DateTime(date.getYear(), date.getMonthOfYear(), 1, 0, 0)
      case None => {
        val one_month_past = now.minusMonths(1)
        new DateTime(one_month_past.getYear(), one_month_past.getMonthOfYear(), 1, 0, 0)
      }
    }

    // �f�[�^�Z�b�g��N���P�ʂŎ擾���A
    // ���łɑ��݂���ꍇ�́A�폜���āA�Ώ۔N���ɑ΂��āA�ēx���v����o�^����
    //
    // ���ݑz�肳��Ă���f�[�^�Z�b�g���ł���΁A���ɖ��͂Ȃ�

    val st = Statistics.s
    while (date.compareTo(now) < 0) {
      if (hasStatistics(date, 2)) {
        // ���łɑ��݂���΍폜����
        withSQL {
          delete.from(Statistics as st)
            .where.eq(st.targetMonth, date)
            .and.eq(st.statisticsType, 2)
        }.update().apply()
      }
      Statistics.create(
        id = UUID.randomUUID().toString,
        targetMonth = date,
        datasetCount = countDatasets(Some(date)),
        realSize = countRealSize(Some(date)),
        compressedSize = countFullSize(Some(date)),
        s3Size = countS3Size(Some(date)),
        statisticsType = 2,
        localSize = countLocalSize(Some(date)),
        createdBy = systemUserId,
        createdAt = now,
        updatedBy = systemUserId,
        updatedAt = now
      )
      date = date.minusMonths(-1)
    }
    if (hasStatistics(date, 1)) {
      // ���łɑ��݂���΍폜����
      withSQL {
        delete.from(Statistics as st)
          .where.eq(st.targetMonth, date)
          .and.eq(st.statisticsType, 1)
      }.update().apply()
    }
    Statistics.create(
      id = UUID.randomUUID().toString,
      targetMonth = date,
      datasetCount = countDatasets(None),
      realSize = countRealSize(None),
      compressedSize = countFullSize(None),
      s3Size = countS3Size(None),
      statisticsType = 1,
      localSize = countLocalSize(None),
      createdBy = systemUserId,
      createdAt = now,
      updatedBy = systemUserId,
      updatedAt = now
    )
  }
  DBs.closeAll()

  def hasStatistics(from: DateTime, statType: Int)(implicit s: DBSession): Boolean = {
    val st = Statistics.s
    val count = withSQL {
      select(sqls"count(1)")
        .from(Statistics as st)
        .where
        .eq(st.targetMonth, from)
        .and
        .eq(st.statisticsType, statType)
    }.map(_.long(1)).single.apply.get
    count > 0
  }

  def countDatasets(from: Option[DateTime])(implicit s: DBSession) = {
    val d = Dataset.d
    withSQL {
      select[Long](sqls"count(1)")
        .from(Dataset as d)
        .where
        .isNull(d.deletedAt)
        .and
        .isNull(d.deletedBy)
        .map { sql =>
          from match {
            case Some(f) => {
              sql.and
                .ge(d.createdAt, f)
                .and
                .lt(d.createdAt, f.minusMonths(-1))
            }
            case None => sql
          }
        }
    }.map(_.long(1)).single.apply.get
  }

  def countFullSize(from: Option[DateTime])(implicit s: DBSession) = {
    val d = Dataset.d
    withSQL {
      select[Long](sqls"coalesce(sum(d.files_size), 0) as total")
        .from(Dataset as d)
        .where
        .isNull(d.deletedAt)
        .and
        .isNull(d.deletedBy)
        .map { sql =>
          from match {
            case Some(f) => {
              sql.and
                .ge(d.createdAt, f)
                .and
                .lt(d.createdAt, f.minusMonths(-1))
            }
            case None => sql
          }
        }

    }.map(_.long("total")).single.apply.get
  }

  def countRealSize(from: Option[DateTime])(implicit s: DBSession) = {
    val d = Dataset.d
    val f = File.f
    val fh = FileHistory.fh

    withSQL {
      select[Long](sqls"coalesce(sum(fh.real_size), 0) as total")
        .from(Dataset as d)
        .innerJoin(File as f).on(f.datasetId, d.id)
        .innerJoin(FileHistory as fh).on(fh.fileId, f.id)
        .where
        .isNull(f.deletedAt)
        .and
        .isNull(f.deletedBy)
        .and
        .isNull(d.deletedAt)
        .and
        .isNull(d.deletedBy)
        .map { sql =>
          from match {
            case Some(f) => {
              sql.and
                .ge(d.createdAt, f)
                .and
                .lt(d.createdAt, f.minusMonths(-1))
            }
            case None => sql
          }
        }
    }.map(_.long("total")).single.apply.get
  }

  def countS3Size(from: Option[DateTime])(implicit s: DBSession) = {
    val d = Dataset.d
    withSQL {
      select[Long](sqls"coalesce(sum(d.files_size), 0) as total")
        .from(Dataset as d)
        .where
        .isNull(d.deletedAt)
        .and
        .isNull(d.deletedBy)
        .and
        .eq(d.s3State, 1)
        .map { sql =>
          from match {
            case Some(f) => {
              sql.and
                .ge(d.createdAt, f)
                .and
                .lt(d.createdAt, f.minusMonths(-1))
            }
            case None => sql
          }
        }
    }.map(_.long("total")).single.apply.get
  }

  def countLocalSize(from: Option[DateTime])(implicit s: DBSession) = {
    val d = Dataset.d
    withSQL {
      select[Long](sqls"coalesce(sum(d.files_size), 0) as total")
        .from(Dataset as d)
        .where
        .isNull(d.deletedAt)
        .and
        .isNull(d.deletedBy)
        .and
        .eq(d.localState, 1)
        .map { sql =>
          from match {
            case Some(f) => {
              sql.and
                .ge(d.createdAt, f)
                .and
                .lt(d.createdAt, f.minusMonths(-1))
            }
            case None => sql
          }
        }
    }.map(_.long("total")).single.apply.get
  }
}
