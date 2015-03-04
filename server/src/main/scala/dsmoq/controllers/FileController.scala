package dsmoq.controllers

import scala.util.{Failure, Success}
import dsmoq.exceptions.NotFoundException
import dsmoq.services.DatasetService
import org.scalatra.ScalatraServlet
import org.scalatra.servlet.FileUploadSupport

class FileController extends ScalatraServlet with SessionTrait with FileUploadSupport with UserTrait {

  get("/:datasetId/:id") {
    val datasetId = params("datasetId")
    val id = params("id")

    val result = for {
      fileInfo <- DatasetService.getDownloadFile(datasetId, id, userFromHeader.getOrElse(currentUser))
    } yield {
      fileInfo
    }
    result match {
      case Success(x) =>
        if (x._1) {
          response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(x._4,"UTF-8"))
          response.setHeader("Content-Type", "application/octet-stream;charset=binary")
          x._5 match {
            case None => x._2
            case Some(in) => org.scalatra.util.io.copy(in, response.getOutputStream)
          }
        } else {
          redirect(x._3)
        }
      case Failure(e) => e match {
        case _:NotFoundException => halt(status = 404, reason = "Not Found", body="Not Found")
        case _:RuntimeException => halt(status = 403, reason = "Forbidden", body="Forbidden")
      }
    }
  }

  private def userFromHeader :Option[User] = userFromHeader(request.getHeader("Authorization"))
}
