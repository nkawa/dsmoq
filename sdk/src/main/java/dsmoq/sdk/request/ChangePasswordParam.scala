package dsmoq.sdk.request

import scala.beans.BeanProperty

case class ChangePasswordParam(@BeanProperty var currentPassword: String, @BeanProperty var newPassword: String) {
  private def param = json.ChangePasswordJson(currentPassword, newPassword)
  def toJsonString = param.toJsonString()
}
