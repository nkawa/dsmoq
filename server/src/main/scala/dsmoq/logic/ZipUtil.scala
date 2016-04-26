package dsmoq.logic

import java.nio.file._
import java.io._

import com.typesafe.scalalogging.LazyLogging
import org.slf4j.MarkerFactory

object ZipUtil extends LazyLogging {

  val LOG_MARKER = MarkerFactory.getMarker("ZIP_LOG")

  case class ZipLocalHeader(
    extractVersion: Short,
    option: Short,
    method: Short,
    time: Short,
    date: Short,
    crc32: Int,
    compressSize: Long,
    uncompressSize: Long,
    fileNameLength: Int,
    extraLength: Int,
    fileName: String,
    extra: Array[Byte]
  )
  case class ZipInfo(
    fileName: String,
    localHeaderOffset: Long,
    dataSizeWithLocalHeader: Long,
    uncompressSize: Long,
    centralHeader: Array[Byte]
  )
  def toZipInfo(offset: Long, localHeader: ZipLocalHeader, centralHeader: Array[Byte]): ZipInfo = {
    val localHeaderSize = 30 + localHeader.fileNameLength + localHeader.extraLength
    ZipInfo(
      fileName = localHeader.fileName,
      localHeaderOffset = offset,
      dataSizeWithLocalHeader = localHeader.compressSize + localHeaderSize,
      uncompressSize = localHeader.uncompressSize,
      centralHeader = centralHeader
    )
  }
  def read(ra: RandomAccessFile, n: Int): Long = {
    var ret = 0L
    var i = 0
    var w = 1L
    while(i < n) {
      ret = ret + ra.readUnsignedByte() * w
      i = i + 1
      w = w * 256
    }
    ret
  }
  def read(a: Array[Byte], from: Int, n: Int): Long = {
    var ret = 0L
    var i = 0
    var w = 1L
    while(i < n) {
      ret = ret + java.lang.Byte.toUnsignedInt(a(from + i)) * w
      i = i + 1
      w = w * 256
    }
    ret
  }
  def splitExtra(extra: Array[Byte]): List[(Short, Array[Byte])] = {
    logger.debug(LOG_MARKER, "  called splitExtra function, extra = 0x{}", bytes2hex(extra))

    val ret = new scala.collection.mutable.ListBuffer[(Short, Array[Byte])]
    var i = 0
    while (i < extra.length) {
      val id = read(extra, i, 2).toShort
      val size = read(extra, i + 2, 2).toInt
      val data = extra.view(i + 4, i + 4 + size).toArray
      ret += ((id, data))
      i = i + 4 + size
    }
    ret.toList
  }
  def p2(x: Int): Long = {
    var i = 0
    var ret = 1L
    while (i < x) {
      ret = ret * 256
      i = i + 1
    }
    ret
  }
  def fromExtra(xs: List[(Long, Int)], extra: Array[Byte]): List[Long] = {
    logger.info(LOG_MARKER, "  called fromExtra function, xs = {}, extra = 0x{}", xs, bytes2hex(extra))

    var i = 0
    xs.map { case (x, size) =>
      if (x == p2(size / 2) - 1) {
        val ret = read(extra, i, size)
        logger.debug(LOG_MARKER, "  - extra value = {}", ret.toString)
        i = i + size
        ret
      } else x
    }
  }
  def readLocalHeader(ra: RandomAccessFile): ZipLocalHeader = {
    logger.debug(LOG_MARKER, "  called readLocalHeader function, ra = {}", ra)

    val extractVersion = read(ra, 2).toShort
    val option = read(ra, 2).toShort
    val method = read(ra, 2).toShort
    val time = read(ra, 2).toShort
    val date = read(ra, 2).toShort
    val crc32 = read(ra, 4).toInt
    val compressSize = read(ra, 4)
    val uncompressSize = read(ra, 4)
    val fileNameLength = read(ra, 2).toInt
    val extraLength = read(ra, 2).toInt
    val fileNameByte = new Array[Byte](fileNameLength)
    ra.read(fileNameByte)
    val fileName = StringUtil.convertByte2String(fileNameByte)
    val extra = new Array[Byte](extraLength)
    ra.read(extra)
    val List(uncompressSize64, compressSize64) = fromExtra(
      List((uncompressSize, 8), (compressSize, 8)),
      splitExtra(extra).find(_._1 == 0x0001).map(_._2).getOrElse(Array.empty)
    )

    logger.debug(LOG_MARKER, "  - converted fileName, fileName = {}, fileNameByte = {}", fileName, bytes2hex(fileNameByte))
    logger.debug(LOG_MARKER, "  - reading extra..., extra = 0x{}", bytes2hex(extra))
    logger.info(LOG_MARKER, "  - compress size = {}. uncompress size = {}. [in header: compress size = {}, uncompress size = {}]", compressSize64.toString, uncompressSize64.toString, compressSize.toString, uncompressSize.toString)

    ZipLocalHeader(
      extractVersion = extractVersion,
      option = option,
      method = method,
      time = time,
      date = date,
      crc32 = crc32,
      compressSize = compressSize64,
      uncompressSize = uncompressSize64,
      fileNameLength = fileNameLength,
      extraLength = extraLength,
      fileName = fileName,
      extra = extra
    )
  }
  def readCentralHeader(ra: RandomAccessFile): (Long, Array[Byte]) = {
    logger.debug(LOG_MARKER, "  called readCentralHeader function, ra = {}", ra)

    val head = Array.fill[Byte](42)(0)
    ra.read(head)
    val compressSize = read(head, 16, 4)
    val uncompressSize = read(head, 20, 4)
    val fileNameLength = read(head, 24, 2).toInt
    val extraLength = read(head, 26, 2).toInt
    val commentLength = read(head, 28, 2).toInt
    val diskStart = read(head, 30, 2).toInt
    val offset = read(head, 38, 4)
    val fileNameByte = new Array[Byte](fileNameLength)
    ra.read(fileNameByte)
    val extra = new Array[Byte](extraLength)
    ra.read(extra)
    val comment = new Array[Byte](commentLength)
    ra.read(comment)
    val zip64ex = splitExtra(extra).find(_._1 == 0x0001)
    val List(uncompressSize64, compressSize64, offset64, _) = fromExtra(
      List((uncompressSize, 8), (compressSize, 8), (offset, 8), (diskStart, 4)),
      zip64ex.map(_._2).getOrElse(Array.empty)
    )
    val bs = Array.concat(
      Array[Byte](0x50, 0x4b, 0x01, 0x02),
      head,
      fileNameByte,
      extra,
      comment
    )
    logger.debug(LOG_MARKER, "  - central header. header = 0x{}", bytes2hex(bs))
    logger.debug(LOG_MARKER, "  - central header. extra = 0x{}", bytes2hex(extra))
    logger.info(LOG_MARKER, "  - compress size = {}. uncompress size = {}. [in header: compress size = {}, uncompress size = {}]", compressSize64.toString, uncompressSize64.toString, compressSize.toString, uncompressSize.toString)

    (offset64, bs)
  }
  def readRaw(path: Path): Either[Long, List[(Long, ZipLocalHeader, Array[Byte])]] = {
    logger.debug(LOG_MARKER, "called readRaw function, path = [{}]", path)
    val file = path.toFile
    if (!file.exists) {
      return Left(0)
    }
    val localHeaders = scala.collection.mutable.Map.empty[Long, ZipLocalHeader]
    val centralHeaders = scala.collection.mutable.Map.empty[Long, Array[Byte]]
    val ra = new RandomAccessFile(file, "r")
    var isLoop = true
    try {
      while (ra.getFilePointer < ra.length && isLoop) {
        val offset = ra.getFilePointer
        val header = Array.fill[Byte](4)(0)
        ra.read(header)

        logger.info(LOG_MARKER, "Read bytes..., header = 0x{}", bytes2hex(header))

        header match {
          case Array(0x50, 0x4b, 0x03, 0x04) => {
            // local file header signature     4 bytes  (0x04034b50)
            logger.debug(LOG_MARKER, "Found Signature: Local file header. (0x04034b50)")

            val header = readLocalHeader(ra)
            ra.seek(ra.getFilePointer + header.compressSize)
            localHeaders += (offset -> header)
          }
          case Array(0x50, 0x4b, 0x01, 0x02) => {
            // central file header signature   4 bytes  (0x02014b50)
            logger.debug(LOG_MARKER, "Found Signature: Central file header. (0x02014b50)")

            val (offset, bs) = readCentralHeader(ra)
            centralHeaders += (offset -> bs)
          }
          case Array(0x50, 0x4b, 0x05, 0x06) => {
            // end of central dir signature    4 bytes  (0x06054b50)
            logger.debug(LOG_MARKER, "Found Signature: End of central dir. (0x06054b50)")

            isLoop = false
          }
          case Array(0x50, 0x4b, 0x06, 0x06) => {
            // zip64 end of central dir signature   4 bytes  (0x06064b50)
            logger.debug(LOG_MARKER, "Found Signature: Zip64 end of central dir. (0x06064b50)")

            isLoop = false
          }
          case Array(0x50, 0x4b, 0x06, 0x07) => {
            // zip64 end of central directory locator signature   4 bytes  (0x07064b50)
            logger.debug(LOG_MARKER, "Found Signature: Zip64 end of central directory locator. (0x07064b50)")

            isLoop = false
          }
          case Array(0x50, 0x4b, 0x05, 0x05) => {
            // digital signature signature   4 bytes  (0x05054b50)
            logger.debug(LOG_MARKER, "Found Signature: Digital signature. (0x05054b50)")

            isLoop = false
          }
          case Array(0x50, 0x4b, 0x06, 0x08) => {
            // archive extra data record signature   4 bytes  (0x08064b50)
            logger.debug(LOG_MARKER, "Found Signature: Archive extra data record. (0x08064b50)")
          }
          case Array(0x50, 0x4b, 0x07, 0x08) => {
            // data descriptor signature   4 bytes  (0x08074b50)
            logger.debug(LOG_MARKER, "Found Signature: Data descriptor. (0x08074b50)")
          }
          case _ => {
            logger.info(LOG_MARKER, "signature not found. header = 0x{}, pointer = {}", bytes2hex(header), ra.getFilePointer.toString)
// del 1 line  aaa
            //return Left(offset)
          }
        }

        logger.debug(LOG_MARKER, "Check: ra.getFilePointer={}, ra.length={}", ra.getFilePointer.toString, ra.length.toString )
        logger.info(LOG_MARKER, "Check: continue?={}", isLoop.toString)

      }
    } catch {
      case e:Throwable => {
        logger.info(LOG_MARKER, "error occurred.", e)
      }
    } finally {
      ra.close()
    }
    val ret = for {
      (key, localHeader) <- localHeaders.toList
    } yield {
      (key, localHeader, centralHeaders.getOrElse(key, Array.empty))
    }

    logger.info(LOG_MARKER, "Return readRaw function, return data len={}, local header len={}, central header len={}", ret.size.toString, localHeaders.size.toString, centralHeaders.size.toString)

    Right(ret.toList)
  }
  def read(path: Path): Either[Long, List[ZipInfo]] = {
    logger.info(LOG_MARKER, "called read function, path = [{}]", path)
    for {
      raw <- readRaw(path).right
    } yield {
      raw.map { case (o, h, c) => toZipInfo(o, h, c) }
    }
  }

  private def bytes2hex(bytes: Array[Byte], sep: Option[String] = None): String = {
    sep match {
      case None =>  bytes.map("%02x".format(_)).mkString
      case _ =>  bytes.map("%02x".format(_)).mkString(sep.get)
    }
  }
}
