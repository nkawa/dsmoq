package dsmoq

import com.typesafe.config.ConfigFactory

object AppConf {
  private val conf = ConfigFactory.load
  private val dsmoq = conf.getConfig("dsmoq").getConfig(System.getProperty(org.scalatra.EnvironmentKey))

  val imageDir = dsmoq.getString("image_dir")
  val fileDir = dsmoq.getString("file_dir")

  val systemUserId = "dccc110c-c34f-40ed-be2c-7e34a9f1b8f0"
  val guestUserId = "6afb4198-859d-4053-8a15-5c791f3a8089"
  val guestGroupId = "f274a75c-e20e-4b4a-8db9-566fd41aa1bd"

  val defaultDatasetImageId = "8b570468-9814-4d30-8c04-392b263b6404"
  val defaultGroupImageId = "960a5601-2b60-2531-e6ad-54b91612ede5"
  val defaultLicenseId = "dc16a22c-00de-b00e-dbec-38cbad333bf7"

  val imageDownloadRoot = dsmoq.getString("image_url_root")
  val fileDownloadRoot = dsmoq.getString("file_url_root")

  val s3UploadAccessKey = conf.getString("s3.upload_access_key")
  val s3UploadSecretKey = conf.getString("s3.upload_secret_key")
  val s3DownloadAccessKey = conf.getString("s3.download_access_key")
  val s3DownloadSecretKey = conf.getString("s3.download_secret_key")
  val s3UploadRoot = conf.getString("s3.upload_bucket")
}