package dsmoq.logic

object StringUtil {
  /**
   * 空白文字(全角含む)のtrim
   */
  def trimAllSpaces(str: String) = {
    str.replaceAll("^[\\s　]*", "").replaceAll("[\\s　]*$", "");
  }

  def isUUID(str: String) = {
    val pattern = "\\A[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-f]{4}-[0-9a-fA-f]{4}-[0-9a-fA-f]{12}\\z".r
    str.trim match {
      case pattern() => true
      case _ => false
    }
  }
}
