package dsmoq

import com.typesafe.config.ConfigFactory

/**
 * Created by terurou on 14/03/17.
 */
object AppConf {
  private val conf = ConfigFactory.load
  private val dsmoq = conf.getConfig("dsmoq").getConfig(System.getProperty(org.scalatra.EnvironmentKey))

  val imageDir = dsmoq.getString("image_dir")
  val fileDir = dsmoq.getString("file_dir")

  val systemUserId = dsmoq.getString("system_user_uuid")
  val guestGroupId = dsmoq.getString("guest_group_uuid")

  val defaultDatasetImageId = dsmoq.getString("default_dataset_image_uuid")
  val defaultGroupImageId = dsmoq.getString("default_group_image_uuid")
  val defaultLicenseId = dsmoq.getString("default_license_uuid")

  val imageDownloadRoot = dsmoq.getString("image_url_root")
  val fileDownloadRoot = dsmoq.getString("file_url_root")

  val clientId = conf.getString("oauth.client_id")
  val clientSecret = conf.getString("oauth.client_secret")
  val callbackUrl = conf.getString("oauth.callback_url")
  val scopes = conf.getStringList("oauth.scopes")
  val applicationName = conf.getString("oauth.application_name")
}