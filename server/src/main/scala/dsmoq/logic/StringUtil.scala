package dsmoq.logic

import java.util.UUID

object StringUtil {
  /**
   * 空白文字(全角含む)のtrim
   */
  def trimAllSpaces(str: String) = {
    str.replaceAll("^[\\s　]*", "").replaceAll("[\\s　]*$", "");
  }

  def isUUID(str: String) = {
    try {
      val uuid = UUID.fromString(str.trim)
      uuid.toString.toLowerCase == str.trim.toLowerCase
    } catch {
      case _ :IllegalArgumentException => false
    }
  }

  /**
    * UTF-8の文字列に変換可能か判定する。
    * (文字コードを判定するのではなく、文字化けしないかを判定)
    *
    * @param bytes 判定対象
    * @return true: UTF-8に変換可能、false: 変換不可。
    */
  def isUTF8Byte(bytes: Array[Byte]): Boolean = {
    try {
      bytes sameElements new String(bytes, "UTF-8").getBytes("UTF-8")
    } catch {
      case e: Exception => false
    }
  }

  /**
    * Byte配列を文字列に変換する。
    * (UTF-8 に変換できるものはUTF-8にそれ以外はShift_JIS)
    *
    * @param bytes 変換対象
    * @return 文字列
    */
  def convertByte2String(bytes: Array[Byte]): String = {
    if (isUTF8Byte(bytes)) {
      new String(bytes, "UTF-8")
    } else {
      new String(bytes, "Shift_JIS")
    }
  }
}
