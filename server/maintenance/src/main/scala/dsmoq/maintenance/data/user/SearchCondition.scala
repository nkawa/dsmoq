package dsmoq.maintenance.data.user

import scala.util.Try

import dsmoq.maintenance.AppConfig

/**
 * ユーザ検索条件を表すクラス
 */
case class SearchCondition(
  userType: SearchCondition.UserType,
  query: String,
  page: Int
) {
  import SearchCondition._

  def toMap: Map[String, String] = {
    Map(
      "userType" -> userType.toString,
      "query" -> query,
      "page" -> page.toString
    )
  }
}

/**
 * ユーザ検索条件を表すクラスのコンパニオンオブジェクト
 */
object SearchCondition {
  /**
   * 表示対象のユーザ種別
   */
  sealed trait UserType
  object UserType {
    /**
     * 表示対象: 全件
     */
    case object All extends UserType {
      override def toString(): String = "all"
    }
    /**
     * 表示対象: 有効ユーザ
     */
    case object Enabled extends UserType {
      override def toString(): String = "enabled"
    }
    /**
     * 表示対象: 無効ユーザ
     */
    case object Disabled extends UserType {
      override def toString(): String = "disabled"
    }
    /**
     * オプショナルな文字列からユーザ種別を取得する。
     *
     * @param str オプショナル文字列
     * @return ユーザ種別
     */
    def apply(str: Option[String]): UserType = {
      str match {
        case Some("enabled") => Enabled
        case Some("disabled") => Disabled
        case _ => All
      }
    }
  }

  /**
   * マップ値から検索条件を作成する。
   *
   * @param map マップ値
   * @return 検索条件
   */
  def fromMap(map: Map[String, String]): SearchCondition = {
    SearchCondition(
      userType = UserType(map.get("userType")),
      query = map.getOrElse("query", ""),
      page = toPage(map.get("page"), 1)
    )
  }

  /**
   * オプショナル文字列をページを表す数値に変換する。
   *
   * @param str オプショナル文字列
   * @param default 変換できなかった場合に用いる値
   * @return 変換された整数
   */
  def toPage(str: Option[String], default: Int): Int = {
    str.flatMap(s => Try(s.toInt).toOption).filter(_ >= 1).getOrElse(default)
  }
}
