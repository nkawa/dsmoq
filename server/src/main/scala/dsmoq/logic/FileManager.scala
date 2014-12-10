package dsmoq.logic

import java.io.File
import java.nio.file.Paths
import java.util.Calendar
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import dsmoq.AppConf
import org.scalatra.servlet.FileItem

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

  def downloadFromS3(filePath: String): String = {
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

}
