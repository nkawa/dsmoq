package dsmoq.sdk.request

import scala.beans.BeanProperty

class AddMemberParam(@BeanProperty var userId: String, @BeanProperty var role: Int) {
  private def param = json.AddMemberJson(userId, role)
  def this() = this("", 0)
  def toJsonString = param.toJsonString()
}
