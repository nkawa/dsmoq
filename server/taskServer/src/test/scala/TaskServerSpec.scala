import java.io.File
import java.nio.file.Paths
import java.util.UUID

import akka.testkit.{ TestKit, TestActorRef }
import akka.actor.ActorSystem
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import dsmoq.taskServer.AppConf
import dsmoq.persistence._
import dsmoq.taskServer.Main.TaskParameter
import org.joda.time.DateTime
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods
import org.json4s.jackson.JsonMethods._
import org.scalatest.{FreeSpecLike, BeforeAndAfterAll, BeforeAndAfter }
import org.scalatest.Matchers._
import scalikejdbc.config.DBsWithEnv
import scalikejdbc._
import dsmoq.taskServer._
import scala.collection.JavaConversions._

class TaskServerSpec extends TestKit(ActorSystem()) with FreeSpecLike with BeforeAndAfter with BeforeAndAfterAll {
  private implicit val jsonFormats: Formats = DefaultFormats
  private val dummyFile = new File("../README.md")

  override def beforeAll() {
    super.beforeAll()
    DBsWithEnv("test").setup()
    System.setProperty(Main.EnvironmentKey, "test")
  }

  override def afterAll() {
    DBsWithEnv("test").close()
    super.afterAll()
    system.shutdown()
  }

  after {
    DB localTx { implicit s =>
      deleteAllData(deleteFrom(Dataset))
      deleteAllData(deleteFrom(Task))
      deleteAllData(deleteFrom(TaskLog))
    }
    val fileDirs = new java.io.File(AppConf.fileDir).listFiles()
    fileDirs.foreach { x =>
      if (x.isDirectory) {
        x.listFiles.foreach { y =>
          deleteFile(y.getPath)
        }
        x.delete()
      }
    }
    deleteAllFile()
  }

