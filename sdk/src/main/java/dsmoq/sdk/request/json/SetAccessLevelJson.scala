package dsmoq.sdk.request.json

case class SetAccessLevelJson(id: String, ownerType: Int, accessLevel: Int) extends Jsonable
