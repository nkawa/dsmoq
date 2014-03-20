package dsmoq.facade.data

/**
 * Created by terurou on 2014/03/20.
 */
object LoginData {
  // request
  case class SigninParams(id: String, password: String)

  // response
  case class User(
                   id: String,
                   name: String,
                   fullname: String,
                   organization: String,
                   title: String,
                   image: String,
                   isGuest: Boolean
                   )
}
