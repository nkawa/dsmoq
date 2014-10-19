package dsmoq.controllers.json

case class SigninParams(
  id: Option[String] = None,
  password: Option[String] = None
)
