package dsmoq.services

case class ProfileData(
  id: String,
  name: String,
  fullname: String,
  organization: String,
  title: String,
  image: String,
  mailAddress: String,
  description: String,
  isGuest: Boolean,
  isDeleted: Boolean,
  isGoogleUser: Boolean
)

object ProfileData {
  def apply(x: dsmoq.services.User, isGoogleUser: Boolean) = new ProfileData(
    id = x.id,
    name = x.name,
    fullname = x.fullname,
    organization = x.organization,
    title = x.title,
    image = x.image,
    mailAddress = x.mailAddress,
    description = x.description,
    isGuest = x.isGuest,
    isDeleted = x.isDeleted,
    isGoogleUser = isGoogleUser
  )
}
