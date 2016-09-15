package dsmoq.maintenance.views

/**
 * Viewで利用するユーティリティクラス
 */
object Util {

  /**
   * Long数値をデータサイズ表記文字列に変換する。
   *
   * @param size サイズ
   * @return データサイズ表記文字列
   */
  def toDatasize(size: Long): String = {
    def round(num: Double): Long = {
      math.round(num * 10.0) / 10
    }
    if (size < 1024L) {
      size + "Byte";
    } else if (size < 1048576L) {
      round(size / 1024L) + "KB";
    } else if (size < 1073741824L) {
      round(size / 1048576L) + "MB";
    } else if (size < 1099511627776L) {
      round(size / 1073741824L) + "GB";
    } else {
      round(size / 1099511627776L) + "TB";
    }
  }
}
