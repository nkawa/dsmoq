package dsmoq.services

import dsmoq.AppConf
import dsmoq.persistence

/**
 * ユーザ情報
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
 */
case class User(
  id: String,
  name: String,
  fullname: String,
  organization: String,
  title: String,
  image: String,
  mailAddress: String,
  description: String,
  isGuest: Boolean,
  isDisabled: Boolean
)

/**
 * ユーザ情報のコンパニオンオブジェクト
 */
object User {
  /**
   * ユーザ情報を取得する。
   *
   * @param x ユーザオブジェクト
   * @param address E-mailアドレス
   * @return ユーザ情報
   */
  def apply(x: persistence.User, address: String): User = User(
    id = x.id,
    name = x.name,
    fullname = x.fullname,
    organization = x.organization,
    title = x.title,
    image = dsmoq.AppConf.imageDownloadRoot + "user/" + x.id + "/" + x.imageId,
    mailAddress = address,
    description = x.description,
    isGuest = false,
    isDisabled = x.disabled
  )
}
