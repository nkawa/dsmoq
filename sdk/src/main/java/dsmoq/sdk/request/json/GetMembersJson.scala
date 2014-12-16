package dsmoq.sdk.request.json

case class GetMembersJson(limit: Option[Int] = 20, offset: Option[Int] = 0) extends Jsonable
