package dsmoq.apikeyweb

import com.typesafe.config.ConfigFactory

/**
  * アプリ設定
  */
object AppConfig {
  private val config = ConfigFactory.load

  val port = config.getInt("apikey.port")
  val user = config.getString("apikey.user")
  val password = config.getString("apikey.password")
}
