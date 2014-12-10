package dsmoq.taskServer

import java.io.File
import java.nio.file.Paths
import java.util.UUID
import akka.actor.Actor
import akka.actor.ActorLogging
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import dsmoq.persistence.{Dataset, Task, TaskLog}
import org.joda.time.DateTime
import dsmoq.persistence.PostgresqlHelper._
import scalikejdbc._
import scala.concurrent.duration._
import scala.collection.JavaConversions._

class TaskActor extends Actor with ActorLogging {
  private val START_LOG = 1
  private val END_LOG = 2
  private val ERROR_LOG = 9
  private val FINISH_TASK = 1
  private val ERROR_TASK = 2
  private val NOT_SAVED_STATE = 0
  private val SAVED_STATE = 1
  private val DELETING_STATE = 3

  def receive = {
    case MoveToLocal(taskId, datasetId, withDelete) => {
      log.info("MoveToLocal Start")
      if (!isStarted(taskId)) {
        try {
          DB localTx { implicit s =>
            createTaskLog(taskId, START_LOG, "")
          }
          val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
          implicit val client = new AmazonS3Client(cre)
          val filePaths = getS3FilePaths(datasetId)
          if (filePaths.isEmpty) {
            throw new IllegalStateException("コピー元のS3バケットにファイルが存在していません")
          }

          for (filePath <- filePaths) {
            log.info("UploadToLocal " + filePath)
            FileManager.moveFromS3ToLocal(filePath)
          }
          DB localTx { implicit s =>
            finishTask(taskId, FINISH_TASK)
            createTaskLog(taskId, END_LOG, "")
            changeLocalState(datasetId, SAVED_STATE)
            if (withDelete) {
              changeS3State(datasetId, DELETING_STATE)
            }
          }
        } catch {
          case e: Exception => DB localTx { implicit s =>
            finishTask(taskId, ERROR_TASK)
            createTaskLog(taskId, ERROR_LOG, e.getMessage)
            log.error(e, "MoveToLocal Failed.")
          }
        }
        if (withDelete) {
          implicit val dispatcher = context.system.dispatcher
          context.system.scheduler.scheduleOnce(Duration(AppConf.delete_cycle, AppConf.delete_unit)) {
            try
            {
            deleteS3Files(datasetId, datasetId)
            log.info("MoveToLocal datasetId=%s Delete End".format(datasetId))
            } catch {
              case e: Exception => log.error(e, "MoveToLocal Delete Failed.")
            }
          }
        }
        log.info("MoveToLocal End")
      }
    }
    case MoveToS3(taskId, datasetId, withDelete) => {
      log.info("MoveToS3 Start")
      if (!isStarted(taskId)) {
        try {
          DB localTx { implicit s =>
            createTaskLog(taskId, START_LOG, "")
          }
          val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
          val client = new AmazonS3Client(cre)

          val localFiles = flattenFilePath(Paths.get(AppConf.fileDir, datasetId).toFile).map(x => x.getCanonicalPath)
          if (localFiles.isEmpty) {
            throw new IllegalStateException("コピー元のフォルダにファイルが存在していません")
          }

          for (file <- localFiles) {
            val separator = if (System.getProperty("file.separator") == "\\") { System.getProperty("file.separator") * 2 } else { System.getProperty("file.separator") }

            val filePath = file.split(separator).reverse.take(4).reverse.mkString("/")
            log.info("UploadToS3 " + filePath)
            FileManager.moveFromLocalToS3(filePath, client)
          }
          DB localTx { implicit s =>
            finishTask(taskId, FINISH_TASK)
            createTaskLog(taskId, END_LOG, "")
            changeS3State(datasetId, SAVED_STATE)
            if (withDelete) {
              changeLocalState(datasetId, DELETING_STATE)
            }
          }
        } catch {
          case e: Exception => DB localTx { implicit s =>
            finishTask(taskId, ERROR_TASK)
            createTaskLog(taskId, ERROR_LOG, e.getMessage)
            log.error(e, "MoveToS3 Failed.")
          }
        }
        if (withDelete) {
          implicit val dispatcher = context.system.dispatcher
          context.system.scheduler.scheduleOnce(Duration(AppConf.delete_cycle, AppConf.delete_unit)) {
            try {
              val file = Paths.get(AppConf.fileDir, datasetId).toFile
              deleteLocalFiles(file)
              DB localTx { implicit s =>
                changeLocalState(datasetId, NOT_SAVED_STATE)
              }
              log.info("MoveToS3 datasetId=%s Delete End".format(datasetId))
            } catch {
              case e: Exception => log.error(e, "MoveToS3 Delete Failed.")
            }
          }
        }
        log.info("MoveToS3 End")
      }
    }
    case Delete(taskId, datasetId, fileId) => {
      log.info("Delete Start")
      if (!isStarted(taskId)) {
        DB localTx { implicit s =>
          createTaskLog(taskId, START_LOG, "")
          finishTask(taskId, FINISH_TASK)
          createTaskLog(taskId, END_LOG, "")
          changeS3State(datasetId, DELETING_STATE)
        }
        implicit val dispatcher = context.system.dispatcher
        context.system.scheduler.scheduleOnce(Duration(AppConf.delete_cycle, AppConf.delete_unit)) {
          try
          {
            deleteS3Files(datasetId, datasetId + "/" + fileId)
          } catch {
            case e: Exception => log.error(e, "Delete Failed.")
          }
        }
      }
    }
    case DoNothing => // do nothing
  }

