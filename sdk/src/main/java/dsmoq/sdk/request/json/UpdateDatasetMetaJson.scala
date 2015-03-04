package dsmoq.sdk.request.json

private[request] case class UpdateDatasetMetaJson(
  name: String,
  description: String,
  license: String,
  attributes: Seq[Attribute]
) extends Jsonable
