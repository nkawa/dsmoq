package jp.ac.nagoya_u.dsmoq.sdk.request

import scala.beans.BeanProperty

case class SetMemberRoleParam(@BeanProperty var role: Int) {
  private def param = json.SetMemberRoleJson(role)
  def this() = this(0)
  def toJsonString = param.toJsonString()
}
