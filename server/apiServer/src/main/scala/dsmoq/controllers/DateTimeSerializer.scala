package dsmoq.controllers

import java.math.BigDecimal

import org.joda.time.DateTime
import org.json4s.CustomSerializer
import org.json4s.JDecimal

/**
 * DateTime用のJSONカスタムシリアライザ
 */
case object DateTimeSerializer extends CustomSerializer[DateTime](format => (
  {
    case JDecimal(x) => new DateTime(x.longValue)
  },
  {
    case d: DateTime => JDecimal(BigDecimal.valueOf(d.getMillis))
  }
))
