package dsmoq.sdk.request

import scala.beans.BeanProperty

case class SigninParam(@BeanProperty var id: String, @BeanProperty var password: String) {
  private def param = json.SigninJson(id, password)
  def this() = this("", "")
  def toJsonString = param.toJsonString()
}
