package dsmoq.sdk.request.json

import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write

trait Jsonable {
  implicit val formats = Serialization.formats(NoTypeHints)
  def toJsonString(): String = write(this)
}
