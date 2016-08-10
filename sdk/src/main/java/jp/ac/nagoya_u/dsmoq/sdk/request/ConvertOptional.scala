package jp.ac.nagoya_u.dsmoq.sdk.request

import java.util.Optional

object ConvertOptional {
  implicit def optionalToOption[A](target: Optional[A]) = new {
    def toOption = if (target.isPresent) {
      Some(target.get())
    } else {
      None
    }
  }
}
