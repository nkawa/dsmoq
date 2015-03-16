package jp.ac.nagoya_u.dsmoq.sdk.request

import scala.beans.BeanProperty

class UpdateFileMetaParam(@BeanProperty var name: String, @BeanProperty var description: String) {
  private def param = json.UpdateFileMetaJson(name, description)
  def this() = this("", "")
  def toJsonString = param.toJsonString()
}
