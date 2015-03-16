package jp.ac.nagoya_u.dsmoq.sdk.request.json

private[request] case class ChangePasswordJson(currentPassword: String, newPassword: String) extends Jsonable
