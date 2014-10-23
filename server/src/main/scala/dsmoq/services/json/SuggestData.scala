package dsmoq.services.json

import dsmoq.persistence.SuggestType

object SuggestData {
  case class User(
    id: String,
    name: String,
    fullname: String,
    organization: String,
    image: String
  )

  case class UserWithType(
    id: String,
    name: String,
    fullname: String,
    organization: String,
    image: String,
    dataType: Int = SuggestType.User
   )

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
  )
}
