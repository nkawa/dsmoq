package dsmoq

import com.typesafe.config.ConfigFactory

object AppConf {
  private val conf = ConfigFactory.load
  private val dsmoq = conf.getConfig("dsmoq").getConfig(System.getProperty(org.scalatra.EnvironmentKey))

  val imageDir = dsmoq.getString("image_dir")
  val fileDir = dsmoq.getString("file_dir")
  val tempDir = dsmoq.getString("temp_dir")
  val messageDir = dsmoq.getString("message_dir")

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

  val imageDownloadRoot = dsmoq.getString("image_url_root")
  val fileDownloadRoot = dsmoq.getString("file_url_root")

  val clientId = conf.getString("oauth.client_id")
  val clientSecret = conf.getString("oauth.client_secret")
  val callbackUrl = conf.getString("oauth.callback_url")
  val scopes = conf.getStringList("oauth.scopes")
  val applicationName = conf.getString("oauth.application_name")
  val allowedMailaddrs = conf.getStringList("oauth.allowed_mailaddrs")

  val s3AccessKey = conf.getString("s3.access_key")
  val s3SecretKey = conf.getString("s3.secret_key")
  val s3UploadRoot = dsmoq.getString("upload_bucket")
}