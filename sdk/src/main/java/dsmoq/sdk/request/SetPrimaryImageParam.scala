package dsmoq.sdk.request

import scala.beans.BeanProperty

case class SetPrimaryImageParam(@BeanProperty var imageId: String) {
  private def param = json.SetPrimaryImageJson(imageId)
  def toJsonString = param.toJsonString()
}
