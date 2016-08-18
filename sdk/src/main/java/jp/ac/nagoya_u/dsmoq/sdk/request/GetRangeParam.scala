package jp.ac.nagoya_u.dsmoq.sdk.request

import java.util.Optional

import jp.ac.nagoya_u.dsmoq.sdk.request.ConvertOptional._
import scala.beans.BeanProperty
import scala.language.reflectiveCalls

case class GetRangeParam(@BeanProperty var offset: Optional[Integer], @BeanProperty var limit: Optional[Integer]) {
  private def param = {
    json.GetRangeJson(limit.toOption.map(x => x.intValue()), offset.toOption.map(x => x.intValue()))
  }
  def this() = this(Optional.empty(), Optional.empty())
  def toJsonString = param.toJsonString()
}
