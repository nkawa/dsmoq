package dsmoq.controllers.json

case class CreateGroupParams(
  name: Option[String] = None,
  description: Option[String] = None)

case class SearchGroupsParams(
  query: Option[String] = None,
  user: Option[String] = None,
  limit: Option[Int] = None,
  offset: Option[Int] = None)

case class GetGroupMembersParams(
  limit: Option[Int] = None,
  offset: Option[Int] = None)

case class UpdateGroupParams(
  name: Option[String] = None,
  description: Option[String] = None)

case class ChangeGroupPrimaryImageParams(
  imageId: Option[String] = None)

case class SetGroupMemberRoleParams(
  role: Option[Int] = None)
