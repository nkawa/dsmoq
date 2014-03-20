package dsmoq.facade

import scala.util.{Try, Failure, Success}
import scalikejdbc._, SQLInterpolation._
import java.security.MessageDigest
import dsmoq.models._

object LoginFacade {

  def getAuthenticatedUser(params: dsmoq.facade.data.LoginData.SigninParams): Try[Option[dsmoq.facade.data.User]] = {
    // TODO dbアクセス時エラーでFailure返す try~catch
    try {
      // TODO パスワードソルトを追加
      val hash = MessageDigest.getInstance("SHA-256").digest(params.password.getBytes("UTF-8")).map("%02x".format(_)).mkString

      DB readOnly { implicit s =>
        val u = User.syntax("u")
        var a = MailAddress.syntax("a")
        val p = Password.syntax("p")

        val user = withSQL {
          select(u.result.*)
            .from(User as u)
            .innerJoin(MailAddress as a).on(u.id, a.userId)
            .innerJoin(Password as p).on(u.id, p.userId)
            .where
              .append(sqls"(")
                .eq(u.name, params.id)
                .or
                .eq(a.address, params.id)
              .append(sqls")")
              .and
              .eq(p.hash, hash)
        }
        .map(rs => User(u.resultName)(rs)).single.apply
        .map(x =>
          dsmoq.facade.data.User(
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