package dsmoq.controllers

import scala.util.{Failure, Success}

import dsmoq.services.DatasetService
import org.scalatra.ScalatraServlet
import org.scalatra.servlet.FileUploadSupport

class FileController extends ScalatraServlet with SessionTrait with FileUploadSupport {

  get("/:datasetId/:id") {
    val datasetId = params("datasetId")
    val id = params("id")

    val result = for {
      fileInfo <- DatasetService.getDownloadFile(datasetId, id, currentUser)
    } yield {
      fileInfo
    }
    result match {
      case Success(x) =>
        if (x._1) {
          response.setHeader("Content-Disposition", "attachment; filename=" + x._4)
          response.setHeader("Content-Type", "application/octet-stream;charset=binary")
          x._5 match {
            case None => x._2
            case Some(in) => org.scalatra.util.io.copy(in, response.getOutputStream)
          }
        } else {
          redirect(x._3)
        }
      case Failure(e) => halt(status = 403, reason = "Forbidden", body="Forbidden")
    }
  }
}
