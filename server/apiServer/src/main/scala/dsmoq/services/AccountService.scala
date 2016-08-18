package dsmoq.services

import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import java.util.ResourceBundle
import java.util.UUID

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.joda.time.DateTime
import org.scalatra.servlet.FileItem
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.ResourceNames
import dsmoq.exceptions.BadRequestException
import dsmoq.exceptions.NotFoundException
import dsmoq.logic.ImageSaveLogic
import dsmoq.logic.StringUtil
import dsmoq.persistence
import dsmoq.persistence.PostgresqlHelper.PgConditionSQLBuilder
import dsmoq.persistence.PostgresqlHelper.PgSQLSyntaxType
import dsmoq.persistence.PresetType
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef
import scalikejdbc.scalikejdbcSQLSyntaxToStringImplicitDef
import scalikejdbc.select
import scalikejdbc.sqls
import scalikejdbc.update
import scalikejdbc.withSQL

class AccountService(resource: ResourceBundle) extends LazyLogging {

  val LOG_MARKER = MarkerFactory.getMarker("AUTH_LOG")

  /**
   * IDとパスワードを指定してユーザを検索します。
   *
   * @param id アカウント名 or メールアドレス
   * @param password パスワード
   * @return 取得したユーザオブジェクト。エラーが発生した場合、例外をFailureで包んで返す。
   *         発生する可能性のある例外は、BadRequestExceptionである。
   */
  def findUserByIdAndPassword(id: String, password: String): Try[User] = {
    logger.info(LOG_MARKER, "Login request... : [id] = {}", id)

    try {
      DB readOnly { implicit s =>
        findUser(id, password) match {
          case Some(x) => {
            logger.info(LOG_MARKER, "Login successed: [id] = {}", id)
            Success(x)
          }
          case None => {
            throw new BadRequestException(resource.getString(ResourceNames.INVALID_PASSWORD))
          }
        }
      }
    } catch {
      case e: BadRequestException =>
        logger.error(LOG_MARKER,
          "Login failed: input validation error occurred, [id] = {}, [error messages] = {}",
          id,
          e.getMessage(),
          e
        )
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
   * @param email EMailアドレス
   * @return 更新後のユーザオブジェクト。エラーが発生した場合は例外をFailureで包んで返す。
   *         発生する可能性のある例外は、BadRequestExceptionである。
   */
  def changeUserEmail(id: String, email: String): Try[User] = {
    try {
      // Eメールアドレスのフォーマットチェックはしていない
      val trimmedEmail = email.trim

      DB localTx { implicit s =>
        if (isGoogleUser(id)) {
          throw new BadRequestException(resource.getString(ResourceNames.CANT_CHANGE_GOOGLE_USER_EMAIL))
        }

        if (existsSameEmail(id, trimmedEmail)) {
          val message = resource.getString(ResourceNames.ALREADY_REGISTERED_EMAIL).format(trimmedEmail)
          throw new BadRequestException(message)
        }

        (for {
          user <- persistence.User.find(id)
          address <- persistence.MailAddress.findByUserId(id)
        } yield {
          // TODO 本当はメールアドレス変更確認フローを行わなければならない
          persistence.MailAddress(
            id = address.id,
            userId = address.userId,
            address = trimmedEmail,
            status = address.status,
            createdBy = address.createdBy,
            createdAt = address.createdAt,
            updatedBy = user.id,
            updatedAt = DateTime.now
          ).save
          User(user, trimmedEmail)
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
      select.apply(sqls"1")
        .from(persistence.MailAddress as ma)
        .where
        .eq(ma.address, email)
        .and
        .not.withRoundBracket { sql =>
          sql.eqUuid(ma.userId, id).and.eq(ma.address, email)
        }
    }.map(_ => Unit).single().apply.nonEmpty
  }

  /**
   * 指定したユーザのパスワードを変更します。
   *
   * @param id ユーザID
   * @param currentPassword 現在のパスワード
   * @param newPassword 新しいパスワード
   * @return エラーがあった場合は、例外をFailureで包んで返す。
   *         発生する可能性のある例外は、BadRequestExceptionである。
   */
  def changeUserPassword(id: String, currentPassword: String, newPassword: String): Try[Unit] = {
    try {
      DB localTx { implicit s =>
        if (isGoogleUser(id)) {
          throw new BadRequestException(resource.getString(ResourceNames.CANT_CHANGE_GOOGLE_USER_PASSWORD))
        }

        val currentPasswordObject = getCurrentPassword(id, currentPassword) match {
          case None => throw new BadRequestException(resource.getString(ResourceNames.INVALID_PASSWORD))
          case Some(p) => p
        }

        updatePassword(id, newPassword, currentPasswordObject)
      }
      Success(Unit)
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  private def getCurrentPassword(
    id: String,
    currentPassword: String)(implicit s: DBSession): Option[persistence.Password] = {
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

  private def updatePassword(
    id: String,
    newPassword: String,
    currentPassword: persistence.Password)(implicit s: DBSession): Int = {
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
   * @param name 名前
   * @param fullname フルネーム
   * @param organization 組織名
   * @param title タイトル
   * @param description 説明
   * @return 更新後のユーザオブジェクト。エラーが発生した場合、例外をFailureで包んで返す。
   *         発生する可能性のある例外は、NotFoundException、BadRequestExceptionである。
   */
  def updateUserProfile(id: String,
    name: Option[String],
    fullname: Option[String],
    organization: Option[String],
    title: Option[String],
    description: Option[String]): Try[User] = {
    try {
      DB localTx { implicit s =>
        val trimmedName = StringUtil.trimAllSpaces(name.getOrElse(""))
        val trimmedFullname = StringUtil.trimAllSpaces(fullname.getOrElse(""))
        val trimmedOrganization = StringUtil.trimAllSpaces(organization.getOrElse(""))
        val trimmedTitle = StringUtil.trimAllSpaces(title.getOrElse(""))
        val checkedDescription = description.getOrElse("")

        if (isGoogleUser(id)) {
          // Googleアカウントユーザーはアカウント名の変更禁止(importスクリプトでusersテーブルのname列を使用しているため)
          persistence.User.find(id) match {
            case None => {
              throw new NotFoundException
            }
            case Some(x) if x.name != trimmedName => {
              throw new BadRequestException(resource.getString(ResourceNames.CANT_CHANGE_GOOGLE_USER_NAME))
            }
            case Some(_) => {
              // do nothing
            }
          }
        }

        if (existsSameName(id, trimmedName)) {
          val message = resource.getString(ResourceNames.ALREADY_REGISTERED_NAME).format(trimmedName)
          throw new BadRequestException(message)
        }

        persistence.User.find(id) match {
          case Some(x) =>
            val user = persistence.User(
              id = x.id,
              name = trimmedName,
              fullname = trimmedFullname,
              organization = trimmedOrganization,
              title = trimmedTitle,
              description = checkedDescription,
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
   * @param id ユーザID
   * @param icon アイコン画像
   * @return アイコンの画像ID。エラーが発生した場合、例外をFailureで包んで返す。
   *         発生する可能性のある例外は、NotFoundExceptionである。
   */
  def changeIcon(id: String, icon: FileItem): Try[String] = {
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

  /**
   * APIキーに紐づくユーザを取得する。
   * パラメータは事前にチェック済みであるものとする。
   *
   * @param apiKey APIキー
   * @param signature シグネチャ
   * @return ユーザが見つかった場合、ユーザオブジェクト
   */
  def getUserByKeys(apiKey: String, signature: String): Option[User] = {
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
      }.map { rs =>
        (
          persistence.User(u.resultName)(rs),
          rs.string(ak.resultName.apiKey),
          rs.string(ak.resultName.secretKey),
          rs.string(ma.resultName.address)
        )
      }.single().apply

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
        case _ => None
      }
    }
  }

  private def getSignature(apiKey: String, secretKey: String): String = {
    val sk = new SecretKeySpec(secretKey.getBytes(), "HmacSHA1");
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(sk)
    val result = mac.doFinal((apiKey + "&" + secretKey).getBytes())
    URLEncoder.encode(Base64.getEncoder.encodeToString(result), "UTF-8")
  }

  private def existsSameName(id: String, name: String)(implicit session: DBSession): Boolean = {
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

  /**
   * ユーザのプロファイルを取得する。
   *
   * @param ユーザオブジェクト
   * @return ユーザのプロファイル
   */
  def getUserProfile(user: User): Try[ProfileData] = {
    DB.readOnly { implicit s =>
      Success(ProfileData(user, isGoogleUser(user.id)))
    }
  }

  private def isGoogleUser(id: String)(implicit session: DBSession): Boolean = {
    persistence.GoogleUser.findByUserId(id) match {
      case Some(x) => true
      case None => false
    }
  }
}
