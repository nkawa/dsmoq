package dsmoq.maintenance.data.localuser

import org.scalatra.servlet.FileItem

/**
 * ローカルユーザー追加のパラメータを表すクラス
 */
case class CreateParameter(
  userName: Option[String],
  fullName: Option[String],
  password: Option[String],
  organization: Option[String],
  title: Option[String],
  mailAddress: Option[String],
  description: Option[String]
) {
}

object CreateParameter {

  /**
   * マップ値からパラメータを作成する。
   *
   * @param map マップ値
   * @return パラメータ
   */
  def fromMap(map: Map[String, String]): CreateParameter = {
    CreateParameter(
      userName = map.get("userName"),
      fullName = map.get("fullName"),
      password = map.get("password"),
      organization = map.get("organization"),
      title = map.get("title"),
      mailAddress = map.get("mailAddress"),
      description = map.get("description")
    )
  }

}