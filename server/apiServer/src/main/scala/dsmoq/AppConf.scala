package dsmoq

import scala.collection.JavaConverters._

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
  private val sys = ConfigFactory.load("system.conf")

  val port = root.getInt("apiserver.port")
  val imageDir = root.getString("apiserver.image_dir")
  val fileDir = root.getString("apiserver.file_dir")
  val tempDir = root.getString("apiserver.temp_dir")
  val messageDir = root.getString("apiserver.message_dir")
  val appDir = if (root.hasPath("apiserver.app_dir")) {
    root.getString("apiserver.app_dir")
  } else {
    fileDir + "/../jws"
  }

  val systemUserId = sys.getString("system.user.system.id")
  val guestGroupId = sys.getString("system.group.guest")
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

  val defaultAvatarImageId = sys.getString("system.image.default.avatar")
  val defaultDatasetImageId = sys.getString("system.image.default.dataset")
  val defaultGroupImageId = sys.getString("system.image.default.group")
  val defaultLicenseId = sys.getString("system.image.default.license")
  val defaultFeaturedImageIds = sys.getStringList("system.image.default.featured").asScala

  val urlRoot = root.getString("apiserver.url_root")
  val imageDownloadRoot = root.getString("apiserver.image_url_root")
  val fileDownloadRoot = root.getString("apiserver.file_url_root")
  val appDownloadRoot = if (root.hasPath("apiserver.app_url_root")) {
    root.getString("apiserver.app_url_root")
  } else {
    fileDownloadRoot + "../apps/"
  }

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
