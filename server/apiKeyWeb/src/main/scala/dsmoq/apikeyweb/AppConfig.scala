package dsmoq.apikeyweb

import com.typesafe.config.ConfigFactory

/**
  * アプリ設定
  */
object AppConfig {
  private val config = ConfigFactory.load
  private val KEY_APP_PORT = "app.port"
  private val KEY_APP_USER = "app.user"
  private val KEY_APP_PASSWORD = "app.password"

  val port = if (config.hasPath(KEY_APP_PORT)) config.getInt(KEY_APP_PORT) else 8090
  val user = if (config.hasPath(KEY_APP_USER)) config.getString(KEY_APP_USER) else "user"
  val password = if (config.hasPath(KEY_APP_PASSWORD)) config.getString(KEY_APP_PASSWORD) else "pass"
}
