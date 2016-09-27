package dsmoq.maintenance.services

import java.nio.file.Path
import java.nio.file.Files

import scala.util.Try
import scala.util.Success
import scala.util.Failure

import org.slf4j.Marker
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client

import dsmoq.persistence
import dsmoq.persistence.PostgresqlHelper.PgConditionSQLBuilder
import dsmoq.persistence.PostgresqlHelper.PgSQLSyntaxType
import org.scalatra.util.MultiMap
import scalikejdbc.ConditionSQLBuilder
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SelectSQLBuilder
import scalikejdbc.SQLSyntax
import scalikejdbc.convertJavaSqlTimestampToConverter
import scalikejdbc.interpolation.Implicits.scalikejdbcSQLInterpolationImplicitDef
import scalikejdbc.interpolation.Implicits.scalikejdbcSQLSyntaxToStringImplicitDef
import scalikejdbc.select
import scalikejdbc.sqls
import scalikejdbc.withSQL

import dsmoq.maintenance.AppConfig

/**
 * 物理削除処理のユーティリティ
 */
object DeleteUtil extends LazyLogging {
  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("MAINTENANCE_DELETE_SUCCESS_LOG")

  /**
   * 削除失敗時に使用するログマーカー
   */
  val DELETE_FAILURE_LOG_MARKER = MarkerFactory.getMarker("MAINTENANCE_DELETE_FAILURE_LOG")

  /**
   * 削除対象の物理ファイルを表す型
   */
  trait DeleteTarget

  /**
   * ローカルに保存された削除対象の物理ファイルを表すケースクラス
   */
  case class LocalFile(path: Path) extends DeleteTarget

  /**
   * S3に保存された削除対象の物理ファイルを表すケースクラス
   */
  case class S3File(bucket: String, path: String) extends DeleteTarget

  /**
   * 削除できなかったデータを表すケースクラス
   */
  case class DeleteFailedData(dataType: String, reason: String, name: String)

  /**
   * 指定されたディレクトリを含んで、それ以下の物理ファイルを一括削除する。
   *
   * @param dirs 削除対象の物理ファイルディレクトリのリスト
   * @return 処理結果
   *        Success(Seq[String]) 削除に失敗した物理ファイル情報のリスト
   */
  def deletePhysicalFiles(dirs: Seq[DeleteTarget]): Try[Seq[String]] = {
    Try {
      lazy val crediential = new BasicAWSCredentials(AppConfig.s3AccessKey, AppConfig.s3SecretKey)
      lazy val client = new AmazonS3Client(crediential)
      val targets = dirs.flatMap {
        case LocalFile(path) => localDirToDeleteTargets(path)
        case S3File(bucket, path) => s3DirToDeleteTargets(client, bucket, path)
      }
      val results = for {
        target <- targets
      } yield {
        target match {
          case LocalFile(path) => {
            val message = s"Location: Local, Path:${path.toAbsolutePath.toString}"
            try {
              Files.deleteIfExists(path)
              logger.info(LOG_MARKER, s"DELETE_SUCCEED: ${message}")
              Success(())
            } catch {
              case e: Exception => {
                logger.error(LOG_MARKER, e.getMessage, e)
                logger.error(DELETE_FAILURE_LOG_MARKER, s"DELETE_FAILED: ${message}")
                Failure(new ServiceException(message))
              }
            }
          }
          case S3File(bucket, path) => {
            val message = s"Location: S3, Bucket: ${bucket}, Path:${path}"
            try {
              client.deleteObject(bucket, path)
              logger.info(LOG_MARKER, s"DELETE_SUCCEED: ${message}")
              Success(())
            } catch {
              case e: Exception => {
                logger.error(LOG_MARKER, e.getMessage, e)
                logger.error(DELETE_FAILURE_LOG_MARKER, s"DELETE_FAILED: ${message}")
                Failure(new ServiceException(message))
              }
            }
          }
        }
      }
      results.collect { case Failure(e) => e.getMessage }
    }
  }

