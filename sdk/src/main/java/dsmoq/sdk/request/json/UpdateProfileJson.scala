package dsmoq.sdk.request.json

private[request] case class UpdateProfileJson(
  name: String,
  fullname: String,
  organization: String,
  title: String,
  description: String
) extends Jsonable
