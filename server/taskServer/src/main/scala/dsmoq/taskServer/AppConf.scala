package dsmoq.taskServer

import com.typesafe.config.ConfigFactory

object AppConf {
  private val conf = ConfigFactory.load
  private val root = if (System.getProperty(Main.EnvironmentKey) == "test") conf.getConfig("test") else conf

  val fileDir = root.getString("taskserver.file_dir")
  val sampling_cycle = root.getLong("taskserver.sampling_cycle")
  val delete_cycle = root.getLong("taskserver.delete_cycle")
  val sampling_unit = root.getString("taskserver.sampling_unit")
  val delete_unit = root.getString("taskserver.delete_unit")

  val systemUserId = "dccc110c-c34f-40ed-be2c-7e34a9f1b8f0"

  val s3AccessKey = root.getString("s3.access_key")
  val s3SecretKey = root.getString("s3.secret_key")
  val s3UploadRoot = root.getString("s3.upload_bucket")
}
