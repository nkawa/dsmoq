package dsmoq

import com.typesafe.config.ConfigFactory

/**
 * Created by terurou on 14/03/17.
 */
object AppConf {
  private val conf = ConfigFactory.load

  val imageDir = conf.getString("dsmoq.image_dir")
  val fileDir = conf.getString("dsmoq.file_dir")

  val systemUserId = conf.getString("dsmoq.system_user_uuid")
  val guestGroupId = conf.getString("dsmoq.guest_group_uuid")

  val defaultDatasetImageId = conf.getString("dsmoq.default_dataset_image_uuid")

  val clientId = conf.getString("oauth.client_id")
  val clientSecret = conf.getString("oauth.client_secret")
  val callbackUrl = conf.getString("oauth.callback_url")
  val scopes = conf.getStringList("oauth.scopes")
  val applicationName = conf.getString("oauth.application_name")
}