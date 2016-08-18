package dsmoq

/**
 * リソースバンドルのキー項目名の列挙
 */
object ResourceNames {

  /**
   * ローカルかS3のいずれかの保存先にチェックを付けてください
   */
  val CHECK_S3_OR_LOCAL = "check_s3_or_local"

  /**
   * 空ファイルが指定されました
   */
  val SELECT_EMPTY_FILE = "select_empty_file"

  /**
   * featured属性は一つまでにしてください
   */
  val FEATURE_ATTRIBUTE_IS_ONLY_ONE = "feature_attribute_is_only_one"

  /**
   * JSONパラメータは必須です
   */
  val JSON_PARAMETER_REQUIRED = "json_parameter_required"

  /**
   * JSONの形式が不正です
   */
  val INVALID_JSON_FORMAT = "invalid_json_format"

  /**
   * パスワードが一致しません
   */
  val INVALID_PASSWORD = "invalid_password"

  /**
   * GoogleユーザーアカウントのEmailアドレスは変更できません
   */
  val CANT_CHANGE_GOOGLE_USER_EMAIL = "cant_change_googleuser_email"

  /**
   * Emailアドレス：%s はすでに登録されています
   */
  val ALREADY_REGISTERED_EMAIL = "already_registered_email"

  /**
   * Googleユーザーアカウントのパスワードは変更できません
   */
  val CANT_CHANGE_GOOGLE_USER_PASSWORD = "cant_change_googleuser_password"

  /**
   * Googleアカウントユーザのアカウント名は変更できません
   */
  val CANT_CHANGE_GOOGLE_USER_NAME = "cant_change_googleuser_name"

  /**
   * ユーザ名：%s はすでに存在しています
   */
  val ALREADY_REGISTERED_NAME = "already_registered_name"

  /**
   * %s は無効なライセンスIDです
   */
  val INVALID_LICENSEID = "invalid_licenseid"

  /**
   * デフォルトイメージは削除できません
   */
  val CANT_DELETE_DEFAULTIMAGE = "cant_delete_default_image"

  /**
   * 対象のファイルはZipファイルではないため、中身を取り出すことができません
   */
  val CANT_TAKE_OUT_BECAUSE_NOT_ZIP = "cant_take_out_because_not_zip"

  /**
   * 対象のファイルは存在しません
   */
  val FILE_NOT_FOUND = "file_not_found"

  /**
   * グループ名：%s はすでに登録されています
   */
  val ALREADY_REGISTERED_GROUP_NAME = "already_registered_group_name"

  /**
   * %s が指定されていません
   */
  val REQUIRE_TARGET = "require_target"

  /**
   * %s の件数が0件です
   */
  val EMPTY = "empty"

  /**
   * %s (値：%s)は有効な値ではありません
   */
  val NOT_CONTAINS_RANGE = "not_contains_range"

  /**
   * %s を入力してください
   */
  val REQUIRE_NON_EMPTY = "require_non_empty"

  /**
   * %s(値：%s) はUUIDの形式ではありません
   */
  val INVALID_UUID = "invalid_uuid"

  /**
   * 数値は0以上を入力してください
   */
  val REQUIRE_NON_MINUS = "require_non_minus"

  /**
   * 対象のユーザにはアクセス権がありません
   */
  val NO_ACCESS_PERMISSION = "no_access_permission"

  /**
   * 対象のユーザにはダウンロード権がありません
   */
  val NO_DOWNLOAD_PERMISSION = "no_download_permission"

  /**
   * この操作はデータセットのオーナーにのみ許可されています
   */
  val ONLY_ALLOW_DATASET_OWNER = "only_allow_dataset_owner"

  /**
   * メールアドレスが許可された形式でありません
   */
  val INVALID_EMAIL_FORMAT = "invalid_email_format"

  /**
   * データセットにはオーナーが最低一人は必要です
   */
  val NO_OWNER = "no_owner"

  /**
   * グループにはマネージャーが最低一人は必要です
   */
  val NO_MANAGER = "no_manager"

  /**
   * この操作はゲストユーザには許可されていません
   */
  val NOT_ALLOW_GUEST = "not_allow_guest"

  /**
   * Authorizationヘッダが不正です
   */
  val INVALID_AUTHORIZATION_HEADER = "invalid_authorization_header"

  /**
   * AuthorizationヘッダにAPIキー・シグネチャを設定してください
   */
  val REQUIRE_APIKEY_AND_SIGNATURE = "require_apikey_and_signature"

  /**
   * APIキー・シグネチャのいずれかが不正です
   */
  val INVALID_APIKEY_OR_SIGNATURE = "invalid_apikey_or_signature"

  /**
   * 対象のユーザには更新権限がありません
   */
  val NO_UPDATE_PERMISSION = "no_update_permission"
}
