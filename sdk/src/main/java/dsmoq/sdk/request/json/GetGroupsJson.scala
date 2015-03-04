package dsmoq.sdk.request.json

case class GetGroupsJson(
  query: Option[String] = None,
  user: Option[String] = None,
  limit: Option[Int] = Some(20),
  offset: Option[Int] = Some(0)
) extends Jsonable
