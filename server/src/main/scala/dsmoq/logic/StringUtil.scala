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
}
