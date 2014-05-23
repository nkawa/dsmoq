package dsmoq.services.data

import org.scalatra.servlet.FileItem

object ProfileData {
  case class UpdateProfileParams(
    name: String,
    fullname: String,
    organization: String,
    title: String,
    description: String,
    image: Option[FileItem]
  )

  case class Account(
    id: String,
    name: String,
    image: String
  )
}
