package dsmoq.services

/**
 * ユーザ情報を返却するためのJSON型
 *
 * @param id ユーザID
 * @param name ユーザアカウント名
 * @param fullname ユーザ名
 * @param organization 所属
 * @param tile タイトル
 * @param image アイコン画像URL
 * @param mailAddress E-mailアドレス
 * @param description 説明
 * @param isGuest ゲストユーザか否か
 * @param isDisabled 無効化されているか否か
 * @param isGoogleUser Googleユーザアカウントか否か
 */
case class ProfileData(
  id: String,
  name: String,
  fullname: String,
  organization: String,
  title: String,
  image: String,
  mailAddress: String,
  description: String,
  isGuest: Boolean,
  isDisabled: Boolean,
  isGoogleUser: Boolean
)

/**
 * ユーザ情報を返却するためのJSON型のコンパニオンオブジェクト
 */
object ProfileData {
  /**
   * 返却用のユーザ情報を取得する。
   *
   * @param x ユーザ情報
   * @param isGoogleUser Googleユーザアカウントか否か
   * @return 返却用のユーザ情報
   */
  def apply(x: dsmoq.services.User, isGoogleUser: Boolean): ProfileData = {
    ProfileData(
      id = x.id,
      name = x.name,
      fullname = x.fullname,
      organization = x.organization,
      title = x.title,
      image = x.image,
      mailAddress = x.mailAddress,
      description = x.description,
      isGuest = x.isGuest,
      isDisabled = x.isDisabled,
      isGoogleUser = isGoogleUser
    )
  }
}
