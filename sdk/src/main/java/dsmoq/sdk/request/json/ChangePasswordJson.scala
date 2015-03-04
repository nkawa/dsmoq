package dsmoq.sdk.request.json

private[request] case class ChangePasswordJson(currentPassword: String, newPassword: String) extends Jsonable
