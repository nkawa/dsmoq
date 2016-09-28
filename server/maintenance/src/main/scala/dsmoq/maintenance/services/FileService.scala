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
  val cantDeleteState = Seq(SaveStatus.Synchronizing, SaveStatus.Deleting)

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
          .orderBy(f.createdAt, f.id)
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
        _ <- execApplyPhysicalDelete(param.targets)
      } yield {
        ()
      }
    }
  }

  /**
   * ファイルデータ物理削除の削除対象のケースクラス
   *
   * @param invalids 削除に失敗したファイルデータ
   * @param records 物理削除を行うデータセットオブジェクト
   * @param deleteFiles 削除対象の物理ファイル・ディレクトリ
   */
  case class DeleteInfo(
    invalids: Seq[DeleteUtil.DeleteFailedData],
    records: Seq[persistence.File],
    deleteFiles: Seq[DeleteUtil.DeleteTarget]
  )

  /**
   * ファイルデータに物理削除を適用する。
   *
   * @param targets 物理削除対象のファイルID
   * @param s DBセッション
   * @return 処理結果
   *        Failure(ServiceException) ファイルデータの物理削除に失敗した場合
   *        Failure(ServiceException) 物理ファイルの物理削除に失敗した場合
   */
  def execApplyPhysicalDelete(targets: Seq[String])(implicit s: DBSession): Try[Unit] = {
    val deleteResult = Try {
      val f = persistence.File.f
      val d = persistence.Dataset.d
      val files = withSQL {
        select(f.result.*, d.result.*)
          .from(persistence.File as f)
          .innerJoin(persistence.Dataset as d).on(f.datasetId, d.id)
          .where
          .inUuid(f.id, targets)
      }.map { rs =>
        val file = persistence.File(f.resultName)(rs)
        val dataset = persistence.Dataset(d.resultName)(rs)
        (file, dataset)
      }.list.apply()
      val (deleteds, notDeleteds) = files.partition {
        case (f, _) => isLogicalDeleted(f)
      }
      val (synchronizingOrDeletings, records) = files.partition {
        case (_, d) => isSynchronizingState(d) || isDeletingState(d)
      }
      val invalids = notDeleteds.map {
        case (file, _) => DeleteUtil.DeleteFailedData("ファイル", "論理削除済みではない", file.name)
      } ++ synchronizingOrDeletings.map {
        case (file, _) => DeleteUtil.DeleteFailedData("ファイル", "ファイルが移動中、または削除中", file.name)
      }
      val deleteFiles = files.flatMap { case (f, d) => getDeleteFile(f, d) }
      DeleteInfo(invalids, records.map(_._1), deleteFiles)
    }
    for {
      DeleteInfo(invalids, records, deleteFiles) <- deleteResult
      _ <- deleteRecords(records)
      deleteFaileds <- DeleteUtil.deletePhysicalFiles(deleteFiles)
      _ <- DeleteUtil.deleteResultToTry(invalids, deleteFaileds)
    } yield {
      ()
    }
  }

  /**
   * 論理削除済みかを判定する。
   *
   * @param file ファイルデータオブジェクト
   * @return 論理削除済みであればtrue、そうでなければfalse
   */
  def isLogicalDeleted(file: persistence.File): Boolean = {
    file.deletedAt.isDefined && file.deletedBy.isDefined
  }

  /**
   * データセットのローカル、S3の保存状態が移動中であるかを判定する。
   * @param dataset データセットオブジェクト
   * @return 移動中であればtrue、そうでなければfalse
   */
  def isSynchronizingState(dataset: persistence.Dataset): Boolean = {
    dataset.localState == SaveStatus.Synchronizing || dataset.s3State == SaveStatus.Synchronizing
  }

  /**
   * データセットのローカル、S3の保存状態が削除中であるかを判定する。
   * @param dataset データセットオブジェクト
   * @return 削除中であればtrue、そうでなければfalse
   */
  def isDeletingState(dataset: persistence.Dataset): Boolean = {
    dataset.localState == SaveStatus.Deleting || dataset.s3State == SaveStatus.Deleting
  }

  /**
   * 削除対象のファイルデータの物理ファイルを取得する。
   *
   * @param file 削除対象のファイルデータ
   * @param dataset 削除対象ファイルの属するデータセット
   * @return 削除対象物理ファイル
   */
  def getDeleteFile(file: persistence.File, dataset: persistence.Dataset): Seq[DeleteUtil.DeleteTarget] = {
    val localDir = if (dataset.localState == SaveStatus.Saved) {
      val path = Paths.get(AppConfig.fileDir, dataset.id, file.id)
      Seq(DeleteUtil.LocalFile(path))
    } else {
      Seq.empty
    }
    val s3Dir = if (dataset.s3State == SaveStatus.Saved) {
      Seq(DeleteUtil.S3File(AppConfig.s3UploadRoot, s"${dataset.id}/${file.id}"))
    } else {
      Seq.empty
    }
    localDir ++ s3Dir
  }

  /**
   * ファイル関連のDBデータを物理削除する。
   *
   * @param files 削除対象のファイルIDのリスト
   * @param s DBセッション
   * @return 処理結果
   */
  def deleteRecords(files: Seq[persistence.File])(implicit s: DBSession): Try[Unit] = {
    Try {
      val f = persistence.File.f
      val h = persistence.FileHistory.fh
      val z = persistence.ZipedFiles.zf
      val ids = files.map(_.id)
      withSQL {
        delete
          .from(persistence.ZipedFiles as z)
          .where
          .exists(
            select
              .from(persistence.FileHistory as h)
              .where
              .eq(z.historyId, h.id)
              .and
              .inUuid(h.fileId, ids)
          )
      }.update.apply()
      withSQL {
        delete
          .from(persistence.FileHistory as h)
          .where
          .inUuid(h.fileId, ids)
      }.update.apply()
      withSQL {
        delete
          .from(persistence.File as f)
          .where
          .inUuid(f.id, ids)
      }.update.apply()
    }
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
