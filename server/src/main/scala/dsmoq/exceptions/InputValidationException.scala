package dsmoq.exceptions

class InputValidationException(id: String, message: String) extends RuntimeException{
  val name = id
  val msg = message

  def getErrorMessage() = {
    List(Map("name" -> name, "message" -> msg))
  }
}
