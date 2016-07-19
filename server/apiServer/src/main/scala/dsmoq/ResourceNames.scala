package dsmoq

/**
 * リソースバンドルのキー項目名の列挙
 */
object ResourceNames {
  
  /**
   * ローカルかS3のいずれかの保存先にチェックを付けてください
   */
  val checkS3OrLocal = "check_s3_or_local"

  /**
   * 空ファイルが指定されました
   */
  val selectEmptyFile = "select_empty_file"

  /**
   * featured属性は一つまでにしてください
   */
  val featureAttributeIsOnlyOne = "feature_attribute_is_only_one"

  /**
   * JSONパラメータは必須です
   */
  val jsonParameterRequired = "json_parameter_required"

  /**
   * JSONの形式が不正です
   */
  val invalidJsonFormat = "invalid_json_format"

  /**
   * パスワードが一致しません
   */
  val invalidPassword = "invalid_password"

  /**
   * GoogleユーザーアカウントのEmailアドレスは変更できません
   */
  val cantChangeGoogleUserEmail = "cant_change_googleuser_email"

  /**
   * Emailアドレス：%s はすでに登録されています
   */
  val alreadyRegisteredEmail = "already_registered_email"

  /**
   * Googleユーザーアカウントのパスワードは変更できません
   */
  val cantChangeGoogleUserPassword = "cant_change_googleuser_password"

  /**
   * Googleアカウントユーザのアカウント名は変更できません
   */
  val cantChangeGoogleUserName = "cant_change_googleuser_name"

  /**
   * ユーザ名：%s はすでに存在しています
   */
  val alreadyResigsteredName = "already_registered_name"

  /**
   * %s は無効なライセンスIDです
   */
  val invalidLicenseId = "invalid_licenseid"

  /**
   * デフォルトイメージは削除できません
   */
  val cantDeleteDefaultImage = "cant_delete_default_image"

  /**
   * 対象のファイルはZipファイルではないため、中身を取り出すことができません
   */
  val cantTakeOutBecauseNotZip = "cant_take_out_because_not_zip"

  /**
   * 対象のファイルは存在しません
   */
  val fileNotFound = "file_not_found" 

  /**
   * グループ名：%s はすでに登録されています
   */
  val alreadyRegisteredGroupName = "already_registered_group_name"

  /**
   * %s が指定されていません
   */
  val requireTarget = "require_target"

  /**
   * %s の件数が0件です
   */
  val empty = "empty"

  /**
   * %s (値：%s)は有効な値ではありません
   */
  val notContainsRange = "not_contains_range"

  /**
   * %s を入力してください
   */
  val requireNonEmpty = "require_non_empty"

  /**
   * %s(値：%s) はUUIDの形式ではありません
   */
  val invalidUuid = "invalid_uuid"
}
