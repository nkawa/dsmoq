package dsmoq.sdk.request

import scala.beans.BeanProperty

case class UpdateEmailParam(@BeanProperty var email: String) {
  private def param = json.UpdateEmailJson(email)
  def toJsonString = param.toJsonString()
}
