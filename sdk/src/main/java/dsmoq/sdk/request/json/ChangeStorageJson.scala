package dsmoq.sdk.request.json

private[request] case class ChangeStorageJson(saveLocal: Boolean, saveS3: Boolean) extends Jsonable
