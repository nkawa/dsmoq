package jp.ac.nagoya_u.dsmoq.sdk.request

import scala.beans.BeanProperty

case class SetFeaturedImageToDatasetParam(@BeanProperty var imageId: String) {
  private def param = json.SetFeaturedImageToDatasetJson(imageId)
  def this() = this("")
  def toJsonString = param.toJsonString()
}
