package dsmoq.sdk.request

import scala.beans.BeanProperty

case class SetGuestAccessLevelParam(@BeanProperty var accessLevel: Int) {
  private def param = json.SetGuestAccessLevelJson(accessLevel)
  def this() = this(0)
  def toJsonString = param.toJsonString()
}
