package dsmoq.sdk.request.json

case class GetGroupsJson(
  query: Option[String] = None,
  user: Option[String] = None,
  limit: Option[Int] = 20,
  offset: Option[Int] = 0
) extends Jsonable
