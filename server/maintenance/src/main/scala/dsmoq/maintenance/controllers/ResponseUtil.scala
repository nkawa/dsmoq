package dsmoq.maintenance.controllers

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.scalatra.ActionResult
import org.scalatra.BadRequest
import org.scalatra.InternalServerError

import dsmoq.maintenance.services.ServiceException

object ResponseUtil {
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
            val content = errorProc("内部エラーが発生しました。")
            InternalServerError(content)
          }
        }
      }
    }
  }
}
