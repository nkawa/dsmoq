package jp.ac.nagoya_u.dsmoq.sdk.request.json

private[request] case class UpdateDatasetMetaJson(
  name: String,
  description: String,
  license: String,
  attributes: Seq[UpdateDatasetAttributeJson]
) extends Jsonable

private[request] case class UpdateDatasetAttributeJson(name: String, value: String)
