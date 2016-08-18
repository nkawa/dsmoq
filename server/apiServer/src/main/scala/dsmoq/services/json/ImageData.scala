package dsmoq.services.json

object ImageData {
  case class GetFileParams(
    datasetId: String,
    id: String,
    size: Option[Int])
}
