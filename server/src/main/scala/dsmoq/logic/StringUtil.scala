package dsmoq.logic

object StringUtil {
  /**
   * 空白文字(全角含む)のtrim
   */
  def trimAllSpaces(str: String) = {
    str.replaceAll("^[\\s　]*", "").replaceAll("[\\s　]*$", "");
  }
}
