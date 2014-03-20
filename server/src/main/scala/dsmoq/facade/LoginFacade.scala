package dsmoq.facade

import scala.util.{Try, Failure, Success}
import scalikejdbc._, SQLInterpolation._
import java.security.MessageDigest

object LoginFacade {

  def getAuthenticatedUser(params: SigninParams): Try[Option[User]] = {
    // TODO dbアクセス時エラーでFailure返す try~catch
    try {
      // TODO パスワードソルトを追加
      val hash = MessageDigest.getInstance("SHA-256").digest(params.password.getBytes("UTF-8")).map("%02x".format(_)).mkString

      DB readOnly { implicit s =>
        val u = dsmoq.models.User.syntax("u")
        var a = dsmoq.models.MailAddress.syntax("a")
        val p = dsmoq.models.Password.syntax("p")

        val user = withSQL {
          select(u.result.*)
            .from(dsmoq.models.User as u)
            .innerJoin(dsmoq.models.MailAddress as a).on(u.id, a.userId)
            .innerJoin(dsmoq.models.Password as p).on(u.id, p.userId)
            .where
              .append(sqls"(")
                .eq(u.name, params.id)
                .or
                .eq(a.address, params.id)
              .append(sqls")")
              .and
              .eq(p.hash, hash)
        }
        .map(rs => dsmoq.models.User(u.resultName)(rs)).single.apply
        .map(x =>
          User(
            id = x.id,
            name = x.name,
            fullname = x.fullname,
            organization = x.organization,
            title = x.title,
            image = "http://xxx",
            isGuest = false
          )
        )
        Success(user)
      }
    } catch {
      case e: RuntimeException => Failure(e)
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