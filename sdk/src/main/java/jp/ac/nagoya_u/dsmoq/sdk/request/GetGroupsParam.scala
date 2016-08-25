package jp.ac.nagoya_u.dsmoq.sdk.request

import java.util.Optional

import jp.ac.nagoya_u.dsmoq.sdk.request.ConvertOptional._
import scala.beans.BeanProperty

class GetGroupsParam(
  @BeanProperty var query: Optional[String],
  @BeanProperty var user: Optional[String],
  @BeanProperty var limit: Optional[Integer],
  @BeanProperty var offset: Optional[Integer]
) {
  private def param = json.GetGroupsJson(
    query.toOption,
    user.toOption,
    limit.toOption.map(x => x.intValue()),
    offset.toOption.map(x => x.intValue())
  )
  def this() = this(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
  def toJsonString = param.toJsonString()
}
