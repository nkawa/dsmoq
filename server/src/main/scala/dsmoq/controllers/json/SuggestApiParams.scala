package dsmoq.controllers.json

case class UserSuggestApiParams(
  query: Option[String] = None,
  limit: Option[Int] = None,
  offset: Option[Int] = None
)

case class UserAndGroupSuggestApiParams(
  query: Option[String] = None,
  limit: Option[Int] = None,
  offset: Option[Int] = None,
  excludeIds: Seq[String] = Seq.empty
)