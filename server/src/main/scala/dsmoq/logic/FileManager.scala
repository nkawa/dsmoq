package dsmoq.logic

import java.io.{InputStream, FileInputStream, IOException, File}
import java.nio.file.Paths
import java.util.Calendar
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{GetObjectRequest, GeneratePresignedUrlRequest, ObjectMetadata}
import com.amazonaws.services.s3.transfer.{TransferManager, TransferManagerConfiguration}
import dsmoq.AppConf
import org.scalatra.servlet.FileItem

object FileManager {
  private val PART_SIZE = 5 * 1024L * 1024L

  def uploadToLocal(datasetId: String, fileId: String, historyId: String, file: FileItem) = {
    val datasetDir = Paths.get(AppConf.fileDir, datasetId).toFile
    if (!datasetDir.exists()) datasetDir.mkdir()

    val fileDir = datasetDir.toPath.resolve(fileId).toFile
    if (!fileDir.exists()) fileDir.mkdir()

    file.write(fileDir.toPath.resolve(historyId).toFile)
  }

  def uploadToS3(datasetId: String, fileId: String, historyId: String, file: FileItem) = {
    uploadToS3(datasetId + "/" + fileId + "/" +  historyId + "/" + file.getName, file.getInputStream)
  }

  def downloadFromLocal(filePath: String): File = {
    val fullPath = Paths.get(AppConf.fileDir, filePath).toFile
    if (!fullPath.exists()) throw new RuntimeException("file not found")
    new File(fullPath.toString)
  }

  def downloadFromS3(filePath: String): String = {
    val cre = new BasicAWSCredentials(AppConf.s3DownloadAccessKey, AppConf.s3DownloadSecretKey)
    val client = new AmazonS3Client(cre)
    // 有効期限(1分)
    val cal = Calendar.getInstance()
    cal.add(Calendar.MINUTE, 1)
    val limit = cal.getTime()

    // URLを生成
    val url = client.generatePresignedUrl(new GeneratePresignedUrlRequest(AppConf.s3UploadRoot, filePath).withExpiration(limit))
    url.toString
  }

  def moveFromLocalToS3(filePath: String, withDelete: Boolean = false) {
    val fullPath = Paths.get(AppConf.fileDir, filePath).toFile
    val file = new File(fullPath.toString)

    loanStream(file)( x => uploadToS3(fullPath.toString, x) )

    if (withDelete) {
      file.delete()
    }
  }

  private def loanStream(file: File)(f: InputStream => Unit)
  {
    var stream :InputStream = null
    try
    {
      stream = new FileInputStream(file)
      f(stream)
    }
    finally
    {
      try {
        if (stream != null) {
          stream.close()
        }
      }
      catch
      {
        case _:IOException =>
      }
    }
  }
  
  def moveFromS3ToLocal(filePath: String, withDelete: Boolean = false) {
    val cre = new BasicAWSCredentials(AppConf.s3DownloadAccessKey, AppConf.s3DownloadSecretKey)
    val client = new AmazonS3Client(cre)

    val request = new GetObjectRequest(AppConf.s3UploadRoot, filePath)
    val manager = new TransferManager(client)

    val pattern = """([^/]+)/([^/]+)/([^/]+)/[^/]+""".r
    filePath match {
      case pattern(datasetId, fileId, historyId) => {
        val datasetDir = Paths.get(AppConf.fileDir, datasetId).toFile
        if (!datasetDir.exists()) datasetDir.mkdir()

        val fileDir = datasetDir.toPath.resolve(fileId).toFile
        if (!fileDir.exists()) fileDir.mkdir()

        val historyDir = fileDir.toPath.resolve(historyId).toFile
        if (!historyDir.exists()) historyDir.mkdir()
      }
      case _ => // do nothing
    }

    val download = manager.download(request, new File(filePath))

    try {
      download.waitForCompletion()
    } catch {
      case e: InterruptedException => throw new IOException(e)
    }

    if (withDelete) {
      client.deleteObject(AppConf.s3UploadRoot, filePath)
    }
  }

  private def uploadToS3(filePath: String, in: InputStream) {
    val cre = new BasicAWSCredentials(AppConf.s3UploadAccessKey, AppConf.s3UploadSecretKey)
    val client = new AmazonS3Client(cre)

    if (! client.doesBucketExist(AppConf.s3UploadRoot)) {
      client.createBucket(AppConf.s3UploadRoot)
    }

    // FIXME このcontentLengthの算出法は概算であり、正確ではない
    val contentLength = in.available
    val putMetaData = new ObjectMetadata()
    putMetaData.setContentLength(contentLength)

    val manager = new TransferManager(client)

    // 分割サイズを設定
    val c = new TransferManagerConfiguration()
    c.setMinimumUploadPartSize(PART_SIZE)
    manager.setConfiguration(c)

    val upload = manager.upload(AppConf.s3UploadRoot, filePath, in, putMetaData)

    try {
      upload.waitForCompletion()
    } catch {
      case e : InterruptedException => throw new IOException(e)
    }
  }

}
