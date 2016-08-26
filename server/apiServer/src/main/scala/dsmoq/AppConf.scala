package dsmoq

import com.typesafe.config.ConfigFactory

object AppConf {
  private val conf = ConfigFactory.load
  private val root = {
    if (System.getProperty(org.scalatra.EnvironmentKey) == "test") {
      conf.getConfig("test")
    } else {
      conf
    }
  }

  val port = root.getInt("apiserver.port")
  val imageDir = root.getString("apiserver.image_dir")
  val fileDir = root.getString("apiserver.file_dir")
  val tempDir = root.getString("apiserver.temp_dir")
  val messageDir = root.getString("apiserver.message_dir")
  val appDir = if (root.hasPath("apiserver.app_dir")) root.getString("apiserver.app_dir") else fileDir + "/../jws"

  val systemUserId = "dccc110c-c34f-40ed-be2c-7e34a9f1b8f0"
  val guestUserId = "6afb4198-859d-4053-8a15-5c791f3a8089"
  val guestGroupId = "f274a75c-e20e-4b4a-8db9-566fd41aa1bd"

  val defaultAvatarImageId = "8a981652-ea4d-48cf-94db-0ceca7d81aef"
  val defaultDatasetImageId = "8b570468-9814-4d30-8c04-392b263b6404"
  val defaultGroupImageId = "960a5601-2b60-2531-e6ad-54b91612ede5"
  val defaultLicenseId = "dc16a22c-00de-b00e-dbec-38cbad333bf7"
  val defaultFeaturedImageIds = Seq(
    "e7aa025c-6498-4f1e-bca4-7c8dd85c9a4e",
    "897b71d7-c704-4f9d-b97e-f594f0570b55",
    "20f1586a-22b3-40c1-a308-d038284148cc",
    "aa6ce408-091d-4d28-897e-e75de8959b7f",
    "cb76a32d-5b12-42f4-90fd-62b8cfb19525",
    "59ae7029-fafc-4f7e-8578-e7a00db2d147"
  )

  val urlRoot = root.getString("apiserver.url_root")
  val imageDownloadRoot = root.getString("apiserver.image_url_root")
  val fileDownloadRoot = root.getString("apiserver.file_url_root")
  val appDownloadRoot = if (root.hasPath("apiserver.app_url_root")) root.getString("apiserver.app_url_root") else fileDownloadRoot + "../apps/"

  val clientId = root.getString("google.client_id")
  val clientSecret = root.getString("google.client_secret")
  val callbackUrl = root.getString("google.callback_url")
  val scopes = root.getStringList("google.scopes")
  val applicationName = root.getString("google.application_name")
  val allowedMailaddrs = root.getStringList("google.allowed_mailaddrs")

  val s3AccessKey = root.getString("s3.access_key")
  val s3SecretKey = root.getString("s3.secret_key")
  val s3UploadRoot = root.getString("s3.upload_bucket")

  val fileLimit = {
    if (root.hasPath("apiserver.file_limit")) {
      root.getInt("apiserver.file_limit")
    } else {
      100
    }
  }
}
