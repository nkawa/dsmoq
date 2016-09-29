package dsmoq.controllers.json

/**
 * PUT /api/profileのリクエストに使用するJSON型のケースクラス
 *
 * @param name ユーザーアカウント名
 * @param fullname ユーザー名
 * @param organization 所属
 * @param title タイトル
 * @param description 説明
 */
case class UpdateProfileParams(
  name: Option[String] = None,
  fullname: Option[String] = None,
  organization: Option[String] = None,
  title: Option[String] = None,
  description: Option[String] = None
)

/**
 * PUT /api/profile/email_change_requestsのリクエストに使用するJSON型のケースクラス
 *
 * @param email 変更するE-Mailアドレス
 */
case class UpdateMailAddressParams(
  email: Option[String] = None
)

/**
 * PUT /api/profile/passwordのリクエストに使用するJSON型のケースクラス
 *
 * @param currentPassword 現在のパスワード
 * @param newPassword 新しいパスワード
 */
case class UpdatePasswordParams(
  currentPassword: Option[String] = None,
  newPassword: Option[String] = None
)
