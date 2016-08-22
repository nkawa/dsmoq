package dsmoq.taskServer

import java.io.{ File, FileInputStream, IOException, InputStream }
import java.nio.file.{ Files, Paths }
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{ GetObjectRequest, ObjectMetadata }
import com.amazonaws.services.s3.transfer.{ TransferManager, TransferManagerConfiguration }
import scala.collection.JavaConversions._

object FileManager {
  private val PART_SIZE = 5 * 1024L * 1024L

  def moveFromLocalToS3(filePath: String, client: AmazonS3Client) {
    if (client.listObjects(AppConf.s3UploadRoot).getObjectSummaries.map(_.getKey).contains(filePath)) {
      return
    }
    if (!client.doesBucketExist(AppConf.s3UploadRoot)) {
      throw new BucketNotFoundException("対象のBucket(%s)が作成されていません。".format(AppConf.s3UploadRoot))
    }
    val path = Paths.get(AppConf.fileDir, filePath)
    val fileSize = Files.size(path)
    val putMetaData = new ObjectMetadata()
    putMetaData.setContentLength(fileSize)
    val c = new TransferManagerConfiguration()
    c.setMinimumUploadPartSize(PART_SIZE)
    val manager = new TransferManager(client)
    manager.setConfiguration(c)
    val in = Files.newInputStream(path)
    try {
      val upload = manager.upload(AppConf.s3UploadRoot, filePath, in, putMetaData)
      upload.waitForCompletion()
    } catch {
      case e: InterruptedException => throw new IOException(e)
    } finally {
      in.close()
    }

    //    val fullPath = Paths.get(AppConf.fileDir, filePath).toFile
    //    val file = new File(fullPath.toString)
    //    loanStream(file)( x => uploadToS3(filePath, x, client) )
  }

  //  private def loanStream(file: File)(f: InputStream => Unit)
  //  {
  //    var stream :InputStream = null
  //    try
  //    {
  //      stream = new FileInputStream(file)
  //      f(stream)
  //    }
  //    finally
  //    {
  //      try {
  //        if (stream != null) {
  //          stream.close()
  //        }
  //      }
  //      catch
  //      {
  //        case _:IOException =>
  //      }
  //    }
  //  }

  def moveFromS3ToLocal(filePath: String)(implicit client: AmazonS3Client) {
    if (Paths.get(AppConf.fileDir, filePath).toFile.exists()) {
      return
    }

    val request = new GetObjectRequest(AppConf.s3UploadRoot, filePath)
    val manager = new TransferManager(client)

    val pattern = """([^/]+)/([^/]+)/[^/]+""".r
    filePath match {
      case pattern(datasetId, fileId) => {
        val datasetDir = Paths.get(AppConf.fileDir, datasetId).toFile
        if (!datasetDir.exists()) datasetDir.mkdir()

        val fileDir = datasetDir.toPath.resolve(fileId).toFile
        if (!fileDir.exists()) fileDir.mkdir()
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

  private def uploadToS3(filePath: String, in: InputStream, client: AmazonS3Client) {
    if (client.listObjects(AppConf.s3UploadRoot).getObjectSummaries.map(_.getKey).contains(filePath)) {
      return
    }

    if (!client.doesBucketExist(AppConf.s3UploadRoot)) {
      throw new BucketNotFoundException("対象のBucket(%s)が作成されていません。".format(AppConf.s3UploadRoot))
    }

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
      case e: InterruptedException => throw new IOException(e)
    }
  }
}
