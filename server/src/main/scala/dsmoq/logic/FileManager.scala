package dsmoq.logic

import java.io.{FileOutputStream, File}
import java.nio.file.Paths
import java.util.Calendar
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{GetObjectRequest, GeneratePresignedUrlRequest}
import dsmoq.AppConf
import org.scalatra.servlet.FileItem
import scalikejdbc._

import scalax.io.Resource

object FileManager {

  def uploadToLocal(datasetId: String, fileId: String, historyId: String, file: FileItem) = {
    val datasetDir = Paths.get(AppConf.fileDir, datasetId).toFile
    if (!datasetDir.exists()) datasetDir.mkdir()

    val fileDir = datasetDir.toPath.resolve(fileId).toFile
    if (!fileDir.exists()) fileDir.mkdir()

    val historyDir = fileDir.toPath.resolve(historyId).toFile
    if (!historyDir.exists()) historyDir.mkdir()

    file.write(historyDir.toPath.resolve(file.getName).toFile)
  }

  def downloadFromLocal(filePath: String): File = {
    val fullPath = Paths.get(AppConf.fileDir, filePath).toFile
    if (!fullPath.exists()) throw new RuntimeException("file not found")
    new File(fullPath.toString)
  }

  def downloadFromS3(filePath: String): File = {
    val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
    val client = new AmazonS3Client(cre)
    val request = new GetObjectRequest(AppConf.s3UploadRoot, filePath)
    val obj = client.getObject(request)
    val in = obj.getObjectContent()
    val input = Resource.fromInputStream(in)
    val splitDirs = obj.getKey.split("/")
    val datasetDir = Paths.get(AppConf.tempDir, splitDirs(0)).toFile
    if (!datasetDir.exists()) datasetDir.mkdir()

    val fileDir = datasetDir.toPath.resolve(splitDirs(1)).toFile
    if (!fileDir.exists()) fileDir.mkdir()

    val historyDir = fileDir.toPath.resolve(splitDirs(2)).toFile
    if (!historyDir.exists()) historyDir.mkdir()

    val file = Paths.get(AppConf.tempDir, obj.getKey).toFile
    use(new FileOutputStream(file)) { out =>
      out.write(input.byteArray)
      ()
    }
    file
  }

  def downloadFromS3Url(filePath: String): String = {
    val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
    val client = new AmazonS3Client(cre)
    // 有効期限(1分)
    val cal = Calendar.getInstance()
    cal.add(Calendar.MINUTE, 1)
    val limit = cal.getTime()

    // URLを生成
    val url = client.generatePresignedUrl(new GeneratePresignedUrlRequest(AppConf.s3UploadRoot, filePath).withExpiration(limit))
    url.toString
  }

  private def use[T1 <: Closable, T2](resource: T1)(f: T1 => T2): T2 = {
    try {
      f(resource)
    } finally {
      try {
        resource.close()
      } catch {
        case e: Exception =>
      }
    }
  }

}
