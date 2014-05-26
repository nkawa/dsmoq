package dsmoq.exceptions

class InputValidationException(id: String, message: String) extends RuntimeException{
  val name = id
  val msg = message

  def getErrorMessage() = {
    Map("name" -> name, "msg" -> msg)
  }
}
