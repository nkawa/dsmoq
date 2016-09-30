package dsmoq

import scala.collection.JavaConverters._

import com.typesafe.config.ConfigFactory

/**
 * application.conf、system.confの設定値を取りまとめるオブジェクト
 */
object AppConf {
  private val conf = ConfigFactory.load
  private val root = {
    if (System.getProperty(org.scalatra.EnvironmentKey) == "test") {
      conf.getConfig("test")
    } else {
      conf
    }
  }
  private val sys = ConfigFactory.load("system.conf")

  /**
   * 起動ポート番号
   */
  val port = root.getInt("apiserver.port")

  /**
   * 画像配置ディレクトリ
   */
  val imageDir = root.getString("apiserver.image_dir")

  /**
   * ファイル配置ディレクトリ
   */
  val fileDir = root.getString("apiserver.file_dir")

  /**
   * 一時ディレクトリ
   */
  val tempDir = root.getString("apiserver.temp_dir")

  /**
   * メッセージファイルディレクトリ
   */
  val messageDir = root.getString("apiserver.message_dir")

  /**
   * jarファイル配置ディレクトリ
   */
  val appDir = if (root.hasPath("apiserver.app_dir")) {
    root.getString("apiserver.app_dir")
  } else {
    fileDir + "/../jws"
  }

  /**
   * システムユーザID
   */
  val systemUserId = sys.getString("system.user.system.id")

  /**
   * ゲストグループID
   */
  val guestGroupId = sys.getString("system.group.guest")

  /**
   * ゲストユーザオブジェクト
   */
  val guestUser = dsmoq.services.User(
    id = sys.getString("system.user.guest.id"),
    name = sys.getString("system.user.guest.name"),
    fullname = sys.getString("system.user.guest.fullname"),
    organization = sys.getString("system.user.guest.organization"),
    title = sys.getString("system.user.guest.title"),
    image = sys.getString("system.user.guest.image"),
    mailAddress = sys.getString("system.user.guest.mailAddress"),
    description = sys.getString("system.user.guest.description"),
    isGuest = true,
    isDisabled = false
  )

  /**
   * デフォルトユーザアイコン画像ID
   */
  val defaultAvatarImageId = sys.getString("system.default.image.avatar")

  /**
   * デフォルトデータセットアイコン画像ID
   */
  val defaultDatasetImageId = sys.getString("system.default.image.dataset")

  /**
   * デフォルトグループアイコン画像ID
   */
  val defaultGroupImageId = sys.getString("system.default.image.group")

  /**
   * デフォルトFeatured画像ID
   */
  val defaultFeaturedImageIds = sys.getStringList("system.default.image.featured").asScala

  /**
   * デフォルトライセンスID
   */
  val defaultLicenseId = sys.getString("system.default.license")

  /**
   * ルートURL
   */
  val urlRoot = root.getString("apiserver.url_root")

  /**
   * 画像URLのルート
   */
  val imageDownloadRoot = root.getString("apiserver.image_url_root")

  /**
   * ファイルダウンロードURLのルート
   */
  val fileDownloadRoot = root.getString("apiserver.file_url_root")

  /**
   * APPURLのルート
   */
  val appDownloadRoot = if (root.hasPath("apiserver.app_url_root")) {
    root.getString("apiserver.app_url_root")
  } else {
    fileDownloadRoot + "../apps/"
  }

  /**
   * Google OAuthのクライアントID
   */
  val clientId = root.getString("google.client_id")

  /**
   * Google OAuthのクライアントSecret
   */
  val clientSecret = root.getString("google.client_secret")

  /**
   * Google OAuthのコールバックURL
   */
  val callbackUrl = root.getString("google.callback_url")

  /**
   * Google OAuthの適用範囲
   */
  val scopes = root.getStringList("google.scopes")

  /**
   * Google OAuthのアプリケーション名
   */
  val applicationName = root.getString("google.application_name")

  /**
   * Google OAuthで許可するメールアドレス形式
   */
  val allowedMailaddrs = root.getStringList("google.allowed_mailaddrs")

  /**
   * Amazon S3のアクセスキー
   */
  val s3AccessKey = root.getString("s3.access_key")

  /**
   * Amazon S3のシークレットキー
   */
  val s3SecretKey = root.getString("s3.secret_key")

  /**
   * Amazon S3のアップロード先bucket
   */
  val s3UploadRoot = root.getString("s3.upload_bucket")

  /**
   * ファイル取得時の取得上限デフォルト値
   */
  val fileLimit = {
    if (root.hasPath("apiserver.file_limit")) {
      root.getInt("apiserver.file_limit")
    } else {
      100
    }
  }
}
