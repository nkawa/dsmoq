package jp.ac.nagoya_u.dsmoq.sdk.request

import java.util.Collections

import jp.ac.nagoya_u.dsmoq.sdk.request.json.{ UpdateDatasetAttributeJson, UpdateDatasetMetaJson }

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

case class UpdateDatasetMetaParam(
  @BeanProperty var name: String,
  @BeanProperty var description: String,
  @BeanProperty var license: String,
  @BeanProperty var attributes: java.util.List[UpdateDatasetAttributeParam]
) {
  private def param = UpdateDatasetMetaJson(
    name, description, license,
    attributes.asScala.map(attr => UpdateDatasetAttributeJson(attr.name, attr.value)).toList
  )
  def this() = this("", "", "", Collections.emptyList())
  def toJsonString = param.toJsonString()
}

case class UpdateDatasetAttributeParam(@BeanProperty var name: String, @BeanProperty var value: String)
