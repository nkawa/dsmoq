package dsmoq.controllers.json

/**
 * サジェスト系検索APIのリクエストに使用するJSON型のケースクラス
 *
 * 具体的には、以下のAPIで使用する。
 * GET /api/suggests/users
 * GET /api/suggests/groups
 * GET /api/suggests/attributes
 *
 * @param query 検索文字列
 * @param limit 検索件数上限
 * @param offset 検索位置
 */
case class SuggestApiParams(
  query: Option[String] = None,
  limit: Option[Int] = None,
  offset: Option[Int] = None
)

/**
 * GET /api/suggests/users_and_groupsのリクエストに使用するJSON型のケースクラス
 *
 * @param query 検索文字列
 * @param limit 検索件数上限
 * @param offset 検索位置
 * @param excludeIds 検索から除外するユーザー・グループID
 */
case class UserAndGroupSuggestApiParams(
  query: Option[String] = None,
  limit: Option[Int] = None,
  offset: Option[Int] = None,
  excludeIds: Seq[String] = Seq.empty
)
