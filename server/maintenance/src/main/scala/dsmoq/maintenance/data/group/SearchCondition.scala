package dsmoq.maintenance.data.group

import scala.util.Try

import dsmoq.maintenance.AppConfig

/**
 * グループ検索条件を表すクラス
 */
case class SearchCondition(
  groupType: SearchCondition.GroupType,
  managerId: String,
  groupName: String,
  page: Int
) {
  import SearchCondition._

  def toMap: Map[String, String] = {
    Map(
      "groupType" -> groupType.toString,
      "managerId" -> managerId,
      "groupName" -> groupName,
      "page" -> page.toString
    )
  }

  def toParam: String = {
    toMap.collect { case (key, value) => s"${key}=${value}" }.mkString("&")
  }

  override def toString: String = toParam.replaceAll("&", ",")
}

/**
 * グループ検索条件を表すクラスのコンパニオンオブジェクト
 */
object SearchCondition {
  /**
   * 表示対象のグループ種別
   */
  sealed trait GroupType
  object GroupType {
    /**
     * 表示対象: 全件
     */
    case object All extends GroupType {
      override def toString(): String = "all"
    }
    /**
     * 表示対象: 閲覧可能
     */
    case object NotDeleted extends GroupType {
      override def toString(): String = "not_deleted"
    }
    /**
     * 表示対象: 論理削除済み
     */
    case object Deleted extends GroupType {
      override def toString(): String = "deleted"
    }
    /**
     * オプショナルな文字列からグループ種別を取得する。
     *
     * @param str オプショナル文字列
     * @return グループ種別
     */
    def apply(str: Option[String]): GroupType = {
      str match {
        case Some("not_deleted") => NotDeleted
        case Some("deleted") => Deleted
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
      groupType = GroupType(map.get("groupType")),
      managerId = map.getOrElse("managerId", ""),
      groupName = map.getOrElse("groupName", ""),
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
