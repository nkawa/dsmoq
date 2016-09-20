package dsmoq.maintenance.services

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.joda.time.DateTime
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.maintenance.AppConfig
import dsmoq.maintenance.data.SearchResult
import dsmoq.maintenance.data.file.UpdateParameter
import dsmoq.maintenance.data.file.SearchCondition
import dsmoq.maintenance.data.file.SearchCondition.FileType
import dsmoq.maintenance.data.file.SearchResultFile
import dsmoq.persistence
import dsmoq.persistence.PostgresqlHelper.PgConditionSQLBuilder
import dsmoq.persistence.PostgresqlHelper.PgSQLSyntaxType
import scalikejdbc.ConditionSQLBuilder
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SelectSQLBuilder
import scalikejdbc.SQLSyntax
import scalikejdbc.interpolation.Implicits.scalikejdbcSQLInterpolationImplicitDef
import scalikejdbc.interpolation.Implicits.scalikejdbcSQLSyntaxToStringImplicitDef
import scalikejdbc.select
import scalikejdbc.sqls
import scalikejdbc.update
import scalikejdbc.withSQL

/**
 * ファイル処理サービス
 */
object FileService extends LazyLogging {
  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("MAINTENANCE_FILE_LOG")

  /**
   * サービス名
   */
  val SERVICE_NAME = "FileService"

