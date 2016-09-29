package dsmoq.services.json

import dsmoq.persistence.SuggestType

/**
 * Suggest系APIのレスポンスに使用するJSON型を取りまとめるオブジェクト
 */
object SuggestData {

  /**
   * データタイプを持つトレイト
   */
  sealed trait WithType {
    def dataType: Int
  }

  /**
   * Suggestのユーザ情報を返却するためのJSON型
   *
   * @param id ユーザID
   * @param name ユーザアカウント名
   * @param fullname ユーザ名
   * @param organization 所属
   * @param title タイトル
   * @param description 説明
   * @param image アイコン画像URL
   */
  case class User(
    id: String,
    name: String,
    fullname: String,
    organization: String,
    title: String,
    description: String,
    image: String
  )

  /**
   * Suggestのユーザ情報を返却するためのJSON型(データタイプ付き)
   *
   * @param id ユーザID
   * @param name ユーザアカウント名
   * @param fullname ユーザ名
   * @param organization 所属
   * @param image アイコン画像URL
   * @param dataType データタイプ
   */
  case class UserWithType(
    id: String,
    name: String,
    fullname: String,
    organization: String,
    image: String,
    // TODO title: String,
    // TODO description: String,
    dataType: Int = SuggestType.User
  ) extends WithType

  /**
   * Suggestのグループ情報を返却するためのJSON型
   *
   * @param id グループID
   * @param name グループ名
   * @param image アイコン画像URL
   */
  case class Group(
    id: String,
    name: String,
    image: String
  )

  /**
   * Suggestのグループ情報を返却するためのJSON型(データタイプ付き)
   *
   * @param id グループID
   * @param name グループ名
   * @param image アイコン画像URL
   * @param dataType データタイプ
   */
  case class GroupWithType(
    id: String,
    name: String,
    image: String,
    dataType: Int = SuggestType.Group
  ) extends WithType
}
