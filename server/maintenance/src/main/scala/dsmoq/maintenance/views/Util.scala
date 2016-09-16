package dsmoq.maintenance.views

import dsmoq.maintenance.AppConfig
import dsmoq.maintenance.data.dataset

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

  def getDsmoqDatasetDetailUrl(datasetId: String): String = {
    s"${AppConfig.dsmoqUrlRoot}/datasets/${datasetId}"
  }

  def getFileManagementUrl(datasetId: String): String = {
    s"../file?datasetId=${datasetId}"
  }

  def getDatasetAclUrl(datasetId: String, condition: dataset.SearchCondition): String = {
    s"/dataset/acl?datasetId=${datasetId}&${condition.toParam}"
  }

  def getDatasetAclUpdateUrl(
    datasetId: String,
    ownership: dataset.SearchResultOwnership,
    condition: dataset.SearchCondition
  ): String = {
    ownership.ownerType match {
      case dataset.OwnerType.Group => s"/dataset/acl/update/group?datasetId=${datasetId}&groupId=${ownership.id}&${condition.toParam}"
      case _ => s"/dataset/acl/update/user?datasetId=${datasetId}&userId=${ownership.id}&${condition.toParam}"
    }
  }

  def getDatasetAclAddUserUrl(datasetId: String, condition: dataset.SearchCondition): String = {
    s"/dataset/acl/add/user?datasetId=${datasetId}&${condition.toParam}"
  }

  def getDatasetAclAddGroupUrl(datasetId: String, condition: dataset.SearchCondition): String = {
    s"/dataset/acl/add/group?datasetId=${datasetId}&${condition.toParam}"
  }

  def getDatasetManagementUrl(condition: dataset.SearchCondition): String = {
    s"/dataset?${condition.toParam}"
  }

  def getDsmoqGroupDetailUrl(groupId: String): String = {
    s"${AppConfig.dsmoqUrlRoot}/groups/${groupId}"
  }

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
