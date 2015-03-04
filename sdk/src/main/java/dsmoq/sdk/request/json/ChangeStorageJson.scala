package dsmoq.sdk.request.json

case class ChangeStorageJson(saveLocal: Boolean, saveS3: Boolean) extends Jsonable
