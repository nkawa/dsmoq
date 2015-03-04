package dsmoq.sdk.request

import scala.beans.BeanProperty

case class UpdateProfileParam(
  @BeanProperty var name: String,
  @BeanProperty var fullname: String,
  @BeanProperty var organization: String,
  @BeanProperty var title: String,
  @BeanProperty var description: String
) {
  private def param = json.UpdateProfileJson(name, fullname, organization, title, description)
  def this() = this("", "", "", "", "")
  def toJsonString = param.toJsonString()
}
