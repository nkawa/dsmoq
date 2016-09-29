package dsmoq.controllers.json

/**
 * POST /api/signinのリクエストに使用するJSON型のケースクラス
 *
 * @param id ユーザーアカウント名
 * @param password パスワード
 */
case class SigninParams(
  id: Option[String] = None,
  password: Option[String] = None
)
