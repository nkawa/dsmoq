package dsmoq.taskServer

import java.io.File
import java.nio.file.Paths
import java.util.UUID
import akka.actor.Actor
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import dsmoq.persistence.{Dataset, Task, TaskLog}
import org.joda.time.DateTime
import dsmoq.persistence.PostgresqlHelper._
import scalikejdbc._
import scala.concurrent.duration._
import scala.collection.JavaConversions._

class TaskActor extends Actor {
  private val START_LOG = 1
  private val END_LOG = 2
  private val ERROR_LOG = 9
  private val FINISH_TASK = 1
  private val ERROR_TASK = 2
  private val SAVED_STATE = 1
  private val DELETING_STATE = 3

  def receive = {
    case MoveToLocal(taskId, datasetId, withDelete) => {
      if (!isStarted(taskId)) {
        try {
          DB localTx { implicit s =>
            createTaskLog(taskId, START_LOG, "")

            val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
            implicit val client = new AmazonS3Client(cre)
            val filePaths = getS3FilePaths(datasetId)

            val missingFiles = flattenFilePath(Paths.get(AppConf.fileDir, datasetId).toFile).filterNot(x => filePaths.exists(y => x.getCanonicalPath.endsWith(y)))

            for (missing <- missingFiles) {
              missing.delete()
            }

            for (filePath <- filePaths) {
              FileManager.moveFromS3ToLocal(filePath)
            }
            finishTask(taskId, FINISH_TASK)
            createTaskLog(taskId, END_LOG, "")
            changeLocalState(datasetId, if (withDelete) {
              DELETING_STATE
            } else {
              SAVED_STATE
            })
          }
        } catch {
          case e: Exception => DB localTx { implicit s =>
            finishTask(taskId, ERROR_TASK)
            createTaskLog(taskId, ERROR_LOG, e.getMessage)
          }
        }
        if (withDelete) {
          implicit val dispatcher = context.system.dispatcher
          context.system.scheduler.scheduleOnce(72 hours) {
            deleteS3Files(datasetId, datasetId)
          }
        }
      }
    }
    case MoveToS3(taskId, datasetId, withDelete) => {
      if (!isStarted(taskId)) {
        try {
          DB localTx { implicit s =>
            createTaskLog(taskId, START_LOG, "")

            val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
            implicit val client = new AmazonS3Client(cre)

            val localFiles = flattenFilePath(Paths.get(AppConf.fileDir, datasetId).toFile).map(x => x.getCanonicalPath)
            val missingFiles = getS3FilePaths(datasetId).filterNot(x => localFiles.exists(y => y.endsWith(x)))
            for (missing <- missingFiles) {
              client.deleteObject(AppConf.s3UploadRoot, missing)
            }

            for (file <- localFiles) {
              val filePath = file.split("\\").reverse.take(4).reverse.mkString("/")
              FileManager.moveFromLocalToS3(filePath)
            }
            finishTask(taskId, FINISH_TASK)
            createTaskLog(taskId, END_LOG, "")
            changeS3State(datasetId, if (withDelete) {
              DELETING_STATE
            } else {
              SAVED_STATE
            })
          }
        } catch {
          case e: Exception => DB localTx { implicit s =>
            finishTask(taskId, ERROR_TASK)
            createTaskLog(taskId, ERROR_LOG, e.getMessage)
          }
        }
        if (withDelete) {
          implicit val dispatcher = context.system.dispatcher
          context.system.scheduler.scheduleOnce(72 hours) {
            val file = Paths.get(AppConf.fileDir, datasetId).toFile
            file.delete()
            DB localTx { implicit s =>
              changeLocalState(datasetId, SAVED_STATE)
            }
          }
        }
      }
    }
    case Delete(taskId, datasetId, fileId) => {
      if (!isStarted(taskId)) {
        DB localTx { implicit s =>
          createTaskLog(taskId, START_LOG, "")
          finishTask(taskId, FINISH_TASK)
          createTaskLog(taskId, END_LOG, "")
          changeS3State(datasetId, DELETING_STATE)
        }
        implicit val dispatcher = context.system.dispatcher
        context.system.scheduler.scheduleOnce(72 hours) {
          deleteS3Files(datasetId, datasetId + "/" + fileId)
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
      changeLocalState(datasetId, SAVED_STATE)
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
      case None =>
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
      case None =>
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
