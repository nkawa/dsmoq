package dsmoq.sdk.request

import scala.beans.BeanProperty

case class UpdateGroupParam(@BeanProperty var name: String, @BeanProperty var description: String) {
  private def param = json.UpdateGroupJson(name, description)
  def toJsonString = param.toJsonString()
}