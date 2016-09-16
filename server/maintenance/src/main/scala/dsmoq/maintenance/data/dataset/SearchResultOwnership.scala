package dsmoq.maintenance.data.dataset

import dsmoq.persistence

sealed trait OwnerType
object OwnerType {
  case object User extends OwnerType {
    override def toString(): String = "ユーザー"
  }
  case object Group extends OwnerType {
    override def toString(): String = "グループ"
  }
  def apply(ownerType: Int): OwnerType = {
    ownerType match {
      case persistence.OwnerType.Group => OwnerType.Group
      case _ => OwnerType.User
    }
  }
}

sealed trait AccessLevel
object AccessLevel {
  case object LimitedRead extends AccessLevel {
    override def toString(): String = "Limited Read"
  }
  case object FullRead extends AccessLevel {
    override def toString(): String = "Full Read"
  }
  case object Owner extends AccessLevel
  case object Provider extends AccessLevel
  case object Deny extends AccessLevel
  def apply(ownerType: OwnerType, accessLevel: Int): AccessLevel = {
    (ownerType, accessLevel) match {
      case (OwnerType.User, persistence.UserAccessLevel.LimitedRead) => AccessLevel.LimitedRead
      case (OwnerType.User, persistence.UserAccessLevel.FullPublic) => AccessLevel.FullRead
      case (OwnerType.User, persistence.UserAccessLevel.Owner) => AccessLevel.Owner
      case (OwnerType.Group, persistence.GroupAccessLevel.LimitedPublic) => AccessLevel.LimitedRead
      case (OwnerType.Group, persistence.GroupAccessLevel.FullPublic) => AccessLevel.FullRead
      case (OwnerType.Group, persistence.GroupAccessLevel.Provider) => AccessLevel.Provider
      case _ => AccessLevel.Deny
    }
  }
}

case class SearchResultOwnership(
  id: String,
  ownerType: OwnerType,
  name: String,
  accessLevel: AccessLevel
)
