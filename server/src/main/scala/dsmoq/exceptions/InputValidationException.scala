package dsmoq.exceptions

class InputValidationException(errors: List[InputValidationError]) extends RuntimeException{
  val validationErrors = errors

  def getErrorMessage() = {
    validationErrors
  }
}

case class InputValidationError(
  name: String,
  messasge: String
)