package dsmoq.taskServer

import java.io.{File, FileInputStream, IOException, InputStream}
import java.nio.file.Paths
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{GetObjectRequest, ObjectMetadata}
import com.amazonaws.services.s3.transfer.{TransferManager, TransferManagerConfiguration}
import scala.collection.JavaConversions._

object FileManager {
  private val PART_SIZE = 5 * 1024L * 1024L

  def moveFromLocalToS3(filePath: String)(implicit client: AmazonS3Client) {
    val fullPath = Paths.get(AppConf.fileDir, filePath).toFile
    val file = new File(fullPath.toString)

    loanStream(file)( x => uploadToS3(fullPath.toString, x) )
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

  def moveFromS3ToLocal(filePath: String)(implicit client: AmazonS3Client) {
    if (Paths.get(AppConf.fileDir, filePath).toFile.exists()) {
      return
    }

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

    val download = manager.download(request, Paths.get(AppConf.fileDir, filePath).toFile)

    try {
      download.waitForCompletion()
    } catch {
      case e: InterruptedException => throw new IOException(e)
    }
  }

  private def uploadToS3(filePath: String, in: InputStream)(implicit client: AmazonS3Client) {
    if (client.listObjects(AppConf.s3UploadRoot).getObjectSummaries.map(_.getKey).contains(filePath)) {
      return
    }

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
