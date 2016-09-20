package dsmoq.maintenance.views

import dsmoq.maintenance.AppConfig
import dsmoq.maintenance.data.dataset
import dsmoq.maintenance.data.group

/**
 * Viewで利用するユーティリティクラス
 */
object Util {

  /**
   * Long数値をデータサイズ表記文字列に変換する。
   *
   * @param size サイズ
   * @return データサイズ表記文字列
   */
  def toDatasize(size: Long): String = {
    def round(num: Double): Long = {
      math.round(num * 10.0) / 10
    }
    if (size < 1024L) {
      size + "Byte";
    } else if (size < 1048576L) {
      round(size / 1024L) + "KB";
    } else if (size < 1073741824L) {
      round(size / 1048576L) + "MB";
    } else if (size < 1099511627776L) {
      round(size / 1073741824L) + "GB";
    } else {
      round(size / 1099511627776L) + "TB";
    }
  }

  /**
   * dsmoqのデータセット詳細画面のURLを取得する。
   *
   * @param datasetId データセットID
   * @return データセット詳細画面のURL
   */
  def getDsmoqDatasetDetailUrl(datasetId: String): String = {
    val rootUrl = AppConfig.dsmoqUrlRoot
    val endsWithSlash = rootUrl.endsWith("/")
    s"${rootUrl}${if (endsWithSlash) "" else "/"}datasets/${datasetId}"
  }

  /**
   * ファイル一覧画面のURLを取得する。
   *
   * @param datasetId データセットID
   * @return ファイル一覧画面のURL
   */
  def getFileManagementUrl(datasetId: String): String = {
    s"/file?datasetId=${datasetId}"
  }

  /**
   * データセットアクセス権一覧画面のURLを取得する。
   *
   * @param datasetId データセットID
   * @param condition データセット一覧画面の検索条件
   * @return データセットアクセス権一覧画面のURL
   */
  def getDatasetAclUrl(datasetId: String, condition: dataset.SearchCondition): String = {
    s"/dataset/acl?datasetId=${datasetId}&${condition.toParam}"
  }

  /**
   * データセットアクセス権更新画面のURLを取得する。
   *
   * @param datasetId データセットID
   * @param ownership アクセス権情報
   * @param condition データセット一覧画面の検索条件
   * @return データセットアクセス権更新画面のURL
   */
  def getDatasetAclUpdateUrl(
    datasetId: String,
    ownership: dataset.SearchResultOwnership,
    condition: dataset.SearchCondition
  ): String = {
    ownership.ownerType match {
      case dataset.OwnerType.Group =>
        s"/dataset/acl/update/group?datasetId=${datasetId}&groupId=${ownership.id}&${condition.toParam}"
      case _ => s"/dataset/acl/update/user?datasetId=${datasetId}&userId=${ownership.id}&${condition.toParam}"
    }
  }

  /**
   * データセットアクセス権追加(ユーザー)画面のURLを取得する。
   *
   * @param datasetId データセットID
   * @param condition データセット一覧画面の検索条件
   * @return データセットアクセス権追加(ユーザー)画面のURL
   */
  def getDatasetAclAddUserUrl(datasetId: String, condition: dataset.SearchCondition): String = {
    s"/dataset/acl/add/user?datasetId=${datasetId}&${condition.toParam}"
  }

  /**
   * データセットアクセス権追加(グループ)画面のURLを取得する。
   *
   * @param datasetId データセットID
   * @param condition データセット一覧画面の検索条件
   * @return データセットアクセス権追加(グループ)画面のURL
   */
  def getDatasetAclAddGroupUrl(datasetId: String, condition: dataset.SearchCondition): String = {
    s"/dataset/acl/add/group?datasetId=${datasetId}&${condition.toParam}"
  }

  /**
   * データセット一覧画面のURLを取得する。
   *
   * @param condition データセット一覧画面の検索条件
   * @return データセット一覧画面のURL
   */
  def getDatasetManagementUrl(condition: dataset.SearchCondition): String = {
    s"/dataset?${condition.toParam}"
  }

  /**
   * dsmoqのグループ詳細のURLを取得する。
   *
   * @param groupId グループID
   * @return グループ詳細のURL
   */
  def getDsmoqGroupDetailUrl(groupId: String): String = {
    val rootUrl = AppConfig.dsmoqUrlRoot
    val endsWithSlash = rootUrl.endsWith("/")
    s"${rootUrl}${if (endsWithSlash) "" else "/"}groups/${groupId}"
  }

  /**
   * グループメンバー一覧画面のURLを取得する。
   *
   * @param groupId グループID
   * @param condition グループ一覧画面の検索条件
   * @return グループメンバー一覧画面のURL
   */
  def getGroupMemberUrl(groupId: String, condition: group.SearchCondition): String = {
    s"/group/member?groupId=${groupId}&${condition.toParam}"
  }

  /**
   * グループメンバー更新画面のURLを取得する。
   *
   * @param groupId グループID
   * @param userId ユーザーID
   * @param condition グループ一覧画面の検索条件
   * @return グループメンバー更新画面のURL
   */
  def getGroupMemberUpdateUrl(groupId: String, userId: String, condition: group.SearchCondition): String = {
    s"/group/member/update?groupId=${groupId}&userId=${userId}&${condition.toParam}"
  }

  /**
   * グループメンバー追加画面のURLを取得する。
   *
   * @param groupId グループID
   * @param condition グループ一覧画面の検索条件
   * @return グループメンバー追加画面のURL
   */
  def getGroupMemberAddUrl(groupId: String, condition: group.SearchCondition): String = {
    s"/group/member/add?groupId=${groupId}&${condition.toParam}"
  }

  /**
   * グループ一覧画面のURLを取得する。
   *
   * @param condition グループ一覧画面の検索条件
   * @return グループ一覧画面のURL
   */
  def getGroupManagementUrl(condition: group.SearchCondition): String = {
    s"/group?${condition.toParam}"
  }

  /**
   * 文字を規定の長さで取りつめ、省略した文字列を取得する。
   *
   * @param str 文字列
   * @param length 取りつめる文字数(実際には、この文字数-1で取りつめ、…が付与される)
   * @return 省略した文字列
   */
  def trimByLength(str: String, length: Int): String = {
    if (length <= 0 || str.isEmpty) {
      ""
    } else if (str.length <= length) {
      str
    } else {
      str.take(length - 1) + "…"
    }
  }
}
