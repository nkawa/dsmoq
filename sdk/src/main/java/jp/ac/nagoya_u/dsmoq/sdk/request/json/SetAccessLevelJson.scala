package jp.ac.nagoya_u.dsmoq.sdk.request.json

private[request] case class SetAccessLevelJson(id: String, ownerType: Int, accessLevel: Int) extends Jsonable
