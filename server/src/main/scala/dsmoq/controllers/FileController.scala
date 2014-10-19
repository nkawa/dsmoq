package dsmoq.controllers

import dsmoq.services.DatasetService

import scala.util.{Failure, Success}
import org.scalatra.{ScalatraServlet, NotFound}
import org.scalatra.servlet.FileUploadSupport

class FileController extends ScalatraServlet with SessionTrait with FileUploadSupport {

  get("/:datasetId/:id") {
    val datasetId = params("datasetId")
    val id = params("id")

    val result = for {
      user <- signedInUser
      fileInfo <- DatasetService.getDownloadFile(datasetId, id, user)
    } yield {
      fileInfo
    }
    result match {
      case Success(x) =>
        response.setHeader("Content-Disposition", "attachment; filename=" + x._2)
        response.setHeader("Content-Type", "application/octet-stream;charset=binary")
        x._1
      case Failure(e) => halt(status = 403, reason = "Forbidden", body="Forbidden")
    }
  }
}
