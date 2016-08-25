package dsmoq.controllers

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.scalatra.ActionResult
import org.scalatra.BadRequest
import org.scalatra.Forbidden
import org.scalatra.InternalServerError
import org.scalatra.NotFound
import org.scalatra.Ok
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.exceptions.AccessDeniedException
import dsmoq.exceptions.BadRequestException
import dsmoq.exceptions.InputCheckException
import dsmoq.exceptions.InputValidationException
import dsmoq.exceptions.NotAuthorizedException
import dsmoq.exceptions.NotFoundException

/**
 * 入力検査異常を表す型
 *
 * @param key 入力検査異常が発生したキー
 * @param value 入力検査異常の詳細メッセージ
 */
case class CheckError(key: String, value: String)

/**
 * 処理結果のAjax表現を表す型
 *
 * @param A 処理結果の型
 * @param status 処理結果のステータスメッセージ
 * @param data 処理結果
 */
case class AjaxResponse[A](status: String, data: A = {})

/**
 * 処理結果のAjax表現を表す型のコンパリオンオブジェクト
 */
object AjaxResponse extends LazyLogging {
  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("AJAX_RESPONSE_LOG")

  /**
   * 処理結果をActionResultに変換します。
   *
   * @param result 処理結果
   * @return 処理結果のActionResult表現
   */
  def toActionResult(result: Try[_]): ActionResult = {
    result match {
      case Success(()) => Ok(AjaxResponse("OK"))
      case Success(x) => Ok(AjaxResponse("OK", x))
      case Failure(e) => {
        logger.info(LOG_MARKER, e.getMessage)
        ResponseUtil.toActionResult(e)
      }
    }
  }
}

/**
 * レスポンス型変換ユーティリティ
 */
object ResponseUtil extends LazyLogging {
  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("RESPONSE_UTIL_LOG")

  /**
   * 処理結果をActionResultに変換します。
   *
   * @param result 処理結果
   * @return 処理結果のActionResult表現
   */
  def toActionResult(result: Try[_]): ActionResult = {
    result match {
      case Success(x) => Ok(x)
      case Failure(e) => {
        logger.info(LOG_MARKER, e.getMessage)
        toActionResult(e)
      }
    }
  }

  /**
   * 処理結果の例外をActionResultに変換します。
   *
   * @param e 処理結果の例外
   * @return 例外のActionResult表現
   */
  def toActionResult(e: Throwable): ActionResult = {
    e match {
      case e: NotAuthorizedException => Forbidden(AjaxResponse("Unauthorized", e.getMessage)) // 403
      case e: AccessDeniedException => Forbidden(AjaxResponse("AccessDenied", e.getMessage)) // 403
      case e: NotFoundException => NotFound(AjaxResponse("NotFound")) // 404
      case e: InputCheckException => {
        if (e.isUrlParam) {
          NotFound(AjaxResponse("Illegal Argument", CheckError(e.target, e.message))) // 404
        } else {
          BadRequest(AjaxResponse("Illegal Argument", CheckError(e.target, e.message))) // 400
        }
      }
      case e: InputValidationException => BadRequest(AjaxResponse("BadRequest", e.getErrorMessage())) // 400
      case e: BadRequestException => BadRequest(AjaxResponse("BadRequest", e.getMessage)) // 400
      case e => {
        logger.error(LOG_MARKER, e.getMessage, e)
        InternalServerError(AjaxResponse("NG")) // 500
      }
    }
  }
}
