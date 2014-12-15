package dsmoq.sdk.request

import scala.beans.BeanProperty

class UpdateFileMetaParam(@BeanProperty var name: String, @BeanProperty var description: String) {
  private def param = json.UpdateFileMetaJson(name, description)
  def toJsonString = param.toJsonString()
}
