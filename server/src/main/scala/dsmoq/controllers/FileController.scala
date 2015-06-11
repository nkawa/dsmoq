package dsmoq.controllers

import scala.util.{Failure, Success}

import org.scalatra.ScalatraServlet
import org.scalatra.servlet.FileUploadSupport

import dsmoq.exceptions.{AccessDeniedException, NotFoundException}
import dsmoq.services.DatasetService
import dsmoq.services.DatasetService.{DownloadFileNormal, DownloadFileRedirect}
import dsmoq.services.{AccountService, User}

class FileController extends ScalatraServlet with SessionTrait with UserTrait {

  get("/:datasetId/:id") {
    val datasetId = params("datasetId")
    val id = params("id")

    val result = for {
      fileInfo <- DatasetService.getDownloadFile(datasetId, id, userFromHeader.getOrElse(currentUser))
    } yield {
      fileInfo
    }
    result match {
      case Success(DownloadFileNormal(data, name)) => {
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(name.split(Array[Char]('\\', '/')).last,"UTF-8"))
        response.setHeader("Content-Type", "application/octet-stream;charset=binary")
        data
      }
      case Success(DownloadFileRedirect(url)) => {
        redirect(url)
      }
      case Failure(e) => e match {
        case _:NotFoundException => halt(status = 404, reason = "Not Found", body="Not Found")
        case _:AccessDeniedException => halt(status = 403, reason = "Forbidden", body="Forbidden")
        case e:Exception => halt(status = 500, reason = e.getMessage, body = e.getMessage)
      }
    }
  }

  private def userFromHeader :Option[User] = userFromHeader(request.getHeader("Authorization"))
}
