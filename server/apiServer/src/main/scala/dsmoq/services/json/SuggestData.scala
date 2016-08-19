package dsmoq.services.json

import dsmoq.persistence.SuggestType

object SuggestData {
  sealed trait WithType {
    def dataType: Int
  }

  case class User(
    id: String,
    name: String,
    fullname: String,
    organization: String,
    title: String,
    description: String,
    image: String
  )

  case class UserWithType(
    id: String,
    name: String,
    fullname: String,
    organization: String,
    image: String,
    // TODO title: String,
    // TODO description: String,
    dataType: Int = SuggestType.User
  ) extends WithType

  case class Group(
    id: String,
    name: String,
    image: String
  )

  case class GroupWithType(
    id: String,
    name: String,
    image: String,
    dataType: Int = SuggestType.Group
  ) extends WithType
}
