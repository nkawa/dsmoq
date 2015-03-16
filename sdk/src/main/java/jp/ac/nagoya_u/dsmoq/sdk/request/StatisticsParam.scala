package jp.ac.nagoya_u.dsmoq.sdk.request

import java.util.Optional

import org.joda.time.DateTime
import jp.ac.nagoya_u.dsmoq.sdk.request.ConvertOptional._
import scala.beans.BeanProperty

case class StatisticsParam(@BeanProperty var from: Optional[DateTime], @BeanProperty var to: Optional[DateTime]) {
  private def param = {
    json.StatisticsJson(from.toOption, to.toOption)
  }
  def this() = this(Optional.empty(), Optional.empty())
  def toJsonString = param.toJsonString()
}
