package dsmoq.maintenance.data.file

import scala.util.Try

import dsmoq.maintenance.AppConfig

/**
 * ファイル検索条件を表すクラス
 */
case class SearchCondition(
  fileType: SearchCondition.FileType,
  datasetId: Option[String],
  page: Int
) {
  import SearchCondition._

  def toMap: Map[String, String] = {
    val results = Map(
      "fileType" -> fileType.toString,
      "page" -> page.toString
    )
    datasetId.map(id => results + ("datasetId" -> id)).getOrElse(results)
  }
}

/**
 * ファイル検索条件を表すクラスのコンパニオンオブジェクト
 */
object SearchCondition {
  /**
   * 表示対象のファイル種別
   */
  sealed trait FileType
  object FileType {
    /**
     * 表示対象: 全件
     */
    case object All extends FileType {
      override def toString(): String = "all"
    }
    /**
     * 表示対象: 閲覧可能
     */
    case object NotDeleted extends FileType {
      override def toString(): String = "not_deleted"
    }
    /**
     * 表示対象: 論理削除済み
     */
    case object Deleted extends FileType {
      override def toString(): String = "deleted"
    }
    /**
     * オプショナルな文字列からファイル種別を取得する。
     *
     * @param str オプショナル文字列
     * @return ファイル種別
     */
    def apply(str: Option[String]): FileType = {
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
      fileType = FileType(map.get("fileType")),
      datasetId = map.get("datasetId"),
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
