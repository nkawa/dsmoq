package dsmoq.facade

import scala.util.{Failure, Success}
import scalikejdbc._, SQLInterpolation._
import java.security.MessageDigest

object LoginFacade {
  def getAuthenticatedUser(params: SigninParams) = {
    // TODO dbアクセス時エラーでFailure返す try~catch
    val user = try {
        DB readOnly { implicit session =>
          sql"""
            SELECT
              users.*, passwords.hash
            FROM
              users
            INNER JOIN
              passwords ON users.password_id = passwords.id
            INNER JOIN
              mail_addresses ON users.id = mail_addresses.user_id
            WHERE
              users.name = ${params.id}
            OR
              mail_addresses.address = ${params.id}
            LIMIT 1
          """
            .map(_.toMap).single.apply()
        }
      }
    // FIXME パスワードのソルトは適当('#' + password + "coi")
    val saltPassword = "#" + params.password + "coi"
    val hash = MessageDigest.getInstance("SHA-256").digest(saltPassword.getBytes("UTF-8")).map("%02x".format(_)).mkString

    user match {
      case Some(x) =>
        if (hash == x("hash")) {
          Success(User(
            x("id").toString,
            x("name").toString,
            x("fullname").toString,
            x("organization").toString,
            x("title").toString,
            "http://xxxx",
            false))
        } else {
          Failure(new Exception("User Not Found"))
        }
      case None => Failure(new Exception("User Not Found"))
    }
  }
}

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