package dsmoq.exceptions

case class InputCheckException(target: String, message: String, isUrlParam: Boolean) extends IllegalArgumentException(message) {
}
