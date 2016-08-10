package jp.ac.nagoya_u.dsmoq.sdk.request

import java.util.{ Collections, Optional }

import jp.ac.nagoya_u.dsmoq.sdk.request.ConvertOptional._
import scala.beans.BeanProperty
import scala.collection.JavaConverters._

case class GetDatasetsParam(
    @BeanProperty var query: Optional[String],
    @BeanProperty var owners: java.util.List[String],
    @BeanProperty var groups: java.util.List[String],
    @BeanProperty var attributes: java.util.List[Attribute],
    @BeanProperty var limit: Optional[Integer],
    @BeanProperty var offset: Optional[Integer]) {

  private def param: json.GetDatasetsJson = {
    json.GetDatasetsJson(
      query.toOption,
      owners.asScala.toList,
      groups.asScala.toList,
      attributes.asScala.map(attr => new json.Attribute(attr.getId, attr.getValue)).toList,
      limit.toOption.map(x => x.intValue()),
      offset.toOption.map(x => x.intValue()))
  }

  def this() = this(Optional.empty(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Optional.empty(), Optional.empty())

  def toJsonString = param.toJsonString()

}

case class Attribute(@BeanProperty var id: String, @BeanProperty var value: String)
