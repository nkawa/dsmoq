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
      case Success(x) => redirect(x._1)
      case Failure(e) => halt(status = 403, reason = "Forbidden", body="Forbidden")
    }
  }
}