  /**
   * 対象のImageが削除可能かを判定する。
   *
   * @param imageId 対象の画像ID
   * @param datasetId 対象のImageを使用しているかのチェック対象から除外するデータセットID
   * @param groupId 対象のImageを使用しているかのチェック対象から除外するグループID
   * @param s DBセッション
   * @return 判定結果。
   * 対象の画像がデフォルトイメージの場合は、falseを返却する。
   * 対象の画像を使用しているデータセット、グループが1件以上存在する場合は、falseを返却する。
   * それ以外の場合はtrueを返却する。
   */
  def canDeleteImage(
    imageId: String,
    datasetIds: Seq[String] = Seq.empty,
    groupIds: Seq[String] = Seq.empty
  )(implicit s: DBSession): Boolean = {
    if (AppConfig.defaultImageIds.contains(imageId)) {
      return false
    }
    val di = persistence.DatasetImage.di
    val datasetCount = withSQL {
      select(sqls.count)
        .from(persistence.DatasetImage as di)
        .where(
          sqls.toAndConditionOpt(
            Some(sqls.eq(di.imageId, sqls.uuid(imageId))),
            if (datasetIds.isEmpty) {
              None
            } else {
              Some(sqls.notIn(di.datasetId, datasetIds.map(sqls.uuid)))
            }
          )
        )
    }.map(_.int(1)).single.apply().getOrElse(0)
    val gi = persistence.GroupImage.gi
    val groupCount = withSQL {
      select(sqls.count)
        .from(persistence.GroupImage as gi)
        .where(
          sqls.toAndConditionOpt(
            Some(sqls.eq(gi.imageId, sqls.uuid(imageId))),
            if (groupIds.isEmpty) {
              None
            } else {
              Some(sqls.notIn(gi.groupId, groupIds.map(sqls.uuid)))
            }
          )
        )
    }.map(_.int(1)).single.apply().getOrElse(0)
    (datasetCount + groupCount) == 0
  }

  /**
   * 削除結果ををTry型に変換する。
   *
   * @param cantDeletes 削除に失敗したデータのリスト
   * @param physicalFiles 削除に失敗した物理ファイル情報のリスト
   * @return 変換結果
   *        Success(Unit) cantDeletes, physicalFilesがともに空の場合
   *        Failure(ServiceException) cantDelete, physicalFilesのいずれかに要素がある場合
   */
  def deleteResultToTry(cantDeletes: Seq[DeleteFailedData], physicalFiles: Seq[String]): Try[Unit] = {
    if (cantDeletes.isEmpty && physicalFiles.isEmpty) {
      return Success(())
    }
    val cantDeleteErrorDetails = cantDeletes.groupBy(_.dataType).map {
      case (dataType, data) =>
        val messages = data.map { d => s"${d.name}, Reason: ${d.reason}" }
        ErrorDetail(s"一部の${dataType}が削除できませんでした。", messages)
    }.toSeq
    val errorDetails = if (physicalFiles.isEmpty) {
      cantDeleteErrorDetails
    } else {
      cantDeleteErrorDetails :+ ErrorDetail("一部のファイルが削除できませんでした。", physicalFiles)
    }
    Failure(new ServiceException(
      message = "物理削除時にエラーが発生しました。",
      details = errorDetails,
      withLogging = false
    ))
  }

  /**
   * ローカルのディレクトリの中身をDeleteTargetとして変換する。
   *
   * @param dir ローカルティレクトリのパス
   * @return 変換結果
   */
  def localDirToDeleteTargets(dir: Path): Seq[DeleteTarget] = {
    val file = dir.toFile
    if (!file.exists) {
      return Seq.empty
    } else if (file.isFile) {
      return Seq(LocalFile(dir))
    }
    file.list.map(dir.resolve).filter(_.toFile.isFile).map(LocalFile.apply).toSeq
  }

  /**
   * S3のディレクトリの中身をDeleteTargetとして変換する。
   *
   * @param client AmazonS3Client
   * @param bucket bucket名
   * @param dir ディレクトリのパス
   * @return 変換結果
   */
  def s3DirToDeleteTargets(client: AmazonS3Client, bucket: String, dir: String): Seq[DeleteTarget] = {
    import scala.collection.JavaConverters._
    val files = client.listObjects(bucket, dir).getObjectSummaries().asScala.map(_.getKey).filterNot(_.endsWith("/"))
    files.map(file => S3File(bucket, file))
  }
}
