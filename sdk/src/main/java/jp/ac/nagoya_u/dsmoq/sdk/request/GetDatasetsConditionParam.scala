package jp.ac.nagoya_u.dsmoq.sdk.request

import java.util.Optional

import org.json4s.JsonAST.{ JArray, JDouble, JInt, JValue }
import org.json4s.jackson.JsonMethods
import org.json4s.{ JObject, JString }
import jp.ac.nagoya_u.dsmoq.sdk.request.ConvertOptional._

import scala.beans.BeanProperty
import scala.collection.mutable.ArrayBuffer

/** File size unit */
sealed trait SizeUnit { val value: String }
object SizeUnit {
  case object Byte extends SizeUnit { override val value = "byte" }
  case object KB extends SizeUnit { override val value = "kb" }
  case object MB extends SizeUnit { override val value = "mb" }
  case object GB extends SizeUnit { override val value = "gb" }
}

sealed trait SearchCondition

case class QueryContainCondition(@BeanProperty var value: String = "") extends SearchCondition
case class QueryNotContainCondition(@BeanProperty var value: String = "") extends SearchCondition
case class OwnerEqualCondition(@BeanProperty var value: String = "") extends SearchCondition
case class OwnerNotEqualCondition(@BeanProperty var value: String = "") extends SearchCondition
case class TagCondition(@BeanProperty var value: String) extends SearchCondition
case class AttributeCondition(
  @BeanProperty var key: String = "",
  @BeanProperty var value: String = ""
) extends SearchCondition
case class TotalSizeLessThanEqualCondition(
  @BeanProperty var value: Double = 0,
  @BeanProperty var unit: SizeUnit = SizeUnit.Byte
) extends SearchCondition
case class TotalSizeGreaterThanEqualCondition(
  @BeanProperty var value: Double = 0,
  @BeanProperty var unit: SizeUnit = SizeUnit.Byte
) extends SearchCondition
case class NumberOfFilesLessThanEqualCondition(
  @BeanProperty var value: Int = 0
) extends SearchCondition
case class NumberOfFilesGreaterThanEqualCondition(
  @BeanProperty var value: Int = 0
) extends SearchCondition
case class AccessLevelPublicCondition() extends SearchCondition
case class AccessLevelPrivateCondition() extends SearchCondition

class GetDatasetsConditionParam(
  @BeanProperty var limit: Optional[Integer],
  @BeanProperty var offset: Optional[Integer]
) {
  private val addCondition = ArrayBuffer.empty[JValue]
  private val orCondition = ArrayBuffer.empty[JValue]

  def this() = this(Optional.empty(), Optional.empty())

  def toJsonString: String = {
    if (addCondition.nonEmpty) {
      orCondition += JObject(
        List(
          "operator" -> JString("and"),
          "value" -> JArray(addCondition.toList)
        )
      )
    }
    val jsonData = JObject(
      "query" -> JObject(
        List(
          "operator" -> JString("or"),
          "value" -> JArray(orCondition.toList)
        )
      ),
      "limit" -> JInt(limit.toOption.getOrElse(new Integer(20)).intValue()),
      "offset" -> JInt(offset.toOption.getOrElse(new Integer(0)).intValue())
    )
    JsonMethods.compact(JsonMethods.render(jsonData))
  }

  def add(condition: SearchCondition): Unit = {
    convertToJson(condition).map(addCondition += _)
  }

  def or(): Unit = {
    if (addCondition.nonEmpty) {
      orCondition += JObject(
        List(
          "operator" -> JString("and"),
          "value" -> JArray(addCondition.toList)
        )
      )
      addCondition.clear()
    }
  }

  private def convertToJson(condition: SearchCondition): Option[JValue] = {
    condition match {
      case QueryContainCondition(value) =>
        Some(JObject(
          List(
            "target" -> JString("query"),
            "operator" -> JString("contain"),
            "value" -> JString(value)
          )
        ))
      case QueryNotContainCondition(value) =>
        Some(JObject(
          List(
            "target" -> JString("query"),
            "operator" -> JString("not-contain"),
            "value" -> JString(value)
          )
        ))
      case OwnerEqualCondition(value) =>
        Some(JObject(
          List(
            "target" -> JString("owner"),
            "operator" -> JString("equal"),
            "value" -> JString(value)
          )
        ))
      case OwnerNotEqualCondition(value) =>
        Some(JObject(
          List(
            "target" -> JString("owner"),
            "operator" -> JString("not-equal"),
            "value" -> JString(value)
          )
        ))
      case TagCondition(value) =>
        Some(JObject(
          List(
            "target" -> JString("tag"),
            "value" -> JString(value)
          )
        ))
      case AttributeCondition(key, value) =>
        Some(JObject(
          List(
            "target" -> JString("attribute"),
            "key" -> JString(key),
            "value" -> JString(value)
          )
        ))
      case TotalSizeLessThanEqualCondition(value, unit) =>
        Some(JObject(
          List(
            "target" -> JString("total-size"),
            "operator" -> JString("le"),
            "value" -> JDouble(value),
            "unit" -> JString(unit.value)
          )
        ))
      case TotalSizeGreaterThanEqualCondition(value, unit) =>
        Some(JObject(
          List(
            "target" -> JString("total-size"),
            "operator" -> JString("ge"),
            "value" -> JDouble(value),
            "unit" -> JString(unit.value)
          )
        ))
      case NumberOfFilesLessThanEqualCondition(value) =>
        Some(JObject(
          List(
            "target" -> JString("num-of-files"),
            "operator" -> JString("le"),
            "value" -> JInt(value)
          )
        ))
      case NumberOfFilesGreaterThanEqualCondition(value) =>
        Some(JObject(
          List(
            "target" -> JString("num-of-files"),
            "operator" -> JString("ge"),
            "value" -> JInt(value)
          )
        ))
      case AccessLevelPublicCondition() =>
        Some(JObject(
          List(
            "target" -> JString("public"),
            "value" -> JString("public")
          )
        ))
      case AccessLevelPrivateCondition() =>
        Some(JObject(
          List(
            "target" -> JString("public"),
            "value" -> JString("private")
          )
        ))
      case _ => None
    }
  }

}
