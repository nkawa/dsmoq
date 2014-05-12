package dsmoq.services

import scala.util.{Try, Failure, Success}
import scalikejdbc._, SQLInterpolation._
import java.security.MessageDigest
import dsmoq.persistence
import dsmoq.persistence.PostgresqlHelper._
import dsmoq.services.data._
import dsmoq.exceptions.NotAuthorizedException
import org.joda.time.DateTime

object AccountService {

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

  def changeUserEmail(user: User, email: Option[String]): Try[String] = {
    try {
      if (user.isGuest) throw new NotAuthorizedException

      // FIXME Eメールアドレスのフォーマットチェックはしていない
      val mail = email match {
        case Some(x) => x
        case None => throw new RuntimeException("email is empty")
      }

      DB localTx {implicit s =>
        val u = persistence.User.u
        val ma = persistence.MailAddress.ma
        val userAddress = withSQL {
          select(ma.result.*)
            .from(persistence.MailAddress as ma)
            .innerJoin(persistence.User as u).on(u.id, ma.userId)
            .where
            .eq(u.id, sqls.uuid(user.id))
            .and
            .isNull(ma.deletedAt)
        }.map(persistence.MailAddress(ma.resultName)).single.apply

        // FIXME メールアドレス変更確認メールを送り、変更を待つようにはしていない(≒データを直接変更している)
        userAddress match {
          case Some(x) =>
            withSQL {
              val ma = persistence.MailAddress.column
              update(persistence.MailAddress)
                .set(ma.address -> mail, ma.updatedAt -> DateTime.now, ma.updatedBy -> sqls.uuid(user.id))
                .where
                .eq(ma.id, sqls.uuid(x.id))
            }.update.apply
          case None => throw new RuntimeException("user mail address is not found.")
        }
      }
      Success(mail)
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def changeUserPassword(user: User, currentPassword: Option[String], newPassword: Option[String]): Try[Option[String]] = {
    try {
      if (user.isGuest) throw new NotAuthorizedException

      // 値があるかチェック
      val result = for {
        c <- currentPassword
        n <- newPassword
      } yield {
        val oldPasswordHash = createPasswordHash(c)
        DB localTx { implicit s =>
          val u = persistence.User.u
          val p = persistence.Password.p
          val pwd = withSQL {
            select(p.result.*)
              .from(persistence.Password as p)
              .innerJoin(persistence.User as u).on(u.id, p.userId)
              .where
              .eq(u.id, sqls.uuid(user.id))
              .and
              .eq(p.hash, oldPasswordHash)
              .and
              .isNull(p.deletedAt)
          }.map(persistence.Password(p.resultName)).single().apply

          val newPasswordHash = createPasswordHash(n)
          pwd match {
            case Some(x) =>
              withSQL {
                val p = persistence.Password.column
                update(persistence.Password)
                  .set(p.hash -> newPasswordHash, p.updatedAt -> DateTime.now, p.updatedBy -> sqls.uuid(user.id))
                  .where
                  .eq(p.id, sqls.uuid(x.id))
              }.update().apply
            case None => throw new RuntimeException("password data is not found.")
          }
        }
        n
      }
      // 引数不足の場合Noneが返る
      if (result.isEmpty) throw new RuntimeException("parameter error.")
      Success(result)
    } catch {
      case e: Exception => Failure(e)
    }
  }

  private def createPasswordHash(password: String) = {
    MessageDigest.getInstance("SHA-256").digest(password.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }
}