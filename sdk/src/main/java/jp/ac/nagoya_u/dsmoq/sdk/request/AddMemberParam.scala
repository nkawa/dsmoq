package jp.ac.nagoya_u.dsmoq.sdk.request

import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization._
import scala.collection.JavaConverters._

import scala.beans.BeanProperty

class AddMemberParam(@BeanProperty var userId: String, @BeanProperty var role: Int) {
  private def param = json.AddMemberJson(userId, role)
  def this() = this("", 0)
}

object AddMemberParam {
  private implicit val formats = Serialization.formats(NoTypeHints)
  def toJsonString(params: java.util.List[AddMemberParam]) = write(params.asScala.map(p => p.param).toList)
}