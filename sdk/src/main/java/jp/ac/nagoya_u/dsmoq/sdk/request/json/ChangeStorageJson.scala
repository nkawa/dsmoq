package jp.ac.nagoya_u.dsmoq.sdk.request.json

private[request] case class ChangeStorageJson(saveLocal: Boolean, saveS3: Boolean) extends Jsonable
