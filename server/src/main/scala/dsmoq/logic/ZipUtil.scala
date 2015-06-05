package dsmoq.logic

import java.nio.file._
import java.io._

object ZipUtil {
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
  def readLocalHeader(ra: RandomAccessFile): ZipLocalHeader = {
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
    val extra = new Array[Byte](extraLength)
    ra.read(extra)
    ZipLocalHeader(
      extractVersion = extractVersion,
      option = option,
      method = method,
      time = time,
      date = date,
      crc32 = crc32,
      compressSize = compressSize,
      uncompressSize = uncompressSize,
      fileNameLength = fileNameLength,
      extraLength = extraLength,
      fileName = new String(fileNameByte,  "Shift-JIS"),
      extra = extra
    )
  }
  def readCentralHeader(ra: RandomAccessFile): (Long, Array[Byte]) = {
    val head = Array.fill[Byte](42)(0)
    ra.read(head)
    val fileNameLength = read(head, 24, 2).toInt
    val extraLength = read(head, 26, 2).toInt
    val commentLength = read(head, 28, 2).toInt
    val offset = read(head, 38, 4)
    val fileNameByte = new Array[Byte](fileNameLength)
    ra.read(fileNameByte)
    val extra = new Array[Byte](extraLength)
    ra.read(extra)
    val comment = new Array[Byte](commentLength)
    ra.read(comment)
    val bs = Array.concat(
      Array[Byte](0x50, 0x4b, 0x01, 0x02),
      head,
      fileNameByte,
      extra,
      comment
    )
    (offset, bs)
  }
  def readRaw(path: Path): Either[Long, List[(Long, ZipLocalHeader, Array[Byte])]] = {
    val file = path.toFile
    if (!file.exists) {
      return Left(0)
    }
    val localHeaders = scala.collection.mutable.Map.empty[Long, ZipLocalHeader]
    val centralHeaders = scala.collection.mutable.Map.empty[Long, Array[Byte]]
    val ra = new RandomAccessFile(file, "r")
    var cont = true
    try {
      while (ra.getFilePointer < ra.length && cont) {
        val offset = ra.getFilePointer
        val header = Array.fill[Byte](4)(0)
        ra.read(header)
        header match {
          case Array(0x50, 0x4b, 0x03, 0x04) => {
            val header = readLocalHeader(ra)
            ra.seek(ra.getFilePointer + header.compressSize)
            localHeaders += (offset -> header)
          }
          case Array(0x50, 0x4b, 0x01, 0x02) => {
            val (offset, bs) = readCentralHeader(ra)
            // DL時には単独のZIPファイルとして扱うため、
            // Central Header内のLocal Headerへの参照を先頭に書き換える必要がある
            bs(42) = 0
            bs(43) = 0
            bs(44) = 0
            bs(45) = 0
            centralHeaders += (offset -> bs)
          }
          case Array(0x50, 0x4b, 0x05, 0x06) => {
            cont = false
          }
          case _ => {
            return Left(offset)
          }
        }
      }
    } finally {
      ra.close()
    }
    val ret = for {
      (key, localHeader) <- localHeaders.toList
    } yield {
      (key, localHeader, centralHeaders.getOrElse(key, Array.empty))
    }
    Right(ret.toList)
  }
  def read(path: Path): Either[Long, List[ZipInfo]] = {
    for {
      raw <- readRaw(path).right
    } yield {
      raw.map { case (o, h, c) => toZipInfo(o, h, c) }
    }
  }
}
