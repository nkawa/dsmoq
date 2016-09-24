package dsmoq.maintenance

import com.typesafe.config.ConfigFactory

/**
 * アプリ設定
 */
object AppConfig {
  private val config = ConfigFactory.load

  val port = config.getInt("maintenance.port")
  val user = config.getString("maintenance.user")
  val password = config.getString("maintenance.password")

  val dsmoqUrlRoot = config.getString("maintenance.dsmoq_url_root")
  val searchLimit = config.getInt("maintenance.search_limit")
  val fileRoot = config.getInt("maintenance.file_root_dir")

  val systemUserId = "dccc110c-c34f-40ed-be2c-7e34a9f1b8f0"

  val s3AccessKey = config.getString("s3.access_key")
  val s3SecretKey = config.getString("s3.secret_key")
  val s3UploadRoot = config.getString("s3.upload_bucket")
}
