package dsmoq.maintenance.controllers

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.scalatra.ActionResult
import org.scalatra.BadRequest
import org.scalatra.InternalServerError
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.maintenance.services.ErrorDetail
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
   * @param errorProc エラーメッセージ、エラー詳細のリストを受け取ってレスポンスボディを返す関数
   * @return レスポンス
   */
  def resultAs(result: Try[ActionResult])(errorProc: (String, Seq[ErrorDetail]) => String): ActionResult = {
    result match {
      case Success(res) => res
      case Failure(e) => {
        e match {
          case se: ServiceException => {
            val content = errorProc(se.getMessage, se.details)
            BadRequest(content)
          }
          case _ => {
            logger.error(LOG_MARKER, e.getMessage, e)
            val errorDetails = Seq(
              ErrorDetail(e.getMessage, e.getStackTrace.map(_.toString))
            )
            val content = errorProc("内部エラーが発生しました。", errorDetails)
            InternalServerError(content)
          }
        }
      }
    }
  }
}
