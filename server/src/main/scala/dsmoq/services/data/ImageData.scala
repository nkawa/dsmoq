package dsmoq.services.data

object ImageData {
  case class GetFileParams(
    datasetId: String,
    id: String,
    size: Option[Int]
  )
}
