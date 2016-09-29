package dsmoq.controllers.json

/**
 * POST /api/groupsのリクエストに使用するJSON型のケースクラス
 *
 * @param name グループ名
 * @param description 説明
 */
case class CreateGroupParams(
  name: Option[String] = None,
  description: Option[String] = None
)

/**
 * GET /api/groupsのリクエストに使用するJSON型のケースクラス
 *
 * @param query 検索文字列
 * @param user 検索に使用するユーザーID
 * @param limit 検索件数上限
 * @param offset 検索位置
 */
case class SearchGroupsParams(
  query: Option[String] = None,
  user: Option[String] = None,
  limit: Option[Int] = None,
  offset: Option[Int] = None
)

/**
 * GET /groups/:groupId/membersのリクエストに使用するJSON型のケースクラス
 *
 * @param limit 検索件数上限
 * @param offset 検索位置
 */
case class GetGroupMembersParams(
  limit: Option[Int] = None,
  offset: Option[Int] = None
)

/**
 * PUT /api/groups/:groupIdのリクエストに使用するJSON型のケースクラス
 *
 * @param name グループ名
 * @param description 説明
 */
case class UpdateGroupParams(
  name: Option[String] = None,
  description: Option[String] = None
)

/**
 * PUT /api/groups/:groupId/images/primaryのリクエストに使用するJSON型のケースクラス
 *
 * @param imageId グループに設定する画像ID
 */
case class ChangeGroupPrimaryImageParams(
  imageId: Option[String] = None
)

/**
 * PUT /api/groups/:groupId/members/:userIdのリクエストに使用するJSON型のケースクラス
 *
 * @param role ロール(@see dsmoq.persistence.GroupMemberRole)
 */
case class SetGroupMemberRoleParams(
  role: Option[Int] = None
)
