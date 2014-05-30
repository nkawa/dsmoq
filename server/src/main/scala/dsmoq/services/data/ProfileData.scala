package dsmoq.services.data

import org.scalatra.servlet.FileItem

object ProfileData {
  case class UpdateProfileParams(
    name: Option[String],
    fullname: Option[String],
    organization: Option[String],
    title: Option[String],
    description: Option[String],
    image: Option[FileItem]
  )
}
