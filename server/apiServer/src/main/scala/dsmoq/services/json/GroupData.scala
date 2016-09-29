package dsmoq.services.json

/**
 * Group系APIのレスポンスに使用するJSON型を取りまとめるオブジェクト
 */
object GroupData {

  /**
   * グループのサマリ情報を返却するためのJSON型
   *
   * @param id グループID
   * @param name グループ名
   * @param description 説明
   * @param image アイコン画像URL
   * @param members メンバー人数
   * @param datasets このグループが管理しているデータセットの件数
   */
  case class GroupsSummary(
    id: String,
    name: String,
    description: String,
    image: String,
    members: Int,
    datasets: Int
  )

  /**
   * グループの情報を返却するためのJSON型
   *
   * @param id グループID
   * @param name グループ名
   * @param description 説明
   * @param images 画像一覧
   * @param primaryImage アイコン画像ID
   * @param isMember API実行アカウントがこのグループのメンバーか否か
   * @param role API実行アカウントがこのグループに対して持つロール(@see dsmoq.persistence.GroupMemberRole)
   * @param providedDatasetCount このグループが管理しているデータセットの件数
   */
  case class Group(
    id: String,
    name: String,
    description: String,
    images: Seq[Image],
    primaryImage: String,
    isMember: Boolean,
    role: Int,
    providedDatasetCount: Int
  )

  /**
   * グループのメンバー情報を返却するためのJSON型
   *
   * @param id ユーザID
   * @param name ユーザアカウント名
   * @param fullname ユーザ名
   * @param organization 所属
   * @param title タイトル
   * @param description 説明
   * @param image アイコン画像URL
   * @param role このグループに対するロール(@see dsmoq.persistence.GroupMemberRole)
   */
  case class MemberSummary(
    id: String,
    name: String,
    fullname: String,
    organization: String,
    title: String,
    description: String,
    image: String,
    role: Int
  )

  /**
   * グループに追加したメンバーを返却するために使用するJSON型
   *
   * @param ownerships メンバーに追加したユーザのリスト
   */
  case class AddMembers(
    ownerships: Seq[MemberSummary]
  )

  /**
   * グループに追加した画像を返却するためのJSON型
   *
   * @param images グループの画像のリスト
   * @param primaryImage メイン画像のID
   */
  case class GroupAddImages(
    images: Seq[GroupGetImage],
    primaryImage: String
  )

  /**
   * グループから画像を削除したときの情報を返却するためのJSON型
   *
   * @param primaryImage 画像削除後のアイコン画像ID
   */
  case class GroupDeleteImage(
    primaryImage: String
  )

  /**
   * グループから取得した画像を返却する際のJSON型
   *
   * @param id 画像ID
   * @param name 画像名
   * @param url 画像URL
   * @param isPrimary アイコン画像か否か
   */
  case class GroupGetImage(
    id: String,
    name: String,
    url: String,
    isPrimary: Boolean
  )

  /**
   * グループに設定した画像IDを返却するためのJSON型
   * @param imageId 画像ID
   */
  case class ChangeGroupImage(
    imageId: String
  )
}
