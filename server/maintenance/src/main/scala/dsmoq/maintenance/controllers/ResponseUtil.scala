package dsmoq.maintenance.controllers

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.scalatra.ActionResult
import org.scalatra.BadRequest
import org.scalatra.InternalServerError
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.maintenance.services.ServiceException

/**
 * レスポンス作成ユーティリティ
 */
object ResponseUtil extends LazyLogging {
  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("MAINTENANCE_RESPONSE_LOG")

  /**
   * エラーが発生し得る処理結果のレスポンスに対して、エラー側のレスポンスを作成する。
   *
   * @param result 処理結果
   * @param errorProc エラーメッセージを受け取ってレスポンスボディを返す関数
   * @return レスポンス
   */
  def resultAs(result: Try[ActionResult])(errorProc: String => String): ActionResult = {
    result match {
      case Success(res) => res
      case Failure(e) => {
        e match {
          case _: ServiceException => {
            val content = errorProc(e.getMessage)
            BadRequest(content)
          }
          case _ => {
            logger.error(LOG_MARKER, e.getMessage, e)
            val content = errorProc("内部エラーが発生しました。")
            InternalServerError(content)
          }
        }
      }
    }
  }
}
