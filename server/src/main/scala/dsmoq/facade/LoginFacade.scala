package dsmoq.facade

import scala.util.{Try, Failure, Success}
import scalikejdbc._, SQLInterpolation._
import java.security.MessageDigest
import dsmoq.persistence
import dsmoq.facade.data._

object LoginFacade {

  def getAuthenticatedUser(params: LoginData.SigninParams): Try[Option[User]] = {
    // TODO dbアクセス時エラーでFailure返す try~catch
    try {
      // TODO パスワードソルトを追加
      val hash = MessageDigest.getInstance("SHA-256").digest(params.password.getBytes("UTF-8")).map("%02x".format(_)).mkString

      DB readOnly { implicit s =>
        val u = persistence.User.u
        val ma = persistence.MailAddress.ma
        val p = persistence.Password.p

        val user = withSQL {
          select(u.result.*)
            .from(persistence.User as u)
            .innerJoin(persistence.MailAddress as ma).on(u.id, ma.userId)
            .innerJoin(persistence.Password as p).on(u.id, p.userId)
            .where
              .append(sqls"(")
                .eq(u.name, params.id)
                .or
                .eq(ma.address, params.id)
              .append(sqls")")
              .and
              .eq(p.hash, hash)
        }
        .map(persistence.User(u.resultName)).single.apply
        .map(x => User(x))

        Success(user)
      }
    } catch {
      case e: RuntimeException => Failure(e)
    }
  }

}