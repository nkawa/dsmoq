package dsmoq.services.json

import dsmoq.services.User
import org.scalatra.servlet.FileItem

object DatasetData {
  // response
  case class DatasetsSummary(
    id: String,
    name: String,
    description: String,
    image: String,
    featuredImage: String,
    license: Option[String] = None,
    attributes: Seq[DatasetAttribute],
    ownerships: Seq[DatasetOwnership],
    files: Long,
    dataSize: Long,
    defaultAccessLevel: Int,
    permission: Int,
    localState: Int,
    s3State: Int
  )

  case class Dataset(
    id: String,
    filesSize: Long,
    filesCount: Int,
    files: Seq[DatasetFile],
    meta: DatasetMetaData,
    images: Seq[Image],
    primaryImage: String,
    featuredImage: String,
    ownerships: Seq[DatasetOwnership],
    defaultAccessLevel: Int,
    permission: Int,
    accessCount: Long,
    localState: Int,
    s3State: Int,
    fileLimit: Int
  )

  case class DatasetMetaData(
    name: String,
    description: String,
    license : String,
    attributes: Seq[DatasetAttribute]
  )

  case class DatasetAttribute(
    name: String,
    value: String
  )

  case class DatasetAddFiles(
    files: Seq[DatasetFile]
  )

  /**
   * データセットに追加した画像を返却する際のJSON型
   *
   * @param images データセットの画像のリスト
   * @param primaryImage メイン画像のID
   */
  case class DatasetAddImages(
    images: Seq[DatasetGetImage],
    primaryImage: String
  )

  case class DatasetGetImage (
    id: String,
    name: String,
    url: String,
    isPrimary: Boolean
  )

  case class DatasetDeleteImage(
    primaryImage: String,
    featuredImage: String
  )

  case class DatasetFile(
    id: String,
    name: String,
    description: String,
    url: String,
    size: Long,
    createdBy: User,
    createdAt: String,
    updatedBy: User,
    updatedAt: String,
    isZip: Boolean,
    zipedFiles: Seq[DatasetZipedFile],
    zipCount: Int
  )

  case class DatasetZipedFile (
    id: String,
    name: String,
    size: Long,
    url: String
  )

  case class DatasetOwnership (
    id: String,
    name: String,
    fullname: String,
    organization: String,
    title: String,
    description: String,
    image: String,
    accessLevel: Int,
    ownerType: Int
  )

  case class DatasetTask (
    taskId: String
  )

  case class CopiedDataset (
    datasetId: String
  )

  /**
   * データセットに設定したゲストアクセスレベルを返却するためのJSON型
   * 
   * @param defaultAccessLevel ゲストユーザが対象のデータセットに持っているロール(DefaultAccessLevelの定義値)
   */
  case class DatasetGuestAccessLevel(
    defaultAccessLevel: Int
  )

  /**
   * データセットに設定した画像IDを返却するためのJSON型
   * @param imageId 画像ID
   */
  case class ChangeDatasetImage(
    imageId: String
  )
}
