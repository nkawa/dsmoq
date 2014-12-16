package dsmoq.sdk.request

import java.util.Optional

import dsmoq.sdk.request.ConvertOptional._
import scala.beans.BeanProperty

case class GetMembersParam(@BeanProperty var limit: Optional[Integer], @BeanProperty var offset: Optional[Integer]) {
  private def param = json.GetMembersJson(limit.toOption.map(x => x.intValue()), offset.toOption.map(x => x.intValue()))
  def toJsonString = param.toJsonString()
}
