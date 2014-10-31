package dsmoq.services

import scala.util.{Try, Failure, Success}
import scalikejdbc._, SQLInterpolation._
import java.security.MessageDigest
import dsmoq.{AppConf, persistence}
import dsmoq.persistence.PostgresqlHelper._
import dsmoq.services.json._
import dsmoq.exceptions._
import org.joda.time.DateTime
import java.util.UUID
import org.scalatra.servlet.FileItem
import dsmoq.logic.{StringUtil, ImageSaveLogic}
import dsmoq.persistence.{SuggestType, GroupType, PresetType}
import scala.util.Failure
import scala.util.Success
import scala.collection.mutable
import dsmoq.services.json.MailValidationResult

object AccountService {
  /**
   * IDとパスワードを指定してユーザを検索します。
   * @param id アカウント名 or メールアドレス
   * @param password パスワード
   * @return
   */
  def findUserByIdAndPassword(id: String, password: String): Try[User] = {
    try {
      val errors = mutable.LinkedHashMap.empty[String, String]

      if (id.isEmpty()) {
        errors.put("id", "ID is empty")
      }
      if (password.isEmpty()) {
        errors.put("id", "password is empty")
      }
      if (errors.size != 0) {
        throw new InputValidationException(errors)
      }

      DB readOnly { implicit s =>
        val u = persistence.User.u
        val ma = persistence.MailAddress.ma
        val p = persistence.Password.p

        val user = withSQL {
          select(u.result.*, ma.result.address)
            .from(persistence.User as u)
            .innerJoin(persistence.Password as p).on(u.id, p.userId)
            .innerJoin(persistence.MailAddress as ma).on(u.id, ma.userId)
            .where
              .withRoundBracket { sql =>
                sql.eq(u.name, id).or.eq(ma.address, id)
              }
              .and
              .eq(p.hash, createPasswordHash(password))
        }
        .map(rs => (persistence.User(u.resultName)(rs), rs.string(ma.resultName.address))).single().apply
        .map(x => User(x._1, x._2))

        user match {
          case Some(x) => Success(x)
          case None => throw new InputValidationException(Map("password" -> "wrong password"))
        }
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  /**
   * 指定したユーザのメールアドレスを更新します。
   * @param id ユーザID
   * @param email
   * @return
   */
  def changeUserEmail(id: String, email: Option[String]) = {
    try {
      // Eメールアドレスのフォーマットチェックはしていない
      val email_ = email.getOrElse("").trim

      DB localTx {implicit s =>
        // validation
        if (email_.isEmpty) {
          throw new InputValidationException(Map("email" -> "email is empty"))
        } else if (existsSameEmail(id, email_)) {
          throw new InputValidationException(Map("email" -> "email is alread exists"))
        }

        (for {
          user <- persistence.User.find(id)
          address <- persistence.MailAddress.findByUserId(id)
        } yield {
          // TODO 本当はメールアドレス変更確認フローを行わなければならない
          persistence.MailAddress(
            id = address.id,
            userId = address.userId,
            address = email_,
            status = address.status,
            createdBy = address.createdBy,
            createdAt = address.createdAt,
            updatedBy = user.id,
            updatedAt = DateTime.now
          ).save
          User(user, email_)
        }) match {
          case Some(x) => Success(x)
          case None => throw new NotFoundException()
        }
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  private def existsSameEmail(id: String, email: String)(implicit s: DBSession): Boolean = {
    val ma = persistence.MailAddress.ma
    withSQL {
      select(sqls"1")
        .from(persistence.MailAddress as ma)
        .where
          .eqUuid(ma.userId, id).and.eq(ma.address, email)
    }.map(_ => Unit).single().apply.isEmpty
  }

  /**
   * 指定したユーザのパスワードを変更します。
   * @param id ユーザID
   * @param currentPassword 現在のパスワード
   * @param newPassword 新しいパスワード
   * @return
   */
  def changeUserPassword(id: String, currentPassword: Option[String], newPassword: Option[String]): Try[Unit] = {
    // TODO リファクタリング
    try {
      DB localTx { implicit s =>
        // input validation
        val errors = mutable.LinkedHashMap.empty[String, String]
        val c = currentPassword.getOrElse("")
        val oldPasswordHash = createPasswordHash(c)

        val u = persistence.User.u
        val p = persistence.Password.p
        val pwd = withSQL {
          select(p.result.*)
            .from(persistence.Password as p)
            .innerJoin(persistence.User as u).on(u.id, p.userId)
            .where
            .eq(u.id, sqls.uuid(id))
            .and
            .eq(p.hash, oldPasswordHash)
            .and
            .isNull(p.deletedAt)
        }.map(persistence.Password(p.resultName)).single().apply
        if (pwd.isEmpty) {
          errors.put("current_password", "wrong password")
        }

        val n = newPassword.getOrElse("")
        if (n.isEmpty) {
          errors.put("new_password", "new password is empty")
        }
        if (errors.size != 0) {
          throw new InputValidationException(errors)
        }

        val newPasswordHash = createPasswordHash(n)
        withSQL {
          val p = persistence.Password.column
          update(persistence.Password)
            .set(p.hash -> newPasswordHash, p.updatedAt -> DateTime.now, p.updatedBy -> sqls.uuid(id))
            .where
            .eq(p.id, sqls.uuid(pwd.get.id))
        }.update().apply
      }
      Success(Unit)
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  /**
   * 指定したユーザの基本情報を更新します。
   * @param id ユーザID
   * @param name
   * @param fullname
   * @param organization
   * @param title
   * @param description
   * @return
   */
  def updateUserProfile(id: String,
                        name: Option[String],
                        fullname: Option[String],
                        organization: Option[String],
                        title: Option[String],
                        description: Option[String]): Try[User]  = {
    // TODO リファクタリング
    try {
      DB localTx { implicit s =>
        val name_ = StringUtil.trimAllSpaces(name.getOrElse(""))
        val fullname_ = StringUtil.trimAllSpaces(fullname.getOrElse(""))
        val organization_ = StringUtil.trimAllSpaces(organization.getOrElse(""))
        val title_ = StringUtil.trimAllSpaces(title.getOrElse(""))
        val description_ = description.getOrElse("")

        // input validation
        val errors = mutable.LinkedHashMap.empty[String, String]

        if (name_.isEmpty) {
          errors.put("name", "name is empty")
        } else if (existsSameName(id, name_)) {
          errors.put("name", "name is already exists")
        }

        if (fullname_.isEmpty) {
          errors.put("fullname", "fullname is empty")
        }

        if (errors.nonEmpty) {
          throw new InputValidationException(errors)
        }

        persistence.User.find(id) match {
          case Some(x) =>
//            val img = image.map {x =>
//              val imageId = UUID.randomUUID().toString
//              val path = ImageSaveLogic.writeImageFile(imageId, x)
//              val bufferedImage = javax.imageio.ImageIO.read(x.getInputStream)
//
//              persistence.Image.create(
//                id = imageId,
//                name = x.getName,
//                width = bufferedImage.getWidth,
//                height = bufferedImage.getWidth,
//                filePath = path,
//                presetType = PresetType.Default,
//                createdBy = id,
//                createdAt = DateTime.now,
//                updatedBy = id,
//                updatedAt = DateTime.now
//              )
//            }

            val user = persistence.User(
              id = x.id,
              name = name_,
              fullname = fullname_,
              organization = organization_,
              title = title_,
              description = description_,
              imageId = x.imageId,
              createdBy = x.createdBy,
              createdAt = x.createdAt,
              updatedBy = id,
              updatedAt = DateTime.now
            )
            user.save

            val address = persistence.MailAddress.findByUserId(user.id) match {
              case Some(x) => x.address
              case None => ""
            }

            Success(User.apply(user, address))
          case None =>
            throw new NotFoundException()
        }
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  private def existsSameName(id: String, name: String)(implicit s: DBSession): Boolean = {
    val u = persistence.User.u
    withSQL {
      select(sqls"1")
        .from(persistence.User as u)
        .where.lowerEq(u.name, name).and.ne(u.id, sqls.uuid(id))
        .limit(1)
    }.map(x => Unit).single().apply.nonEmpty
  }

  private def createPasswordHash(password: String) = {
    // TODO パスワードソルトを追加
    MessageDigest.getInstance("SHA-256").digest(password.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }
}