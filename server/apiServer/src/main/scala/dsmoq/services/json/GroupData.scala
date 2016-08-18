package dsmoq.services.json

object GroupData {
  // response
  case class GroupsSummary(
    id: String,
    name: String,
    description: String,
    image: String,
    members: Int,
    datasets: Int)

  case class Group(
    id: String,
    name: String,
    description: String,
    images: Seq[Image],
    primaryImage: String,
    isMember: Boolean,
    role: Int,
    providedDatasetCount: Int)

  case class MemberSummary(
    id: String,
    name: String,
    fullname: String,
    organization: String,
    title: String,
    description: String,
    image: String,
    role: Int)

  /**
   * グループに追加したメンバーを返却するために使用するJSON型
   *
   * @param ownerships メンバーに追加したユーザのリスト
   */
  case class AddMembers(
    ownerships: Seq[MemberSummary])

  case class AddMember(
    id: String,
    name: String,
    organization: String,
    role: Int)

  /**
   * グループに追加した画像を返却するためのJSON型
   *
   * @param images グループの画像のリスト
   * @param primaryImage メイン画像のID
   */
  case class GroupAddImages(
    images: Seq[GroupGetImage],
    primaryImage: String)

  case class GroupDeleteImage(
    primaryImage: String)
  case class GroupGetImage(
    id: String,
    name: String,
    url: String,
    isPrimary: Boolean)
  /**
   * グループに設定した画像IDを返却するためのJSON型
   * @param imageId 画像ID
   */
  case class ChangeGroupImage(
    imageId: String)
}
