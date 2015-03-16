package jp.ac.nagoya_u.dsmoq.sdk.request

import scala.beans.BeanProperty

case class UpdateEmailParam(@BeanProperty var email: String) {
  private def param = json.UpdateEmailJson(email)
  def this() = this("")
  def toJsonString = param.toJsonString()
}
