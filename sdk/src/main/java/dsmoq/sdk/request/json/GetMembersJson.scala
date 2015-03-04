package dsmoq.sdk.request.json

case class GetMembersJson(limit: Option[Int] = Some(20), offset: Option[Int] = Some(0)) extends Jsonable
