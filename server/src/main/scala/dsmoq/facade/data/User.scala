package dsmoq.facade.data

case class User(
  id: String,
  name: String,
  fullname: String,
  organization: String,
  title: String,
  image: String,
  isGuest: Boolean,
  isDeleted: Boolean
)

object User {
  def apply(x: dsmoq.persistence.User) = new User(
    id = x.id,
    name = x.name,
    fullname = x.fullname,
    organization = x.organization,
    title = x.title,
    image = "", //TODO
    isGuest = false,
    isDeleted = x.deletedAt.isDefined
  )
}