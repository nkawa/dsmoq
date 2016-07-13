package dsmoq.services

import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.util.Try
import scalikejdbc._
import java.security.MessageDigest
import dsmoq.persistence
import dsmoq.persistence.PostgresqlHelper._
import dsmoq.exceptions._
import org.joda.time.DateTime
import java.util.{Base64, UUID}
import org.scalatra.servlet.FileItem
import dsmoq.logic.{StringUtil, ImageSaveLogic}
import dsmoq.persistence.PresetType
import scala.util.Failure
import scala.util.Success
import scala.collection.mutable

import com.typesafe.scalalogging.LazyLogging
import org.slf4j.MarkerFactory

object AccountService extends LazyLogging {

  val LOG_MARKER = MarkerFactory.getMarker("AUTH_LOG")

  /**
   * IDとパスワードを指定してユーザを検索します。
    *
    * @param id アカウント名 or メールアドレス
   * @param password パスワード
   * @return
   */
  def findUserByIdAndPassword(id: String, password: String): Try[User] = {
    logger.info(LOG_MARKER, "Login request... : [id] = {}", id)

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
        findUser(id, password) match {
          case Some(x) => {
            logger.info(LOG_MARKER, "Login successed: [id] = {}", id)
            Success(x)
          }
          case None => {
            throw new InputValidationException(Map("password" -> "wrong password"))
          }
        }
      }
    } catch {
      case e: InputValidationException =>
        logger.error(LOG_MARKER, "Login failed: input validation error occurred, [id] = {}, [error messages] = {}", id, e.getErrorMessage())
        Failure(e)
      case t: Throwable =>
        logger.error(LOG_MARKER, "Login failed: error occurred. [id] = {}", id, t)
        Failure(t)
    }
  }

  private def findUser(id: String, password: String)(implicit s: DBSession): Option[User] = {
    val u = persistence.User.u
    val ma = persistence.MailAddress.ma
    val p = persistence.Password.p

    withSQL {
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
  }

  /**
   * 指定したユーザのメールアドレスを更新します。
    *
    * @param id ユーザID
   * @param email
   * @return
   */
  def changeUserEmail(id: String, email: String) = {
    try {
      // Eメールアドレスのフォーマットチェックはしていない
      val email_ = email.trim

      DB localTx {implicit s =>
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

  def existsSameEmail(id: String, email: String)(implicit s: DBSession): Boolean = {
    val ma = persistence.MailAddress.ma
    withSQL {
      select.apply(sqls"1")
        .from(persistence.MailAddress as ma)
        .where
          .eq(ma.address, email)
          .and
          .not.withRoundBracket {sql => sql.eqUuid(ma.userId, id).and.eq(ma.address, email) }
    }.map(_ => Unit).single().apply.nonEmpty
  }

  /**
   * 指定したユーザのパスワードを変更します。
    *
    * @param id ユーザID
   * @param currentPassword 現在のパスワード
   * @param newPassword 新しいパスワード
   * @return
   */
  def changeUserPassword(id: String, currentPassword: persistence.Password, newPassword: String): Try[Unit] = {
    // TODO リファクタリング
    try {
      DB localTx { implicit s =>
        updatePassword(id, newPassword, currentPassword)
      }
      Success(Unit)
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  def getCurrentPassword(id: String, currentPassword: String)(implicit s: DBSession): Option[persistence.Password] =
  {
    val oldPasswordHash = createPasswordHash(currentPassword)
    val u = persistence.User.u
    val p = persistence.Password.p

    withSQL {
      select(p.result.*)
        .from(persistence.Password as p)
        .innerJoin(persistence.User as u).on(u.id, p.userId)
        .where
        .eq(u.id, sqls.uuid(id))
        .and
        .eq(p.hash, oldPasswordHash)
        .and
        .isNull(u.deletedAt)
        .and
        .isNull(p.deletedAt)
    }.map(persistence.Password(p.resultName)).single().apply
  }

  private def updatePassword(id: String, newPassword:String, currentPassword: persistence.Password)(implicit s: DBSession): Int =
  {
    val newPasswordHash = createPasswordHash(newPassword)
    withSQL {
      val p = persistence.Password.column
      update(persistence.Password)
        .set(p.hash -> newPasswordHash, p.updatedAt -> DateTime.now, p.updatedBy -> sqls.uuid(id))
        .where
        .eq(p.id, sqls.uuid(currentPassword.id))
    }.update().apply
  }

  /**
   * 指定したユーザの基本情報を更新します。
    *
    * @param id ユーザID
   * @param name
   * @param fullname
   * @param organization
   * @param title
   * @param description
   * @return
   */
  def updateUserProfile(id: String,
                        name: String,
                        fullname: String,
                        organization: Option[String],
                        title: Option[String],
                        description: Option[String]): Try[User]  = {
    // TODO リファクタリング
    try {
      DB localTx { implicit s =>
        val organization_ = StringUtil.trimAllSpaces(organization.getOrElse(""))
        val title_ = StringUtil.trimAllSpaces(title.getOrElse(""))
        val description_ = description.getOrElse("")

        persistence.User.find(id) match {
          case Some(x) =>
            val user = persistence.User(
              id = x.id,
              name = name,
              fullname = fullname,
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

  /**
   * 指定したユーザのアイコンを更新します。
    *
    * @param id
   * @param icon
   */
  def changeIcon(id: String, icon: FileItem) = {
    try {
      DB localTx { implicit s =>
        persistence.User.find(id) match {
          case Some(x) =>
            val imageId = UUID.randomUUID().toString
            val path = ImageSaveLogic.writeImageFile(imageId, icon)
            val bufferedImage = javax.imageio.ImageIO.read(icon.getInputStream)

            val image = persistence.Image.create(
              id = imageId,
              name = icon.getName,
              width = bufferedImage.getWidth,
              height = bufferedImage.getWidth,
              filePath = path,
              presetType = PresetType.Default,
              createdBy = id,
              createdAt = DateTime.now,
              updatedBy = id,
              updatedAt = DateTime.now
            )

            val user = persistence.User(
              id = x.id,
              name = x.name,
              fullname = x.fullname,
              organization = x.organization,
              title = x.title,
              description = x.description,
              imageId = image.id,
              createdBy = x.createdBy,
              createdAt = x.createdAt,
              updatedBy = id,
              updatedAt = DateTime.now
            )
            user.save

            Success(image.id)
          case None =>
            throw new NotFoundException()
        }
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  def getUserByKeys(apiKey: String, signature: String): Option[User] = {
    try
    {
      DB readOnly { implicit s =>
        val u = persistence.User.u
        val ak = persistence.ApiKey.ak
        val ma = persistence.MailAddress.ma
        val targetUser = withSQL {
          select(u.result.*, ak.result.apiKey, ak.result.secretKey, ma.result.address)
            .from(persistence.User as u)
            .innerJoin(persistence.ApiKey as ak).on(u.id, ak.userId)
            .innerJoin(persistence.MailAddress as ma).on(u.id, ma.userId)
            .where
              .eq(ak.apiKey, apiKey)
              .and
              .isNull(u.deletedAt)
              .and
              .isNull(u.deletedBy)
              .and
              .isNull(ak.deletedAt)
              .and
              .isNull(ak.deletedBy)
        }.map(rs => (persistence.User(u.resultName)(rs), rs.string(ak.resultName.apiKey), rs.string(ak.resultName.secretKey), rs.string(ma.resultName.address))).single().apply

        targetUser match {
          case Some(user) if getSignature(user._2, user._3) == signature => {
            Some(User(
              id = user._1.id,
              name = user._1.name,
              fullname = user._1.fullname,
              organization = user._1.organization,
              title = user._1.title,
              image = user._1.imageId,
              mailAddress = user._4,
              description = user._1.description,
              isGuest = false,
              isDeleted = false
            ))
          }
          case None => None
        }
      }
    } catch {
      case e: Throwable => None
    }
  }

  private def getSignature(apiKey: String, secretKey: String): String = {
    val sk = new SecretKeySpec(secretKey.getBytes(), "HmacSHA1");
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(sk)
    val result = mac.doFinal((apiKey + "&" + secretKey).getBytes())
    URLEncoder.encode(Base64.getEncoder.encodeToString(result), "UTF-8")
  }

  def existsSameName(id: String, name: String)(implicit session: DBSession): Boolean = {
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

  def getUserProfile(user: User): Try[ProfileData] = {
    DB.readOnly { implicit s =>
      Success(ProfileData(user, isGoogleUser(user.id)))
    }
  }

  def isGoogleUser(id: String)(implicit session: DBSession): Boolean = {
    persistence.GoogleUser.findByUserId(id) match {
      case Some(x) => true
      case None => false
    }
  }
}
