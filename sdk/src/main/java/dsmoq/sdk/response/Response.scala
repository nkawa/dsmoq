package dsmoq.sdk.response

case class Response[A] (status: String, data: A)
