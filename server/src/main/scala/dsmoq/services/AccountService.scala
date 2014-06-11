package dsmoq.services

import scala.util.{Try, Failure, Success}
import scalikejdbc._, SQLInterpolation._
import java.security.MessageDigest
import dsmoq.{AppConf, persistence}
import dsmoq.persistence.PostgresqlHelper._
import dsmoq.services.data._
import dsmoq.exceptions._
import org.joda.time.DateTime
import dsmoq.services.data.ProfileData.UpdateProfileParams
import dsmoq.controllers.SessionTrait
import java.nio.file.Paths
import java.util.UUID
import java.awt.image.BufferedImage
import org.scalatra.servlet.FileItem
import dsmoq.logic.{StringUtil, ImageSaveLogic}
import dsmoq.persistence.{SuggestType, GroupType, PresetType}
import scala.util.Failure
import scala.Some
import dsmoq.services.data.ProfileData.UpdateProfileParams
import scala.util.Success
import scala.collection.mutable
import dsmoq.services.data.MailValidationResult

object AccountService extends SessionTrait {

  def getAuthenticatedUser(params: LoginData.SigninParams): Try[User] = {
    // TODO dbアクセス時エラーでFailure返す try~catch
    try {
      val errors = mutable.LinkedHashMap.empty[String, String]
      val id = params.id match {
        case Some(x) => x
        case None =>
          errors.put("id", "ID is empty")
          ""
      }
      val password = params.password match {
        case Some(x) => x
        case None =>
          errors.put("password", "password is empty")
          ""
      }
      if (errors.size != 0) {
        throw new InputValidationException(errors)
      }

      // TODO パスワードソルトを追加
      val hash = MessageDigest.getInstance("SHA-256").digest(password.getBytes("UTF-8")).map("%02x".format(_)).mkString

      DB readOnly { implicit s =>
        val u = persistence.User.u
        val ma = persistence.MailAddress.ma
        val p = persistence.Password.p

        val user = withSQL {
          select(u.result.*, ma.result.address)
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
        .map(rs => (persistence.User(u.resultName)(rs), rs.string(ma.resultName.address))).single().apply
        .map(x => User(x._1, x._2))

        user match {
          case Some(x) => Success(x)
          case None => throw new InputValidationException(mutable.LinkedHashMap[String, String]("password" -> "wrong password"))
        }
      }
    } catch {
      case e: RuntimeException => Failure(e)
    }
  }

  def changeUserEmail(user: User, email: Option[String]) = {
    try {
      if (user.isGuest) throw new NotAuthorizedException

      // Eメールアドレスのフォーマットチェックはしていない
      val mail = email match {
        case Some(x) =>
          if (x.trim.length == 0) {
            throw new InputValidationException(mutable.LinkedHashMap[String, String]("email" -> "email is empty"))
          } else {
            x.trim
          }
        case None => throw new InputValidationException(mutable.LinkedHashMap[String, String]("email" -> "email is empty"))
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

        // 新しいユーザー情報を取得
        val newUser = withSQL {
          select(u.result.*, ma.result.address)
            .from(persistence.User as u)
            .innerJoin(persistence.MailAddress as ma).on(u.id, ma.userId)
            .where
            .eq(u.id, sqls.uuid(user.id))
        }.map(rs => (persistence.User(u.resultName)(rs), rs.string(ma.resultName.address))).single().apply
          .map(x => User(x._1, x._2))

        newUser match {
          case Some(x) => Success(x)
          case None => throw new RuntimeException("user data not found.")
        }
      }
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def changeUserPassword(user: User, currentPassword: Option[String], newPassword: Option[String]): Try[String] = {
    try {
      if (user.isGuest) throw new NotAuthorizedException

      // input validation
      val errors = mutable.LinkedHashMap.empty[String, String]
      val c = currentPassword match {
        case Some(x) =>
          if (x.length == 0) {
            errors.put("current_password", "current password is empty.")
          }
          x
        case None =>
          errors.put("current_password", "current password is empty.")
          ""
      }
      val n = newPassword match {
        case Some(x) =>
          if (x.length == 0) {
            errors.put("new_password", "new password is empty")
          }
          x
        case None =>
          errors.put("new_password", "new password is empty")
          ""
      }
      if (errors.size != 0) {
        throw new InputValidationException(errors)
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
          case None => throw new InputValidationException(mutable.LinkedHashMap[String, String]("current_password" -> "wrong password"))
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

      DB localTx { implicit s =>
        // input validation
        val errors = mutable.LinkedHashMap.empty[String, String]

        val name = StringUtil.trimAllSpaces(params.name.getOrElse(""))
        if (name.isEmpty) {
          errors.put("name", "name is empty")
        }
        // 同名チェック
        val u = persistence.User.syntax("u")
        val users = withSQL {
          select(u.result.id)
            .from(persistence.User as u)
            .where
            .lowerEq(u.name, name)
            .and
            .ne(u.id, sqls.uuid(user.id))
        }.map(_.string(u.resultName.id)).list().apply
        if (users.size != 0) {
          errors.put("name", "same name")
        }
        val fullname = StringUtil.trimAllSpaces(params.fullname.getOrElse(""))
        if (fullname.isEmpty) {
          errors.put("fullname", "fullname is empty")
        }
        val organization = StringUtil.trimAllSpaces(params.organization.getOrElse(""))
        val title = StringUtil.trimAllSpaces(params.title.getOrElse(""))
        val description = params.description.getOrElse("")

        if (errors.size != 0) {
          throw new InputValidationException(errors)
        }

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
                filePath = "/" + ImageSaveLogic.uploadPath + "/" + imageId,
                presetType = PresetType.Default,
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
        val ma = persistence.MailAddress.ma
        val newUser = withSQL {
          select(u.result.*, ma.result.address)
            .from(persistence.User as u)
            .innerJoin(persistence.MailAddress as ma).on(u.id, ma.userId)
            .where
            .eq(u.id, sqls.uuid(user.id))
        }.map(rs => (persistence.User(u.resultName)(rs), rs.string(ma.resultName.address))).single().apply
        .map(x => User(x._1, x._2))

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
        x.trim match {
          case pattern() => x.trim
          case _ => throw new ValidationException
        }
      case None => throw new InputValidationException(mutable.LinkedHashMap[String, String]("email" -> "email is empty"))
    }

    val result = DB readOnly { implicit s =>
      val ma = persistence.MailAddress.syntax("ma")
      withSQL {
        select(ma.result.id)
          .from(persistence.MailAddress as ma)
          .where
          .eq(ma.address, email)
      }.map(rs => rs.string(ma.resultName.id)).single().apply match {
        case Some(x) => MailValidationResult(isValid = false)
        case None => MailValidationResult(isValid = true)
      }
    }
    Success(result)
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
    DB readOnly { implicit s =>
      val u = persistence.User.u
      val ma = persistence.MailAddress.ma
      withSQL {
        select(u.result.*, ma.result.address)
          .from(persistence.User as u)
          .innerJoin(persistence.MailAddress as ma).on(u.id, ma.userId)
          .where
          .isNull(u.deletedAt)
          .and
          .isNull(ma.deletedAt)
          .orderBy(u.name)
      }.map(rs => (persistence.User(u.resultName)(rs), rs.string(ma.resultName.address))).list().apply
      .map(x => User(x._1, x._2))
    }
  }

  def getUsersAndGroups(param: Option[String]) = {
    val query = param match {
      case Some(x) => x + "%"
      case None => ""
    }

    DB readOnly { implicit s =>
      val u = persistence.User.u
      val g = persistence.Group.g
      val gi = persistence.GroupImage.gi

      withSQL {
        select(u.id, u.name, u.imageId, u.fullname, u.organization, sqls"'1' as type")
          .from(persistence.User as u)
          .where
          .like(u.name, query)
          .or
          .like(u.fullname, query)
          .and
          .isNull(u.deletedAt)
          .union(
            select(g.id, g.name,gi.imageId, sqls"null, null, '2' as type")
              .from(persistence.Group as g)
              .innerJoin(persistence.GroupImage as gi)
                .on(sqls.eq(g.id, gi.groupId).and.eq(gi.isPrimary, true).and.isNull(gi.deletedAt))
              .where
              .like(g.name, query)
              .and
              .eq(g.groupType, GroupType.Public)
              .and
              .isNull(g.deletedAt)
          )
          .orderBy(sqls"name")
          .limit(100)
      }.map(rs => (rs.string("id"),
        rs.string("name"),
        rs.string("image_id"),
        rs.string("fullname"),
        rs.string("organization"),
        rs.int("type"))).list().apply
      .map {x =>
        if(x._6 == SuggestType.User) {
          SuggestData.User(
            dataType = SuggestType.User,
            id = x._1,
            name = x._2,
            fullname = x._4,
            organization = x._5,
            image = AppConf.imageDownloadRoot + x._3
          )
        } else if (x._6 == SuggestType.Group){
          SuggestData.Group(
            dataType = SuggestType.Group,
            id = x._1,
            name = x._2,
            image = AppConf.imageDownloadRoot + x._3
          )
        }
      }
    }
  }

  def getAttributes(param: Option[String]) = {
    val query = param match {
      case Some(x) => x + "%"
      case None => ""
    }

    val a = persistence.Annotation.a
    DB readOnly { implicit s =>
      val attributes = withSQL {
        select(a.result.*)
          .from(persistence.Annotation as a)
          .where
          .like(a.name, query)
          .and
          .isNull(a.deletedAt)
          .orderBy(a.name)
          .limit(100)
      }.map(rs => rs.string(a.resultName.name)).list().apply
      attributes
    }
  }

  def getGroups(param: Option[String]) = {
    val query = param match {
      case Some(x) => x + "%"
      case None => ""
    }

    val g = persistence.Group.g
    val gi = persistence.GroupImage.gi
    DB readOnly { implicit s =>
      withSQL {
        select(g.result.*, gi.result.*)
          .from(persistence.Group as g)
          .innerJoin(persistence.GroupImage as gi)
            .on(sqls.eq(g.id, gi.groupId).and.eq(gi.isPrimary, true).and.isNull(gi.deletedAt))
          .where
          .like(g.name, query)
          .and
          .eq(g.groupType, GroupType.Public)
          .and
          .isNull(g.deletedAt)
          .orderBy(g.name, g.createdAt).desc
          .limit(100)
      }.map(rs => (persistence.Group(g.resultName)(rs), persistence.GroupImage(gi.resultName)(rs))).list().apply
      .map{ x =>
        SuggestData.GroupWithoutType(
          id = x._1.id,
          name = x._1.name,
          image = AppConf.imageDownloadRoot + x._2.imageId
        )
      }
    }
  }

  private def createPasswordHash(password: String) = {
    MessageDigest.getInstance("SHA-256").digest(password.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }
}