package dsmoq.sdk.request

import scala.beans.BeanProperty

case class CreateGroupParam(@BeanProperty var name: String, @BeanProperty var description: String) {
  private def param = json.CreateGroupJson(name, description)
  def this() = this("", "")
  def toJsonString = param.toJsonString()
}