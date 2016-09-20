package dsmoq.maintenance.data.group

/**
 * メンバーの検索結果を表すケースクラス
 */
case class SearchResultMember(
  id: String,
  name: String,
  role: MemberRole
)
