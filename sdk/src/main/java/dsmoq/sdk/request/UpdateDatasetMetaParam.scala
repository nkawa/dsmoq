package dsmoq.sdk.request

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

case class UpdateDatasetMetaParam(
  @BeanProperty var name: String,
  @BeanProperty var description: String,
  @BeanProperty var license: String,
  @BeanProperty var attributes: java.util.List[Attribute]
) {
  private def param = json.UpdateDatasetMetaJson(name, description, license, attributes.asScala.map(attr => new json.Attribute(attr.getId, attr.getValue)).toList)
  def toJsonString = param.toJsonString()
}
