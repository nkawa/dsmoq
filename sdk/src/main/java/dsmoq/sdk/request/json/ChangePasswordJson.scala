package dsmoq.sdk.request.json

case class ChangePasswordJson(currentPassword: String, newPassword: String) extends Jsonable
