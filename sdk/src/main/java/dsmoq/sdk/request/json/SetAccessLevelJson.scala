package dsmoq.sdk.request.json

private[request] case class SetAccessLevelJson(id: String, ownerType: Int, accessLevel: Int) extends Jsonable
