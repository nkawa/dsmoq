package dsmoq.services.json

import org.json4s.CustomSerializer
import org.json4s.JArray
import org.json4s.JDecimal
import org.json4s.JDouble
import org.json4s.JInt
import org.json4s.JNull
import org.json4s.JObject
import org.json4s.JString
import org.json4s.JValue

sealed trait SearchDatasetCondition

object SearchDatasetCondition {
  case class Container(
    operator: Operators.Container,
    value: Seq[SearchDatasetCondition]
  ) extends SearchDatasetCondition

  case class Query(query: String = "", contains: Boolean = true) extends SearchDatasetCondition

  case class Owner(id: String, equals: Boolean = true) extends SearchDatasetCondition

  case class Tag(tag: String) extends SearchDatasetCondition

  case class Attribute(key: String = "", value: String = "") extends SearchDatasetCondition

  case class TotalSize(
    operator: Operators.Compare = Operators.Compare.GT,
    value: Double = 0,
    unit: SizeUnit = SizeUnit.KB
  ) extends SearchDatasetCondition

  case class NumOfFiles(
    operator: Operators.Compare = Operators.Compare.GT,
    value: Int = 0
  ) extends SearchDatasetCondition

  case class Public(public: Boolean = true) extends SearchDatasetCondition

  object Operators {
    sealed trait Container
    object Container {
      case object AND extends Container
      case object OR extends Container
    }
    sealed trait Compare
    object Compare {
      case object LT extends Compare
      case object GT extends Compare
    }
  }

  sealed trait SizeUnit {
    def magnification: Double
  }
  object SizeUnit {
    case object KB extends SizeUnit {
      val magnification = 1024D
    }
    case object MB extends SizeUnit {
      val magnification = 1024D * 1024
    }
    case object GB extends SizeUnit {
      val magnification = 1024D * 1024 * 1024
    }
  }

  def toJson(x: SearchDatasetCondition): JValue = {
    x match {
      case Container(op, xs) => {
        val operator = op match {
          case Operators.Container.AND => JString("and")
          case Operators.Container.OR => JString("or")
        }
        JObject(
          List(
            "operator" -> operator,
            "value" -> JArray(xs.map(toJson).toList)
          )
        )
      }
      case Query(value, contains) => {
        JObject(
          List(
            "target" -> JString("query"),
            "operator" -> JString(if (contains) "contain" else "not-contain"),
            "value" -> JString(value)
          )
        )
      }
      case Owner(value, equals) => {
        JObject(
          List(
            "target" -> JString("owner"),
            "operator" -> JString(if (equals) "equal" else "not-equal"),
            "value" -> JString(value)
          )
        )
      }
      case Tag(value) => {
        JObject(
          List(
            "target" -> JString("tag"),
            "value" -> JString(value)
          )
        )
      }
      case Attribute(key, value) => {
        JObject(
          List(
            "target" -> JString("attribute"),
            "key" -> JString(key),
            "value" -> JString(value)
          )
        )
      }
      case TotalSize(op, value, u) => {
        val operator = op match {
          case Operators.Compare.LT => JString("lt")
          case Operators.Compare.GT => JString("gt")
        }
        val unit = u match {
          case SizeUnit.KB => JString("kb")
          case SizeUnit.MB => JString("mb")
          case SizeUnit.GB => JString("gb")
        }
        JObject(
          List(
            "target" -> JString("total-size"),
            "operator" -> operator,
            "unit" -> unit,
            "value" -> JDouble(value)
          )
        )
      }
      case NumOfFiles(op, value) => {
        val operator = op match {
          case Operators.Compare.LT => JString("lt")
          case Operators.Compare.GT => JString("gt")
        }
        JObject(
          List(
            "target" -> JString("num-of-files"),
            "operator" -> operator,
            "value" -> JInt(value)
          )
        )
      }
      case Public(public) => {
        JObject(
          List(
            "target" -> JString("public"),
            "value" -> JString(if (public) "public" else "private")
          )
        )
      }
    }
  }

  def unapply(x: JValue): Option[SearchDatasetCondition] = {
    x match {
      case JObject(fs) => {
        val fields = fs.toMap
        val t = fields.getOrElse("target", JNull)
        val op = fields.getOrElse("operator", JNull)
        val v = fields.getOrElse("value", JNull)
        (t, op, v) match {
          case (_, JString("or"), JArray(xs)) => {
            sequence(xs.map(unapply)).map { value =>
              Container(Operators.Container.OR, value)
            }
          }
          case (_, JString("and"), JArray(xs)) => {
            sequence(xs.map(unapply)).map { value =>
              Container(Operators.Container.AND, value)
            }
          }
          case (JString("query"), _, JString(value)) => {
            Some(Query(value, op != JString("not-contain")))
          }
          case (JString("owner"), _, JString(value)) => {
            Some(Owner(value, op != JString("not-equal")))
          }
          case (JString("tag"), _, JString(value)) => {
            Some(Tag(value))
          }
          case (JString("attribute"), _, _) => {
            val key = fields.get("key").collect { case JString(x) => x }.getOrElse("")
            val value = v match {
              case JString(x) => x
              case _ => ""
            }
            Some(Attribute(key, value))
          }
          case (JString("total-size"), _, _) => {
            val value = doubleValueOf(v).getOrElse(0D)
            val operator = if (op == JString("lt")) Operators.Compare.LT else Operators.Compare.GT
            val unit = fields.get("unit").collect {
              case JString("mb") => SizeUnit.MB
              case JString("gb") => SizeUnit.GB
            }.getOrElse(SizeUnit.KB)
            Some(TotalSize(operator, value, unit))
          }
          case (JString("num-of-files"), _, JInt(value)) => {
            val operator = if (op == JString("lt")) Operators.Compare.LT else Operators.Compare.GT
            Some(NumOfFiles(operator, value.toInt))
          }
          case (JString("public"), _, JString(value)) => {
            Some(Public(op != JString("private")))
          }
          case _ => None
        }
      }
      case _ => None
    }
  }

  def sequence[A](xs: Seq[Option[A]]): Option[Seq[A]] = {
    val ret = xs.flatten
    if (ret.size == xs.size) Some(ret) else None
  }

  def doubleValueOf(x: JValue): Option[Double] = {
    x match {
      case JInt(x) => Some(x.doubleValue)
      case JDouble(x) => Some(x)
      case JDecimal(x) => Some(x.doubleValue)
      case _ => None
    }
  }
}

object SearchDatasetConditionSerializer extends CustomSerializer[SearchDatasetCondition](formats => (
  {
    case SearchDatasetCondition(p) => p
  },
  {
    case x: SearchDatasetCondition => SearchDatasetCondition.toJson(x)
  }
))
