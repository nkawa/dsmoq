package dsmoq.sdk.request

import scala.beans.BeanProperty

case class SetMemberRoleParam(@BeanProperty var role: Int) {
  private def param = json.SetMemberRoleJson(role)
  def toJsonString = param.toJsonString()
}
