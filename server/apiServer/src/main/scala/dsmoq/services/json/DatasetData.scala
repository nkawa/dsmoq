package dsmoq.services.json

import java.io.InputStream

import org.joda.time.DateTime

import dsmoq.services.User

/**
 * Dataset系APIのレスポンスに使用するJSON型を取りまとめるオブジェクト
 */
object DatasetData {
  /**
   * データセットのサマリ情報を返却するためのJSON型
   *
   * @param id データセットID
   * @param name データセット名
   * @param description 説明
   * @param image アイコン画像URL
   * @param featuredImage Featured画像URL
   * @param license ライセンス
   * @param attributes 属性一覧
   * @param ownerships アクセス権一覧
   * @param files ファイル数
   * @param dataSize データセット内ファイルの総データサイズ
   * @param defaultAccessLevel ゲストアクセスレベル
   *  (@see dsmoq.persistence.DefaultAccessLevel)
   * @param permission API実行アカウントのこのデータセットに対するアクセス権
   *  (@see dsmoq.persistence.UserAccessLevel)
   * @param localState ファイルのローカル保存状態(@see dsmoq.services.SaveStatus)
   * @param s3State ファイルのS3保存状態(@see dsmoq.services.SaveStatus)
   */
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

  /**
   * データセットの情報を返却するためのJSON型
   *
   * @param id データセットID
   * @param filesSize データセット内ファイルの総データサイズ
   * @param filesCount ファイル数
   * @param files データセットのファイル一覧
   * @param meta データセットのメタ情報
   * @param images データセットの画像一覧
   * @param primaryImage アイコン画像ID
   * @param featuredImage Featured画像ID
   * @param license ライセンス
   * @param ownerships アクセス権一覧
   * @param defaultAccessLevel ゲストアクセスレベル
   *  (@see dsmoq.persistence.DefaultAccessLevel)
   * @param permission API実行アカウントのこのデータセットに対するアクセス権
   *  (@see dsmoq.persistence.UserAccessLevel)
   * @param localState ファイルのローカル保存状態(@see dsmoq.services.SaveStatus)
   * @param s3State ファイルのS3保存状態(@see dsmoq.services.SaveStatus)
   * @param fileLimit ファイルの検索上限の設定値(@see dsmoq.AppConf.fileLimit)
   */
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

  /**
   * データセットのメタ情報を返却するためのJSON型
   *
   * @param name データセット名
   * @param description 説明
   * @param license ライセンスID
   * @param attributes 属性一覧
   */
  case class DatasetMetaData(
    name: String,
    description: String,
    license: String,
    attributes: Seq[DatasetAttribute]
  )

  /**
   * データセットの属性情報を返却するためのJSON型
   *
   * @param name 属性名
   * @param value 属性値
   */
  case class DatasetAttribute(
    name: String,
    value: String
  )

  /**
   * データセットに追加されたファイル情報を返却するためのJSON型
   *
   * @param files 追加されたファイル一覧
   */
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

  /**
   * データセットから取得した画像を返却する際のJSON型
   *
   * @param id 画像ID
   * @param name 画像名
   * @param url 画像URL
   * @param isPrimary アイコン画像か否か
   */
  case class DatasetGetImage(
    id: String,
    name: String,
    url: String,
    isPrimary: Boolean
  )

  /**
   * データセットから画像を削除したときの情報を返却するためのJSON型
   *
   * @param primaryImage 画像削除後のアイコン画像ID
   * @param featuredImage 画像削除後のFeatured画像ID
   */
  case class DatasetDeleteImage(
    primaryImage: String,
    featuredImage: String
  )

  /**
   * データセットのファイル情報を返却するためのJSON型
   *
   * @param id ファイルID
   * @param name ファイル名
   * @param description 説明
   * @param url ダウンロードURL
   * @param size データサイズ
   * @param createdBy 作成者
   * @param createdAt 作成日時
   * @param updatedBy 更新者
   * @param updatedAt 更新日時
   * @param isZip Zipファイルか否か
   * @param zipCount Zip内ファイルの件数
   */
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

  /**
   * データセットのZip内ファイル情報を返却するためのJSON型
   *
   * @param id ZipファイルID
   * @param name Zipファイル名
   * @param size 圧縮サイズ
   * @param url ダウンロードURL
   */
  case class DatasetZipedFile(
    id: String,
    name: String,
    size: Option[Long],
    url: Option[String]
  )

  /**
   * データセットのアクセス権情報を返却するためのJSON型
   *
   * @param id アクセス権を持つユーザー/グループのID
   * @param name アクセス権を持つユーザのアカウント名/グループの名前
   * @param fullname アクセス権を持つユーザの名前
   * @param organization アクセス権を持つユーザの所属
   * @param title アクセス権を持つユーザのタイトル
   * @param description アクセス権を持つユーザ/グループの説明
   * @param image アイコン画像URL
   * @param accessLevel アクセスレベル(@see dsmoq.persistence.UserAccessLevel)
   * @param ownerType オーナー種別(@see dsmoq.persistence.OwnerType)
   */
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

  /**
   * データセットのタスク情報を返却するためのJSON型
   *
   * @param taskId タスクID
   */
  case class DatasetTask(
    taskId: String
  )

  /**
   * コピーしたデータセット情報を返却するためのJSON型
   *
   * @param datasetId コピーによって作成したデータセットID
   */
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
    size: Long,
    content: InputStream
  )
}
