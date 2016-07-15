package dsmoq.exceptions

case class AccessDeniedException(message: String) extends RuntimeException(message)