  def deleteS3Files(datasetId: String, path: String): Unit = {
    val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
    implicit val client = new AmazonS3Client(cre)
    val filePaths = getS3FilePaths(path)
    for (filePath <- filePaths) {
      client.deleteObject(AppConf.s3UploadRoot, filePath)
    }
    DB localTx { implicit s =>
      changeS3State(datasetId, NOT_SAVED_STATE)
    }
  }

  def deleteLocalFiles(file: File): Unit = file match {
    case f if f.isFile => f.delete()
    case f if f.isDirectory => {
      f.listFiles().foreach(deleteLocalFiles(_))
      f.delete()
    }
  }

  def getS3FilePaths(datasetId: String)(implicit client: AmazonS3Client) = client.listObjects(AppConf.s3UploadRoot, datasetId).getObjectSummaries().map(_.getKey).filterNot(_.endsWith("/"))

  def isStarted(taskId: String): Boolean = {
    DB readOnly { implicit s =>
      val tl = TaskLog.tl
      withSQL {
        select(sqls"count(1)")
          .from(TaskLog as tl)
          .where
          .eq(tl.taskId, sqls.uuid(taskId))
      }.map(_.long(1)).single().apply().get > 0
    }
  }

  def createTaskLog(taskId: String, logType: Int, message: String)(implicit s: DBSession): Unit = {
    TaskLog.create(
      id = UUID.randomUUID.toString,
      taskId = taskId,
      logType = logType,
      message = message,
      createdBy = AppConf.systemUserId,
      createdAt = DateTime.now()
    )
  }

  def finishTask(taskId: String, status: Int)(implicit s: DBSession): Unit = {
    Task.find(taskId) match {
      case Some(t) => {
        Task(
          id = t.id,
          taskType =  t.taskType,
          parameter = t.parameter,
          status = status,
          createdBy = t.createdBy,
          createdAt = t.createdAt,
          updatedBy = AppConf.systemUserId,
          updatedAt = DateTime.now()
        ).save()
      }
      case None =>
    }
  }

  def changeS3State(datasetId: String, s3State: Int)(implicit s: DBSession): Unit = {
    Dataset.find(datasetId) match {
      case Some(d) => {
        Dataset(
          id = d.id,
          name = d.name,
          description = d.description,
          licenseId = d.licenseId,
          filesCount = d.filesCount,
          filesSize = d.filesSize,
          createdBy = d.createdBy,
          createdAt = d.createdAt,
          updatedBy = AppConf.systemUserId,
          updatedAt = DateTime.now,
          deletedAt = d.deletedAt,
          deletedBy = d.deletedBy,
          localState = d.localState,
          s3State = s3State
        ).save()
      }
      case None => throw new IllegalArgumentException("datasetId=%sに対応するDatasetが存在しません".format(datasetId))
    }
  }

  def changeLocalState(datasetId: String, localState: Int)(implicit s: DBSession): Unit = {
    Dataset.find(datasetId) match {
      case Some(d) => {
        Dataset(
          id = d.id,
          name = d.name,
          description = d.description,
          licenseId = d.licenseId,
          filesCount = d.filesCount,
          filesSize = d.filesSize,
          createdBy = d.createdBy,
          createdAt = d.createdAt,
          updatedBy = AppConf.systemUserId,
          updatedAt = DateTime.now,
          deletedAt = d.deletedAt,
          deletedBy = d.deletedBy,
          localState = localState,
          s3State = d.s3State
        ).save()
      }
      case None => throw new IllegalArgumentException("datasetId=%sに対応するDatasetが存在しません".format(datasetId))
    }
  }

  def flattenFilePath(file: File): List[File] = file match {
    case f:File if f.isDirectory => {
      def flatten(files: List[File]): List[File] = files match {
        case x :: xs if x.isDirectory => flatten(x.listFiles.toList) ::: flatten(xs)
        case x :: xs if x.isFile => x :: flatten(xs)
        case Nil => List()
      }
      flatten(f.listFiles.toList)
    }
    case f: File if f.isFile => List(f)
  }
}
