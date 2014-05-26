package dsmoq.services

import scala.util.{Try, Failure, Success}
import scalikejdbc._, SQLInterpolation._
import java.security.MessageDigest
import dsmoq.{AppConf, persistence}
import dsmoq.persistence.PostgresqlHelper._
import dsmoq.services.data._
import dsmoq.exceptions.{InputValidationException, ValidationException, NotAuthorizedException}
import org.joda.time.DateTime
import dsmoq.services.data.ProfileData.UpdateProfileParams
import dsmoq.controllers.SessionTrait
import java.nio.file.Paths
import java.util.UUID
import java.awt.image.BufferedImage
import org.scalatra.servlet.FileItem
import dsmoq.logic.ImageSaveLogic

object AccountService extends SessionTrait {

  def getAuthenticatedUser(params: LoginData.SigninParams): Try[User] = {
    // TODO dbアクセス時エラーでFailure返す try~catch
    try {
      val id = params.id match {
        case Some(x) => x
        case None => throw new InputValidationException("id", "ID is empty")
      }
      val password = params.password match {
        case Some(x) => x
        case None => throw new InputValidationException("password", "password is empty")
      }

      // TODO パスワードソルトを追加
      val hash = MessageDigest.getInstance("SHA-256").digest(password.getBytes("UTF-8")).map("%02x".format(_)).mkString

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
                .eq(u.name, id)
                .or
                .eq(ma.address, id)
              .append(sqls")")
              .and
              .eq(p.hash, hash)
        }
        .map(persistence.User(u.resultName)).single.apply
        .map(x => User(x))

        user match {
          case Some(x) => Success(x)
          case None => throw new InputValidationException("password", "wrong password")
        }
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

  def changeUserPassword(user: User, currentPassword: Option[String], newPassword: Option[String]): Try[String] = {
    try {
      if (user.isGuest) throw new NotAuthorizedException

      // FIXME input validation
      val c = currentPassword match {
        case Some(x) => x
        case None => throw new InputValidationException("current_password", "current password is empty.")
      }
      val n = newPassword match {
        case Some(x) => x
        case None => throw new InputValidationException("new_password", "new password is empty")
      }
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
          case None => throw new InputValidationException("current_password", "wrong password")
        }
      }
      Success(n)
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def updateUserProfile(user: User, params: UpdateProfileParams): Try[User]  = {
    try {
      if (user.isGuest) throw new NotAuthorizedException
      // FIXME input validation
      val name = params.name match {
        case Some(x) =>
          if (x.length == 0) {
            throw new InputValidationException("name", "name is empty")
          } else {
            x
          }
        case None => throw new InputValidationException("name", "name is empty")
      }
      val fullname = params.fullname match {
        case Some(x) => x
        case None => throw new InputValidationException("fullname", "fullname is empty")
      }
      val organization = params.organization match {
        case Some(x) => x
        case None => throw new InputValidationException("organization", "organization is empty")
      }
      val title = params.title match {
        case Some(x) => x
        case None => throw new InputValidationException("title", "title is empty")
      }
      val description = params.description match {
        case Some(x) => x
        case None => throw new InputValidationException("description", "description is empty")
      }

      DB localTx { implicit s =>
        withSQL {
          val u = persistence.User.column
          update(persistence.User)
            .set(u.name -> name, u.fullname -> fullname, u.organization -> organization,
              u.title -> title, u.description -> description,
              u.updatedAt -> DateTime.now, u.updatedBy -> sqls.uuid(user.id))
            .where
            .eq(u.id, sqls.uuid(user.id))
        }.update().apply

        // imageがある場合、画像保存処理
        params.image match {
          case Some(x) =>
            if (x.size > 0) {
              // save image file
              val imageId = UUID.randomUUID().toString
              ImageSaveLogic.writeImageFile(imageId, x)

              val bufferedImage = javax.imageio.ImageIO.read(x.getInputStream)
              persistence.Image.create(
                id = imageId,
                name = x.getName,
                width = bufferedImage.getWidth,
                height = bufferedImage.getWidth,
                filePath = "/" + imageId,
                createdBy = user.id,
                createdAt = DateTime.now,
                updatedBy = user.id,
                updatedAt = DateTime.now
              )

              withSQL {
                val u = persistence.User.column
                update(persistence.User)
                  .set(u.imageId -> sqls.uuid(imageId))
                  .where
                  .eq(u.id, sqls.uuid(user.id))
              }.update().apply
            }
          case None => // do nothing
        }

        // 新しいユーザー情報を取得
        val u = persistence.User.u
        val newUser = withSQL {
          select(u.result.*)
            .from(persistence.User as u)
            .where
            .eq(u.id, sqls.uuid(user.id))
        }.map(persistence.User(u.resultName)).single().apply
        .map(x => User(x))

        newUser match {
          case Some(x) => Success(x)
          case None => throw new RuntimeException("user data not found.")
        }
      }
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def isValidEmail(user: User, value: Option[String]) = {
    val email = value match {
      case Some(x) =>
        // メールアドレスバリデーションはHTHML5準拠(RFC5322には違反)
        val pattern = "\\A[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*\\z".r
        x match {
          case pattern() => x
          case _ => throw new ValidationException
        }
      case None => throw new RuntimeException("email is empty")
    }

    DB readOnly { implicit s =>
      val ma = persistence.MailAddress.syntax("ma")
      withSQL {
        select(ma.result.id)
          .from(persistence.MailAddress as ma)
          .where
          .eq(ma.address, email)
      }.map(rs => rs.string(ma.resultName.id)).single().apply match {
        case Some(x) => throw new RuntimeException("email already exists")
        case None => // do nothing
      }
    }
    Success(email)
  }

  def getLicenses()  = {
    val licenses = DB readOnly { implicit s =>
      persistence.License.findOrderedAll()
    }
    licenses.map(x =>
      dsmoq.services.data.License(
        id = x.id,
        name = x.name
    ))
  }

  def getAccounts() = {
    val accounts = DB readOnly { implicit s =>
      persistence.User.findAllOrderByName()
    }
    accounts.map(x =>
      dsmoq.services.data.ProfileData.Account(
        id = x.id,
        name = x.name,
        image = if (x.imageId.length > 0) {
          AppConf.imageDownloadRoot + x.imageId
        } else {
          ""
        }
      )
    )
  }

  private def createPasswordHash(password: String) = {
    MessageDigest.getInstance("SHA-256").digest(password.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }

  private def saveThumbnailImage(thumbImageId: String, f: FileItem) = {
    // FIXME 画像のアス比は計算せず固定(100x100)にしている
    val picSize = 100
    val bufferedImage = javax.imageio.ImageIO.read(f.getInputStream)
    val thumbBufferedImage = new BufferedImage(picSize, picSize, bufferedImage.getType)
    thumbBufferedImage.getGraphics.drawImage(bufferedImage.getScaledInstance(picSize, picSize,
      java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, 100, 100, null)

    // save thumbnail file
    val fileType = f.name.split('.').last
    javax.imageio.ImageIO.write(thumbBufferedImage, fileType, (Paths.get(AppConf.imageDir).resolve(thumbImageId).toFile))
    (picSize, picSize)
  }
}