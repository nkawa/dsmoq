package dsmoq.exceptions

case class NotAuthorizedException(message: String = "") extends RuntimeException(message)
