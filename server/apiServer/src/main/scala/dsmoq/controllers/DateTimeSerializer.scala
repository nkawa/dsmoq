package dsmoq.controllers

import java.math.BigDecimal

import scala.util.Try

import org.joda.time.DateTime
import org.json4s.CustomSerializer
import org.json4s.JDecimal
import org.json4s.JDouble
import org.json4s.JInt
import org.json4s.JString

/**
 * DateTime用のJSONカスタムシリアライザ
 */
case object DateTimeSerializer extends CustomSerializer[DateTime](format => (
  {
    case JInt(x) => new DateTime(x.longValue)
    case JDouble(x) => new DateTime(x.longValue)
    case JDecimal(x) => new DateTime(x.longValue)
    case JString(x) if Try(x.toLong).isSuccess => new DateTime(x.toLong)
    case JString(x) if Try(DateTime.parse(x)).isSuccess => DateTime.parse(x)
  },
  {
    case d: DateTime => JDecimal(BigDecimal.valueOf(d.getMillis))
  }
))
