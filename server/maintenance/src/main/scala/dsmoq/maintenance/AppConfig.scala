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

  val systemUserId = "dccc110c-c34f-40ed-be2c-7e34a9f1b8f0"
}
