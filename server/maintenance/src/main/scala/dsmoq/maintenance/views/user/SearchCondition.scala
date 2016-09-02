package dsmoq.maintenance.views.user

case class SearchCondition(
  userType: SearchCondition.UserType,
  query: String,
  offset: Int,
  limit: Int
)
object SearchCondition {
  sealed trait UserType
  object UserType {
    case object All extends UserType
    case object Enabled extends UserType
    case object Disabled extends UserType
    def apply(str: Option[String]): UserType = {
      str match {
        case Some("enabled") => Enabled
        case Some("disabled") => Disabled
        case _ => All
      }
    }
  }
}
