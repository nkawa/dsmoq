package dsmoq.services.data

case class User(
  id: String,
  name: String,
  fullname: String,
  organization: String,
  title: String,
  image: String,
  mailAddress: String,
  description: String,
  isGuest: Boolean,
  isDeleted: Boolean
)

object User {
  def apply(x: dsmoq.persistence.User, address: String) = new User(
    id = x.id,
    name = x.name,
    fullname = x.fullname,
    organization = x.organization,
    title = x.title,
    image = dsmoq.AppConf.imageDownloadRoot + x.imageId,
    mailAddress = address,
    description = x.description,
    isGuest = false,
    isDeleted = x.deletedAt.isDefined
  )
}