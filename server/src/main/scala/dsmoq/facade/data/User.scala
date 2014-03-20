package dsmoq.facade.data

case class User(
  id: String,
  name: String,
  fullname: String,
  organization: String,
  title: String,
  image: String,
  isGuest: Boolean
)