  "Test" - {
    "Main" - {
      "未実行のタスクのみを取得できるか" in {
        for(i <- 1 to 10) { createTask(0, UUID.randomUUID().toString, 0, true) }
        for(i <- 1 to 10) { createTask(1, UUID.randomUUID().toString, 0, true) }
        for(i <- 1 to 10) { createTask(2, UUID.randomUUID().toString, 0, true) }
        val tasks = Main.getNewTasks()
        tasks.length should be(10)
        tasks.forall(x => x.status == 0) should be(true)
      }

      "タスクを正しく処理に変換できるか" in {
        createTask(0, UUID.randomUUID().toString, 0, true)
        createTask(0, UUID.randomUUID().toString, 0, false)
        createTask(0, UUID.randomUUID().toString, 1, true)
        createTask(0, UUID.randomUUID().toString, 1, false)
        createTask(0, UUID.randomUUID().toString, 2, true, "dummyId")
        createTask(0, UUID.randomUUID().toString, 2, false, "dummyId")
        createTask(0, UUID.randomUUID().toString, 3, true)
        createTask(0, UUID.randomUUID().toString, 3, false)
        createTask(0, UUID.randomUUID().toString, 4, true)
        createTask(0, UUID.randomUUID().toString, 4, false)
        createTask(0, UUID.randomUUID().toString, 10, true)
        createTask(0, UUID.randomUUID().toString, 10, false)

        val tasks = Main.getNewTasks()
        val commands = tasks.map(x => Main.datasetToCommand(x.id, JsonMethods.parse(x.parameter).extract[TaskParameter]))
        commands.collect{ case MoveToLocal(taskId, datasetId, withDelete) if withDelete == false => true }.length should be(1)
        commands.collect{ case MoveToLocal(taskId, datasetId, withDelete) if withDelete == true => true }.length should be(1)
        commands.collect{ case MoveToS3(taskId, datasetId, withDelete) if withDelete == false => true }.length should be(1)
        commands.collect{ case MoveToS3(taskId, datasetId, withDelete) if withDelete == true => true }.length should be(1)
        commands.collect{ case Delete(taskId, datasetId, fileId) => true }.length should be(2)
        commands.collect{ case DeleteS3(taskId, datasetId) => true }.length should be(2)
        commands.collect{ case DeleteLocal(taskId, datasetId) => true }.length should be(2)
        commands.collect{ case DoNothing() => true }.length should be(2)
      }
    }
    "TaskActor" - {
      "S3へコピーできるか" in {
        val datasetId = createDataset(1, 2)
        val task = createTask(0, datasetId, 0, true)
        val dir = Paths.get(AppConf.fileDir, datasetId, "dummyFileId").toFile
        dir.mkdirs()
        val file = dir.toPath.resolve("dummyHistoryId").toFile
        file.createNewFile()
        val command = Main.datasetToCommand(task.id, JsonMethods.parse(task.parameter).extract[TaskParameter])
        val taskActor = TestActorRef[TaskActor]
        taskActor ! command

        val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
        val client = new AmazonS3Client(cre)
        client.listObjects(AppConf.s3UploadRoot).getObjectSummaries().map(x => x.getKey).exists(_.endsWith("dummyHistoryId")) should be(true)

        DB readOnly { implicit s =>
          val dataset = Dataset.find(datasetId).get
          dataset.localState should be(1)
          dataset.s3State should be(1)
        }
      }

      "S3へコピーできるか(ローカルは削除)" in {
        val datasetId = createDataset(1, 2)
        val task = createTask(0, datasetId, 0, false)
        val dir = Paths.get(AppConf.fileDir, datasetId, "dummyFileId").toFile
        dir.mkdirs()
        val file = dir.toPath.resolve("dummyHistoryId").toFile
        file.createNewFile()
        val command = Main.datasetToCommand(task.id, JsonMethods.parse(task.parameter).extract[TaskParameter])
        val taskActor = TestActorRef[TaskActor]
        taskActor ! command

        val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
        val client = new AmazonS3Client(cre)
        client.listObjects(AppConf.s3UploadRoot).getObjectSummaries().map(x => x.getKey).exists(_.endsWith("dummyHistoryId")) should be(true)
        DB readOnly { implicit s =>
          val t = Task.t
          val tasks = Task.findAllBy(sqls.eq(t.status, 0))
          tasks.size should be(1)
          val param = JsonMethods.parse(tasks.head.parameter).extract[TaskParameter]
          param.commandType should be(4)

          val dataset = Dataset.find(datasetId).get
          dataset.localState should be(3)
          dataset.s3State should be(1)
        }
      }

      "ローカルへコピーできるか" in {
        val datasetId = createDataset(2, 1)
        val task = createTask(0, datasetId, 1, true)
        val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
        val client = new AmazonS3Client(cre)
        client.putObject(AppConf.s3UploadRoot, datasetId + "/dummyFileId/dummyHistoryId/" + dummyFile.getName, dummyFile)

        val command = Main.datasetToCommand(task.id, JsonMethods.parse(task.parameter).extract[TaskParameter])
        val taskActor = TestActorRef[TaskActor]
        taskActor ! command

        Paths.get(AppConf.fileDir, datasetId, "dummyFileId", "dummyHistoryId", dummyFile.getName).toFile.exists() should be(true)

        DB readOnly { implicit s =>
          val dataset = Dataset.find(datasetId).get
          dataset.localState should be(1)
          dataset.s3State should be(1)
        }
      }

      "ローカルへコピーできるか(S3は削除)" in {
        val datasetId = createDataset(2, 1)
        val task = createTask(0, datasetId, 1, false)
        val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
        val client = new AmazonS3Client(cre)
        client.putObject(AppConf.s3UploadRoot, datasetId + "/dummyFileId/dummyHistoryId/" + dummyFile.getName, dummyFile)

        val command = Main.datasetToCommand(task.id, JsonMethods.parse(task.parameter).extract[TaskParameter])
        val taskActor = TestActorRef[TaskActor]
        taskActor ! command

        Paths.get(AppConf.fileDir, datasetId, "dummyFileId", "dummyHistoryId", dummyFile.getName).toFile.exists() should be(true)
        DB readOnly { implicit s =>
          val t = Task.t
          val tasks = Task.findAllBy(sqls.eq(t.status, 0))
          tasks.size should be(1)
          val param = JsonMethods.parse(tasks.head.parameter).extract[TaskParameter]
          param.commandType should be(3)

          val dataset = Dataset.find(datasetId).get
          dataset.localState should be(1)
          dataset.s3State should be(3)
        }
      }

      "ファイル一つだけ削除できるか" in {
        val datasetId = createDataset(1, 1)
        val task = createTask(0, datasetId, 2, false, "fileId1")
        val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
        val client = new AmazonS3Client(cre)
        client.putObject(AppConf.s3UploadRoot, datasetId + "/fileId1/dummyHistoryId/file1.md", dummyFile)
        client.putObject(AppConf.s3UploadRoot, datasetId + "/fileId2/dummyHistoryId/file2.md", dummyFile)

        val command = Main.datasetToCommand(task.id, JsonMethods.parse(task.parameter).extract[TaskParameter])
        val taskActor = TestActorRef[TaskActor]
        taskActor ! command

        client.listObjects(AppConf.s3UploadRoot).getObjectSummaries().map(x => x.getKey).exists(_.endsWith("file1.md")) should be(false)
        client.listObjects(AppConf.s3UploadRoot).getObjectSummaries().map(x => x.getKey).exists(_.endsWith("file2.md")) should be(true)
      }

      "ローカルフォルダを削除できるか" in {
        val datasetId = createDataset(3, 1)
        val task = createTask(0, datasetId, 4, false)
        val dir = Paths.get(AppConf.fileDir, datasetId, "dummyFileId", "dummyHistoryId").toFile
        dir.mkdirs()
        val file = dir.toPath.resolve("sample.txt").toFile
        file.createNewFile()
        val command = Main.datasetToCommand(task.id, JsonMethods.parse(task.parameter).extract[TaskParameter])
        val taskActor = TestActorRef[TaskActor]
        taskActor ! command

        file.exists() should be(false)

        DB readOnly { implicit s =>
          val dataset = Dataset.find(datasetId).get
          dataset.localState should be(0)
          dataset.s3State should be(1)
        }
      }

      "S3フォルダを削除できるか" in {
        val datasetId = createDataset(1, 3)
        val task = createTask(0, datasetId, 3, true)
        val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
        val client = new AmazonS3Client(cre)
        client.putObject(AppConf.s3UploadRoot, datasetId + "/dummyFileId/dummyHistoryId/" + dummyFile.getName, dummyFile)

        val command = Main.datasetToCommand(task.id, JsonMethods.parse(task.parameter).extract[TaskParameter])
        val taskActor = TestActorRef[TaskActor]
        taskActor ! command

        client.listObjects(AppConf.s3UploadRoot).getObjectSummaries().map(x => x.getKey).exists(_.endsWith(dummyFile.getName)) should be(false)

        DB readOnly { implicit s =>
          val dataset = Dataset.find(datasetId).get
          dataset.localState should be(1)
          dataset.s3State should be(0)
        }
      }

      "S3へコピーするが、コピー元フォルダが存在しない" in {
        val datasetId = createDataset(1, 2)
        val task = createTask(0, datasetId, 0, true)
        val command = Main.datasetToCommand(task.id, JsonMethods.parse(task.parameter).extract[TaskParameter])
        val taskActor = TestActorRef[TaskActor]
        taskActor ! command
        DB readOnly { implicit s =>
          val tl = TaskLog.tl
          val errorLog = withSQL {
            select.from(TaskLog as tl).where.eq(tl.logType, 9)
          }.map(TaskLog(tl)).single.apply()
          errorLog shouldNot be(None)

          val dataset = Dataset.find(datasetId).get
          dataset.localState should be(1)
          dataset.s3State should be(0)
        }
      }

      "ローカルへコピーするが、コピー元S3フォルダが存在しない" in {
        val datasetId = createDataset(2, 1)
        val task = createTask(0, datasetId, 1, true)
        val command = Main.datasetToCommand(task.id, JsonMethods.parse(task.parameter).extract[TaskParameter])
        val taskActor = TestActorRef[TaskActor]
        taskActor ! command

        DB readOnly { implicit s =>
          val tl = TaskLog.tl
          val errorLog = withSQL {
            select.from(TaskLog as tl).where.eq(tl.logType, 9)
          }.map(TaskLog(tl)).single.apply()
          errorLog shouldNot be(None)

          val dataset = Dataset.find(datasetId).get
          dataset.localState should be(0)
          dataset.s3State should be(1)
        }
      }
    }
  }

