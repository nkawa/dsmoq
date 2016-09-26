package dsmoq.maintenance.services

import java.nio.file.Paths

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
import org.scalatra.util.MultiMap
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
import scalikejdbc.delete
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
   * 物理削除不能な保存状態
   */
  val cantDeleteState = Seq(persistence.SaveStatus.Synchronizing, persistence.SaveStatus.Deleting)

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
    // datasetIdがUUID形式でない場合は、それに合致するIDは存在しないため、検索結果は0になる
    if (condition.datasetId.map(!Util.isUUID(_)).getOrElse(false)) {
      return SearchResult(
        offset,
        limit,
        0,
        Seq.empty
      )
    }
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
        offset,
        limit,
        total,
        records
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
          .inUuid(f.id, ids)
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
          .inUuid(fc.id, ids)
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
  def applyCancelLogicalDelete(param: UpdateParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyCancelLogicalDelete", param))
    DB.localTx { implicit s =>
      for {
        _ <- checkNonEmpty(param.targets)
        _ <- execApplyCancelLogicalDelete(param.targets)
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
  def execApplyCancelLogicalDelete(ids: Seq[String])(implicit s: DBSession): Try[Unit] = {
    Try {
      val timestamp = DateTime.now()
      val systemUserId = AppConfig.systemUserId
      val f = persistence.File.f
      val datasetIds = withSQL {
        select(f.result.datasetId)
          .from(persistence.File as f)
          .where
          .inUuid(f.id, ids)
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
          .inUuid(fc.id, ids)
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
   *        Failure(ServiceException) ファイルの物理削除に失敗した場合
   *        Failure(ServiceException) 物理ファイルの物理削除に失敗した場合
   */
  def applyPhysicalDelete(param: UpdateParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyPhysicalDelete", param))
    DB.localTx { implicit s =>
      for {
        _ <- checkNonEmpty(param.targets)
        (cantDeleteFiles, deleteTargets) <- execApplyPhysicalDelete(param.targets)
        cantDeletePhysicalFiles <- DeleteUtil.deletePhysicalFiles(LOG_MARKER, deleteTargets)
        _ <- DeleteUtil.deleteResultToTry(cantDeleteFiles, cantDeletePhysicalFiles)
      } yield {
        ()
      }
    }
  }

  /**
   * ファイルに物理削除を適用する。
   *
   * @param ids 物理削除対象のファイルID
   * @param s DBセッション
   * @return 処理結果
   *        Seq[DeleteUtil.CantDeleteData] 削除に失敗したファイルデータのリスト
   *        Seq[DeleteUtil.DeleteTarget] 削除対象の物理ファイルディレクトリのリスト
   */
  def execApplyPhysicalDelete(
    ids: Seq[String]
  )(implicit s: DBSession): Try[(Seq[DeleteUtil.CantDeleteData], Seq[DeleteUtil.DeleteTarget])] = {
    Try {
      val f = persistence.File.f
      val files = withSQL {
        select
          .from(persistence.File as f)
          .where
          .inUuid(f.id, ids)
      }.map(persistence.File(f.resultName)).list.apply()
      val deleteResults = files.map { file =>
        val dataset = persistence.Dataset.find(file.datasetId)
        val (localState, s3State) = dataset match {
          case Some(dataset) => (dataset.localState, dataset.s3State)
          case None => (file.localState, file.s3State)
        }
        if (!file.deletedAt.isDefined || !file.deletedBy.isDefined) {
          // 削除対象に関連付けられたファイルが論理削除済みでない場合は削除対象から外す
          (Some(DeleteUtil.CantDeleteData("ファイル", "論理削除済みではない", file.name)), Seq.empty)
        } else if (cantDeleteState.contains(localState) || cantDeleteState.contains(s3State)) {
          // 削除対象に関連付けられたファイルが移動中、または削除中の場合は削除対象から外す
          (Some(DeleteUtil.CantDeleteData("ファイル", "ファイルが移動中、または削除中", file.name)), Seq.empty)
        } else {
          val deleteTargets = physicalDeleteFile(file, localState, s3State)
          (None, deleteTargets)
        }
      }
      val cantDeleteFiles = deleteResults.collect { case (Some(cantDelete), _) => cantDelete }
      val deleteTargets = deleteResults.collect { case (None, deleteTargets) => deleteTargets }.flatten
      (cantDeleteFiles, deleteTargets)
    }
  }

  /**
   * File, FileHistory, ZipedFilesを物理削除する。
   *
   * @param file Fileオブジェクト
   * @param localState Localの保存状況
   * @param s3State s3の保存状況
   * @param s DBセッション
   * @return 削除対象の物理ファイルディレクトリのリスト
   */
  def physicalDeleteFile(
    file: persistence.File,
    localState: Int,
    s3State: Int
  )(implicit s: DBSession): Seq[DeleteUtil.DeleteTarget] = {
    val fh = persistence.FileHistory.fh
    val fileHistoryIds = withSQL {
      select(fh.result.id)
        .from(persistence.FileHistory as fh)
        .where
        .eq(fh.fileId, sqls.uuid(file.id))
    }.map(_.string(fh.resultName.id)).list.apply()
    deleteZipedFiles(fileHistoryIds)
    deleteFileHistories(file.id)
    deleteFile(file.id)
    val localFiles: Seq[DeleteUtil.DeleteTarget] = if (localState == persistence.SaveStatus.Saved) {
      Seq(DeleteUtil.LocalFile(Paths.get(AppConfig.fileDir, file.datasetId, file.id)))
    } else {
      Seq.empty
    }
    val s3Files: Seq[DeleteUtil.DeleteTarget] = if (s3State == persistence.SaveStatus.Saved) {
      Seq(DeleteUtil.S3File(AppConfig.s3UploadRoot, s"${file.datasetId}/${file.id}"))
    } else {
      Seq.empty
    }
    localFiles ++ s3Files
  }

  /**
   * Fileを物理削除する。
   *
   * @param fileId ファイルID
   * @param s DBセッション
   */
  def deleteFile(fileId: String)(implicit s: DBSession): Unit = {
    withSQL {
      delete
        .from(persistence.File)
        .where
        .eq(persistence.File.column.id, sqls.uuid(fileId))
    }.update.apply()
  }

  /**
   * FileHistoryを物理削除する。
   *
   * @param fileId ファイルID
   * @param s DBセッション
   */
  def deleteFileHistories(fileId: String)(implicit s: DBSession): Unit = {
    withSQL {
      delete
        .from(persistence.FileHistory)
        .where
        .eq(persistence.FileHistory.column.fileId, sqls.uuid(fileId))
    }.update.apply()
  }

  /**
   * ZipedFilesを物理削除する。
   *
   * @param fileHistoryIds ファイル履歴ID
   * @param s DBセッション
   */
  def deleteZipedFiles(fileHistoryIds: Seq[String])(implicit s: DBSession): Unit = {
    withSQL {
      delete
        .from(persistence.ZipedFiles)
        .where
        .in(persistence.ZipedFiles.column.historyId, fileHistoryIds.map(sqls.uuid))
    }.update.apply()
  }

  /**
   * POST /file/applyの更新操作を行う。
   *
   * @param params 入力パラメータ
   * @param multiParams 入力パラメータ(複数取得可能)
   * @return 処理結果
   *        Failure(ServiceException) 存在しない操作の場合
   */
  def applyChange(params: Map[String, String], multiParams: MultiMap): Try[Unit] = {
    val param = UpdateParameter.fromMap(multiParams)
    val result = params.get("update") match {
      case Some("logical_delete") => applyLogicalDelete(param)
      case Some("cancel_logical_delete") => applyCancelLogicalDelete(param)
      case Some("physical_delete") => applyPhysicalDelete(param)
      case _ => Failure(new ServiceException("無効な操作です。"))
    }
    Util.withErrorLogging(logger, LOG_MARKER, result)
  }
}
