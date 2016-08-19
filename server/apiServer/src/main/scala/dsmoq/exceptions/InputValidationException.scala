package dsmoq.exceptions

class InputValidationException(errors: Iterable[(String, String)]) extends RuntimeException {
  val validationErrors = errors

  def getErrorMessage(): Iterable[InputValidationError] = {
    validationErrors.map {
      case (name, message) => {
        InputValidationError(
          name = name,
          message = message
        )
      }
    }
  }
}

case class InputValidationError(
  name: String,
  message: String
)