  private def createTask(status: Int, datasetId: String, taskType: Int, isSave: Boolean, fileId: String = "", executeAt: DateTime = DateTime.now): Task = {
    DB localTx { implicit s =>
      Task.create(
        id = UUID.randomUUID.toString,
        taskType = 0,
        parameter = compact(render(("commandType" -> JInt(taskType)) ~ ("datasetId" -> datasetId) ~ ("withDelete" -> JBool(!isSave)) ~ ("fileId" -> fileId))),
        status = status,
        executeAt = executeAt,
        createdBy = AppConf.systemUserId,
        createdAt = DateTime.now(),
        updatedBy = AppConf.systemUserId,
        updatedAt = DateTime.now()
      )
    }
  }

  private def createDataset(localState: Int, s3State: Int): String = {
    DB localTx { implicit s =>
      val id = UUID.randomUUID().toString
      Dataset.create(
        id = id,
        name = "",
        description = "",
        licenseId = UUID.randomUUID.toString,
        filesCount = 1,
        filesSize = 0,
        createdBy = AppConf.systemUserId,
        createdAt = DateTime.now(),
        updatedBy = AppConf.systemUserId,
        updatedAt = DateTime.now,
        localState = localState,
        s3State = s3State
      )
      id
    }
  }

  private def deleteAllData(query: SQLBuilder[UpdateOperation])(implicit s: DBSession) {
    withSQL {
      query
    }.update().apply
  }

  private def deleteAllFile(): Unit =
  {
    val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
    val client = new AmazonS3Client(cre)
    val l = client.listObjects(AppConf.s3UploadRoot)

    l.getObjectSummaries.toList.foreach { obj =>
      client.deleteObject(AppConf.s3UploadRoot, obj.getKey)
    }
    l.getCommonPrefixes.toList.foreach { obj =>
      client.deleteObject(AppConf.s3UploadRoot, obj)
    }
  }

  private def deleteFile(path: String) {
    val file = new File(path)
    if (file.isDirectory) {
      file.listFiles.foreach { f =>
        deleteFile(f.getPath)
      }
    }
    file.delete()
  }
}
