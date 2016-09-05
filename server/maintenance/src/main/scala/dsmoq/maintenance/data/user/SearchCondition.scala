package dsmoq.maintenance.data.user

import scala.util.Try

import dsmoq.maintenance.AppConfig

case class SearchCondition(
  userType: SearchCondition.UserType,
  query: String,
  offset: Int,
  limit: Int
) {
  import SearchCondition._

  def toMap: Map[String, String] = {
    Map(
      "userType" -> userType.toString,
      "query" -> query,
      "offset" -> offset.toString,
      "limit" -> limit.toString
    )
  }
}

object SearchCondition {
  sealed trait UserType
  object UserType {
    case object All extends UserType {
      override def toString(): String = "all"
    }
    case object Enabled extends UserType {
      override def toString(): String = "enabled"
    }
    case object Disabled extends UserType {
      override def toString(): String = "disabled"
    }
    def apply(str: Option[String]): UserType = {
      str match {
        case Some("enabled") => Enabled
        case Some("disabled") => Disabled
        case _ => All
      }
    }
  }

  def fromMap(map: Map[String, String]): SearchCondition = {
    SearchCondition(
      userType = UserType(map.get("userType")),
      query = map.getOrElse("query", ""),
      offset = toInt(map.get("offset"), 0),
      limit = toInt(map.get("limit"), AppConfig.searchLimit)
    )
  }

  def toInt(str: Option[String], default: Int): Int = {
    str.flatMap(s => Try(s.toInt).toOption).filter(_ >= 0).getOrElse(default)
  }
}
