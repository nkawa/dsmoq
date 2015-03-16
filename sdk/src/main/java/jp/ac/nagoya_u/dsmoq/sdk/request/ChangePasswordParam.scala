package jp.ac.nagoya_u.dsmoq.sdk.request

import scala.beans.BeanProperty

case class ChangePasswordParam(@BeanProperty var currentPassword: String, @BeanProperty var newPassword: String) {
  private def param = json.ChangePasswordJson(currentPassword, newPassword)
  def this() = this("", "")
  def toJsonString = param.toJsonString()
}
