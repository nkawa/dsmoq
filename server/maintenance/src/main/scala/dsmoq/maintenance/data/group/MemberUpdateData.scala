package dsmoq.maintenance.data.group

/**
 * グループメンバー更新画面を描画するための情報
 */
case class MemberUpdateData(
  groupId: String,
  groupName: String,
  member: SearchResultMember
)
