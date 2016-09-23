package dsmoq.services

import dsmoq.AppConf
import dsmoq.persistence

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
  isDisabled: Boolean
)

object User {
  def apply(x: persistence.User, address: String): User = User(
    id = x.id,
    name = x.name,
    fullname = x.fullname,
    organization = x.organization,
    title = x.title,
    image = dsmoq.AppConf.imageDownloadRoot + "user/" + x.id + "/" + x.imageId,
    mailAddress = address,
    description = x.description,
    isGuest = false,
    isDisabled = x.disabled
  )
}
