package dsmoq.controllers.json

case class GetGroupMembersParams(
  limit: Option[Int] = None,
  offset: Option[Int] = None
)
