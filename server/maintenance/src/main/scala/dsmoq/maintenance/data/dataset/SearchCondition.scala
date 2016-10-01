package dsmoq.maintenance.data.dataset

import scala.util.Try

import dsmoq.maintenance.AppConfig

/**
 * データセット検索条件を表すクラス
 */
case class SearchCondition(
  datasetType: SearchCondition.DatasetType,
  ownerId: String,
  datasetName: String,
  page: Int
) {
  import SearchCondition._

  def toMap: Map[String, String] = {
    Map(
      "datasetType" -> datasetType.toString,
      "ownerId" -> ownerId,
      "datasetName" -> datasetName,
      "page" -> page.toString
    )
  }

  def toParam: String = {
    toMap.collect { case (key, value) => s"${key}=${value}" }.mkString("&")
  }

  override def toString: String = toParam.replaceAll("&", ",")
}

/**
 * データセット検索条件を表すクラスのコンパニオンオブジェクト
 */
object SearchCondition {
  /**
   * 表示対象のデータセット種別
   */
  sealed trait DatasetType
  object DatasetType {
    /**
     * 表示対象: 全件
     */
    case object All extends DatasetType {
      override def toString(): String = "all"
    }
    /**
     * 表示対象: 閲覧可能
     */
    case object NotDeleted extends DatasetType {
      override def toString(): String = "not_deleted"
    }
    /**
     * 表示対象: 論理削除済み
     */
    case object Deleted extends DatasetType {
      override def toString(): String = "deleted"
    }
    /**
     * オプショナルな文字列からデータセット種別を取得する。
     *
     * @param str オプショナル文字列
     * @return データセット種別
     */
    def apply(str: Option[String]): DatasetType = {
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
      datasetType = DatasetType(map.get("datasetType")),
      ownerId = map.getOrElse("ownerId", ""),
      datasetName = map.getOrElse("datasetName", ""),
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
