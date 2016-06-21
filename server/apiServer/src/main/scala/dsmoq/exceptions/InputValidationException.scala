package dsmoq.exceptions

class InputValidationException(errors: Iterable[(String, String)]) extends RuntimeException{
  val validationErrors = errors

  def getErrorMessage() = {
    validationErrors.map(x =>
      InputValidationError(
        name = x._1,
        message = x._2
      ))
  }
}

case class InputValidationError(
  name: String,
  message: String
)