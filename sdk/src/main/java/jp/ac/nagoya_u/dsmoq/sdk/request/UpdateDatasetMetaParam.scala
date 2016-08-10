package jp.ac.nagoya_u.dsmoq.sdk.request

import java.util.Collections

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

case class UpdateDatasetMetaParam(
    @BeanProperty var name: String,
    @BeanProperty var description: String,
    @BeanProperty var license: String,
    @BeanProperty var attributes: java.util.List[Attribute]) {
  private def param = json.UpdateDatasetMetaJson(name, description, license, attributes.asScala.map(attr => new json.Attribute(attr.getId, attr.getValue)).toList)
  def this() = this("", "", "", Collections.emptyList())
  def toJsonString = param.toJsonString()
}
