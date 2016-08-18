package jp.ac.nagoya_u.dsmoq.sdk.request

import java.util.Optional

import jp.ac.nagoya_u.dsmoq.sdk.request.ConvertOptional._
import scala.beans.BeanProperty

class GetMembersParam(@BeanProperty var limit: Optional[Integer], @BeanProperty var offset: Optional[Integer]) {
  private def param = json.GetMembersJson(limit.toOption.map(x => x.intValue()), offset.toOption.map(x => x.intValue()))
  def this() = this(Optional.empty(), Optional.empty())
  def toJsonString = param.toJsonString()
}
