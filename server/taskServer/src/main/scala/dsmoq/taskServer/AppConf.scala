package dsmoq.taskServer

import com.typesafe.config.ConfigFactory

object AppConf {
  private val conf = ConfigFactory.load
  private val dsmoq = conf.getConfig("dsmoq").getConfig(System.getProperty(Main.EnvironmentKey))
  private val s3 = conf.getConfig("s3").getConfig(System.getProperty(Main.EnvironmentKey))

  val fileDir = dsmoq.getString("file_dir")
  val sampling_cycle = dsmoq.getLong("sampling_cycle")
  val delete_cycle = dsmoq.getLong("delete_cycle")
  val sampling_unit = dsmoq.getString("sampling_unit")
  val delete_unit = dsmoq.getString("delete_unit")

  val systemUserId = "dccc110c-c34f-40ed-be2c-7e34a9f1b8f0"

  val s3AccessKey = conf.getString("s3.access_key")
  val s3SecretKey = conf.getString("s3.secret_key")
  val s3UploadRoot = s3.getString("upload_bucket")
}
