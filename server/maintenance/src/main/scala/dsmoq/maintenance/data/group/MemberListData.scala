package dsmoq.maintenance.data.group

/**
 * グループメンバー一覧画面を描画するための情報
 */
case class MemberListData(
  groupId: String,
  groupName: String,
  members: Seq[SearchResultMember]
)
