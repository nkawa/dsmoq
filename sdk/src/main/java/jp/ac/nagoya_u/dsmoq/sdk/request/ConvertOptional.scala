package jp.ac.nagoya_u.dsmoq.sdk.request

import java.util.Optional
import scala.language.implicitConversions

trait ConvertOptional[T] {
  def toOption: Option[T]
}

object ConvertOptional {
  implicit def optionalToOption[A](target: Optional[A]): ConvertOptional[A] = {
    new ConvertOptional[A] {
      override def toOption: Option[A] = {
        if (target.isPresent) {
          Some(target.get())
        } else {
          None
        }
      }
    }
  }
}
