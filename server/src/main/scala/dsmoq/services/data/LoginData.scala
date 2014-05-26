package dsmoq.services.data

/**
 * Created by terurou on 2014/03/20.
 */
object LoginData {
  // request
  case class SigninParams(id: Option[String], password: Option[String])

}
