package dsmoq.logic

import java.io.File
import java.io.InputStream
import java.nio.file.Paths
import java.util.Calendar

import scala.language.reflectiveCalls

import org.scalatra.servlet.FileItem

import com.amazonaws.HttpMethod
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.ResponseHeaderOverrides

import dsmoq.AppConf
import scalikejdbc.Closable

object FileManager {

  def uploadToLocal(datasetId: String, fileId: String, historyId: String, file: FileItem): Unit = {
    val datasetDir = Paths.get(AppConf.fileDir, datasetId).toFile
    if (!datasetDir.exists()) datasetDir.mkdir()

    val fileDir = datasetDir.toPath.resolve(fileId).toFile
    if (!fileDir.exists()) fileDir.mkdir()

    file.write(fileDir.toPath.resolve(historyId).toFile)
  }

  def downloadFromLocal(filePath: String): File = {
    val fullPath = Paths.get(AppConf.fileDir, filePath).toFile
    if (!fullPath.exists()) throw new RuntimeException("file not found")
    new File(fullPath.toString)
  }

  def downloadFromS3(filePath: String, start: Long, end: Long): InputStream = {
    val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
    val client = new AmazonS3Client(cre)
    val request = new GetObjectRequest(AppConf.s3UploadRoot, filePath)
    request.setRange(start, end)
    val obj = client.getObject(request)
    obj.getObjectContent()
  }

  /**
   * S3の署名付きURLを生成する。
   * @param filePath S3上における対象ファイルのファイルパス
   * @param fileName 対象ファイル名
   * @param isHead HEADリクエストか否か
   * @return 署名付きURL
   */
  def generateS3PresignedURL(filePath: String, fileName: String, isHead: Boolean): String = {
    val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
    val client = new AmazonS3Client(cre)
    // 有効期限(3分)
    val cal = Calendar.getInstance()
    cal.add(Calendar.MINUTE, 3)
    val limit = cal.getTime()

    // ファイル名を指定
    val response = new ResponseHeaderOverrides
    val contentDispositionFilename = java.net.URLEncoder.encode(fileName.split(Array[Char]('\\', '/')).last, "UTF-8")
    response.setContentDisposition(s"attachment; filename*=UTF-8''${contentDispositionFilename}")

    val request = new GeneratePresignedUrlRequest(AppConf.s3UploadRoot, filePath)
      .withExpiration(limit)
      .withResponseHeaders(response)
    if (isHead) {
      request.setMethod(HttpMethod.HEAD)
    }
    // URLを生成
    val url = client.generatePresignedUrl(request)
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