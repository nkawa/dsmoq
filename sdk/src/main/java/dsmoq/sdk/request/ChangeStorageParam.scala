package dsmoq.sdk.request

import scala.beans.BeanProperty

case class ChangeStorageParam(@BeanProperty var saveLocal: Boolean, @BeanProperty var saveS3: Boolean) {
  private def param = {
    json.ChangeStorageJson(saveLocal, saveS3)
  }
  def toJsonString = param.toJsonString()
}
