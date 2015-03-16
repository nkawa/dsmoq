package jp.ac.nagoya_u.dsmoq.sdk.request

import scala.beans.BeanProperty

case class ChangeStorageParam(@BeanProperty var saveLocal: Boolean, @BeanProperty var saveS3: Boolean) {
  private def param = {
    json.ChangeStorageJson(saveLocal, saveS3)
  }
  def this() = this(false, false)
  def toJsonString = param.toJsonString()
}
