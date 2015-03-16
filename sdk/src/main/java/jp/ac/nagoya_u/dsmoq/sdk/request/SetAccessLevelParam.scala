package jp.ac.nagoya_u.dsmoq.sdk.request

import org.json4s.NoTypeHints

import scala.beans.BeanProperty
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write
import scala.collection.JavaConverters._

case class SetAccessLevelParam(@BeanProperty var id: String, @BeanProperty var ownerType: Int, @BeanProperty var accessLevel: Int) {
  private def param = json.SetAccessLevelJson(id, ownerType, accessLevel)
  def this() = this("", 0, 0)
}

object SetAccessLevelParam {
  private implicit val formats = Serialization.formats(NoTypeHints)
  def toJsonString(params: java.util.List[SetAccessLevelParam]) = write(params.asScala.map(p => p.param).toList)
}