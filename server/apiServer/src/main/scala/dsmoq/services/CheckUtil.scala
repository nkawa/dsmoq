package dsmoq.services

import com.typesafe.scalalogging.LazyLogging
import org.slf4j.MarkerFactory

/**
 * Service用のチェックユーティリティオブジェクト
 */
object CheckUtil extends LazyLogging {

  /**
    * ログマーカー
    */
  private val LOG_MARKER = MarkerFactory.getMarker("SERVICE_CHECK_LOG")

  /**
   * nullチェックを行う。
   *
   * @param value チェック対象
   * @param name チェックに違反した場合にメッセージに表示する項目名
   * @throws NullPointerException チェック対象の値がnullの場合
   */
  def checkNull[T](value: T, name: String): Unit = {
    if (value == null) {
      val message = s"null check failed: name:${name}"
      logger.error(LOG_MARKER, message)
      throw new NullPointerException(message)
    }
  }

}
