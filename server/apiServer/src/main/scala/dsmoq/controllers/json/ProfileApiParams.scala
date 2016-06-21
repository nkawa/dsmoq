package dsmoq.controllers.json

import org.scalatra.servlet.FileItem

case class UpdateProfileParams(
  name: Option[String] = None,
  fullname: Option[String] = None,
  organization: Option[String] = None,
  title: Option[String] = None,
  description: Option[String] = None
)

case class UpdateMailAddressParams(
  email: Option[String] = None
)

case class UpdatePasswordParams(
  currentPassword: Option[String] = None,
  newPassword: Option[String] = None
)