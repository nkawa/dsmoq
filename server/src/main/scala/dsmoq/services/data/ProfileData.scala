package dsmoq.services.data

object ProfileData {
  case class UpdateProfileParams(
    name: String,
    fullname: String,
    organization: String,
    title: String,
    description: String
  )
}
