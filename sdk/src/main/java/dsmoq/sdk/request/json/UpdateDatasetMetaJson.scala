package dsmoq.sdk.request.json

case class UpdateDatasetMetaJson(
  name: String,
  description: String,
  license: String,
  attributes: Seq[Attribute]
) extends Jsonable
