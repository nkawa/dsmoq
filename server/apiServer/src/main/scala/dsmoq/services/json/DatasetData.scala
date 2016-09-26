package dsmoq.services.json

import java.io.InputStream

import org.joda.time.DateTime

import dsmoq.services.User

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
    license: String,
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

  case class DatasetGetImage(
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
    url: Option[String],
    size: Option[Long],
    createdBy: Option[User],
    createdAt: String,
    updatedBy: Option[User],
    updatedAt: String,
    isZip: Boolean,
    zipedFiles: Seq[DatasetZipedFile],
    zipCount: Int
  )

  case class DatasetZipedFile(
    id: String,
    name: String,
    size: Option[Long],
    url: Option[String]
  )

  case class DatasetOwnership(
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

  case class DatasetTask(
    taskId: String
  )

  case class CopiedDataset(
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

  /**
   * アプリ情報を返却するためのJSON型
   *
   * @param id アプリID
   * @param name アプリ名
   * @param datasetId データセットID
   * @param isPrimary データセットにアプリとして設定されているか
   * @param lastModified 最終更新日時
   */
  case class App(
    id: String,
    name: String,
    datasetId: String,
    isPrimary: Boolean,
    lastModified: DateTime
  )

  /**
   * アプリのJNLPファイルを返却するためのJSON型
   *
   * @param id アプリID
   * @param name アプリ名
   * @param datasetId データセットID
   * @param lastModified 最終更新日時
   * @param content JNLPファイルの中身
   */
  case class AppJnlp(
    id: String,
    name: String,
    datasetId: String,
    lastModified: DateTime,
    content: String
  )

  /**
   * アプリのJARファイルを返却するためのJSON型
   *
   * @param appId アプリID
   * @param appVersionId アプリバージョンID
   * @param lastModified 最終更新日時
   * @param content JARファイルの中身
   */
  case class AppFile(
    appId: String,
    lastModified: DateTime,
    content: InputStream
  )
}