  /**
   * ファイルを検索する。
   *
   * @param condition 検索条件
   * @return 検索結果
   */
  def search(condition: SearchCondition): SearchResult[SearchResultFile] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "search", condition))
    val f = persistence.File.f
    val d = persistence.Dataset.d
    val u = persistence.User.u
    def createSqlBase(select: SelectSQLBuilder[Unit]): ConditionSQLBuilder[Unit] = {
      select
        .from(persistence.File as f)
        .innerJoin(persistence.Dataset as d).on(d.id, f.datasetId)
        .leftJoin(persistence.User as u).on(sqls.eq(u.id, f.createdBy).and.eq(u.disabled, false))
        .where(
          sqls.toAndConditionOpt(
            condition.fileType match {
              case FileType.NotDeleted => Some(sqls.isNull(f.deletedBy).and.isNull(f.deletedAt))
              case FileType.Deleted => Some(sqls.isNotNull(f.deletedBy).and.isNotNull(f.deletedAt))
              case _ => None
            },
            condition.datasetId.map { id => sqls.eqUuid(f.datasetId, id) }
          )
        )
    }
    val limit = AppConfig.searchLimit
    val offset = (condition.page - 1) * limit
    DB.readOnly { implicit s =>
      val total = withSQL {
        createSqlBase(select(sqls.count))
      }.map(_.int(1)).single.apply().getOrElse(0)
      val records = withSQL {
        createSqlBase(select(f.result.*, d.result.*, u.result.*))
          .orderBy(f.createdAt)
          .offset(offset)
          .limit(limit)
      }.map { rs =>
        val file = persistence.File(f.resultName)(rs)
        val datasetName = rs.string(d.resultName.name)
        val createdBy = rs.stringOpt(u.resultName.name)
        SearchResultFile(
          datasetName = datasetName,
          id = file.id,
          name = file.name,
          size = file.fileSize,
          createdBy = createdBy,
          createdAt = file.createdAt,
          updatedAt = file.updatedAt,
          deletedAt = file.deletedAt
        )
      }.list.apply()
      SearchResult(
        from = offset + 1,
        to = offset + records.length,
        lastPage = (total / limit) + math.min(total % limit, 1),
        total = total,
        data = records
      )
    }
  }

  /**
   * Seqが空ではないことを確認する。
   *
   * @param seq 確認対象
   * @return 処理結果、Seqが空の場合 Failure(ServiceException)
   */
  def checkNonEmpty(seq: Seq[String]): Try[Unit] = {
    if (seq.isEmpty) {
      Failure(new ServiceException("ファイルが選択されていません。"))
    } else {
      Success(())
    }
  }

  /**
   * 論理削除を適用する。
   *
   * @param param 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) 要素が空の場合
   */
  def applyLogicalDelete(param: UpdateParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyLogicalDelete", param))
    DB.localTx { implicit s =>
      for {
        _ <- checkNonEmpty(param.targets)
        _ <- execApplyLogicalDelete(param.targets)
      } yield {
        ()
      }
    }
  }

  /**
   * ファイルに論理削除を適用する。
   *
   * @param ids 論理削除対象のファイルID
   * @param s DBセッション
   * @return 処理結果
   */
  def execApplyLogicalDelete(ids: Seq[String])(implicit s: DBSession): Try[Unit] = {
    Try {
      val timestamp = DateTime.now()
      val systemUserId = AppConfig.systemUserId
      val f = persistence.File.f
      val datasetIds = withSQL {
        select(f.result.datasetId)
          .from(persistence.File as f)
          .where
          .in(f.id, ids.map(sqls.uuid))
          .and
          .isNull(f.deletedAt)
          .and
          .isNull(f.deletedBy)
      }.map(_.string(f.resultName.datasetId)).list.apply()
      val fc = persistence.File.column
      withSQL {
        update(persistence.File)
          .set(
            fc.deletedAt -> timestamp,
            fc.deletedBy -> sqls.uuid(systemUserId),
            fc.updatedAt -> timestamp,
            fc.updatedBy -> sqls.uuid(systemUserId)
          )
          .where
          .in(fc.id, ids.map(sqls.uuid))
          .and
          .isNull(fc.deletedAt)
          .and
          .isNull(fc.deletedBy)
      }.update.apply()
      datasetIds.toSet.foreach { id: String =>
        updateDatasetFileStatus(id, timestamp)
      }
    }
  }

  /**
   * 論理削除解除を適用する。
   *
   * @param param 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) 要素が空の場合
   */
  def applyRollbackLogicalDelete(param: UpdateParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyRollbackLogicalDelete", param))
    DB.localTx { implicit s =>
      for {
        _ <- checkNonEmpty(param.targets)
        _ <- execApplyRollbackLogicalDelete(param.targets)
      } yield {
        ()
      }
    }
  }

  /**
   * ファイルに論理削除解除を適用する。
   *
   * @param ids 論理削除解除対象のファイルID
   * @param s DBセッション
   * @return 処理結果
   */
  def execApplyRollbackLogicalDelete(ids: Seq[String])(implicit s: DBSession): Try[Unit] = {
    Try {
      val timestamp = DateTime.now()
      val systemUserId = AppConfig.systemUserId
      val f = persistence.File.f
      val datasetIds = withSQL {
        select(f.result.datasetId)
          .from(persistence.File as f)
          .where
          .in(f.id, ids.map(sqls.uuid))
          .and
          .isNotNull(f.deletedAt)
          .and
          .isNotNull(f.deletedBy)
      }.map(_.string(f.resultName.datasetId)).list.apply()
      val fc = persistence.File.column
      withSQL {
        update(persistence.File)
          .set(
            fc.deletedAt -> None,
            fc.deletedBy -> None,
            fc.updatedAt -> timestamp,
            fc.updatedBy -> sqls.uuid(systemUserId)
          )
          .where
          .in(fc.id, ids.map(sqls.uuid))
          .and
          .isNotNull(fc.deletedAt)
          .and
          .isNotNull(fc.deletedBy)
      }.update.apply()
      datasetIds.toSet.foreach { id: String =>
        updateDatasetFileStatus(id, timestamp)
      }
    }
  }

  /**
   * データセットのファイル情報(ファイル件数、合計サイズ)を更新する。
   *
   * @param datasetId データセットID
   * @param timestamp タイムスタンプ
   */
  def updateDatasetFileStatus(
    datasetId: String,
    timestamp: DateTime
  )(implicit s: DBSession): Unit = {
    val systemUserId = AppConfig.systemUserId
    val f = persistence.File.f
    val allFiles = withSQL {
      select(f.result.*)
        .from(persistence.File as f)
        .where
        .eq(f.datasetId, sqls.uuid(datasetId))
        .and
        .isNull(f.deletedAt)
        .and
        .isNull(f.deletedBy)
    }.map(persistence.File(f.resultName)).list.apply()
    val totalFileSize = allFiles.foldLeft(0L)((a: Long, b: persistence.File) => a + b.fileSize)
    withSQL {
      val d = persistence.Dataset.column
      update(persistence.Dataset)
        .set(
          d.filesCount -> allFiles.size,
          d.filesSize -> totalFileSize,
          d.updatedBy -> sqls.uuid(systemUserId),
          d.updatedAt -> timestamp
        )
        .where
        .eq(d.id, sqls.uuid(datasetId))
    }.update.apply()
  }

  /**
   * 物理削除を適用する。
   *
   * @param param 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) 要素が空の場合
   */
  def applyPhysicalDelete(param: UpdateParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyPhysicalDelete", param))
    // TODO 実装
    Failure(new ServiceException("未実装です"))
  }
}
