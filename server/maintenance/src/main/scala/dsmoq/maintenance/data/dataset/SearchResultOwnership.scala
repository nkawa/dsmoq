package dsmoq.maintenance.data.dataset

/**
 * アクセス権の検索結果を表すクラス
 */
case class SearchResultOwnership(
  id: String,
  ownerType: OwnerType,
  name: String,
  accessLevel: AccessLevel
)
