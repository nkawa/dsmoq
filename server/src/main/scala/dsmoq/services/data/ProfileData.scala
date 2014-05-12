package dsmoq.services.data

import org.scalatra.servlet.FileItem

object ProfileData {
  case class UpdateProfileParams(
    name: String,
    fullname: String,
    organization: String,
    title: String,
    description: String,
    icon: Option[FileItem]
  )
}
