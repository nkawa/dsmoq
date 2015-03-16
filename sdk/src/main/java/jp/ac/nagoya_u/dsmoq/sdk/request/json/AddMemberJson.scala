package jp.ac.nagoya_u.dsmoq.sdk.request.json

private[request] case class AddMemberJson(userId: String, role: Int) extends Jsonable
