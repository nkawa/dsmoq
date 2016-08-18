package dsmoq.exceptions

class InputCheckException(
  val target: String,
  val message: String,
  val isUrlParam: Boolean) extends IllegalArgumentException(message)
