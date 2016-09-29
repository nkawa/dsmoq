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

/**
 * ユーザアカウント関連の操作を取り扱うサービスクラス
 *
 * @param resource リソースバンドルのインスタンス
 */
class AccountService(resource: ResourceBundle) extends LazyLogging {

  val LOG_MARKER = MarkerFactory.getMarker("AUTH_LOG")

  /**
   * IDとパスワードを指定してユーザを検索します。
   *
   * @param id アカウント名 or メールアドレス
   * @param password パスワード
   * @return
   *         Success(User) ログイン成功時、ログインユーザ情報
   *         Failure(NullPointerException) 引数がnullの場合
   *         Failure(BadRequestException) ログイン失敗時
   */
  def findUserByIdAndPassword(id: String, password: String): Try[User] = {
    logger.info(LOG_MARKER, "Login request... : [id] = {}", id)
    Try {
      CheckUtil.checkNull(id, "id")
      CheckUtil.checkNull(password, "password")
      DB.readOnly { implicit s =>
        findUser(id, password) match {
          case None => {
            throw new BadRequestException(resource.getString(ResourceNames.INVALID_PASSWORD))
          }
          case Some(u) if u.isDisabled => {
            throw new BadRequestException(resource.getString(ResourceNames.DISABLED_USER))
          }
          case Some(u) if !u.isDisabled => {
            logger.info(LOG_MARKER, "Login successed: [id] = {}", id)
            u
          }
        }
      }
    }.recoverWith {
      case e: BadRequestException =>
        logger.error(
          LOG_MARKER,
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

  /**
   * IDとパスワードを指定してユーザを検索します。
   *
   * @param id アカウント名 or メールアドレス
   * @param password パスワード
   * @param s DBセッション
   * @return 取得結果
   */
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
      .map(rs => (persistence.User(u.resultName)(rs), rs.string(ma.resultName.address))).single.apply()
      .map(x => User(x._1, x._2))
  }

  /**
   * 指定したIDのユーザを取得します。
   *
   * @param id ユーザID
   * @return 取得したユーザ
   */
  def getUser(id: String): Option[User] = {
    DB.readOnly { implicit s =>
      val u = persistence.User.u
      val ma = persistence.MailAddress.ma
      withSQL {
        select(u.result.*, ma.result.address)
          .from(persistence.User as u)
          .innerJoin(persistence.MailAddress as ma).on(u.id, ma.userId)
          .where
          .eq(u.id, sqls.uuid(id))
      }.map { rs =>
        val user = persistence.User(u.resultName)(rs)
        val address = rs.string(ma.resultName.address)
        User(user, address)
      }.single.apply()
    }
  }

  /**
   * 指定したユーザのメールアドレスを更新します。
   *
   * @param id ユーザID
   * @param email EMailアドレス
   * @return
   *         Success(User) 更新成功時、更新後ユーザ情報
   *         Failure(NullPointerException) 引数がnullの場合
   *         Failure(BadRequestException) 更新対象ユーザがGoogleアカウントユーザの場合
   *         Failure(BadRequestException) Emailアドレスが既に登録されている場合
   */
  def changeUserEmail(id: String, email: String): Try[User] = {
    Try {
      CheckUtil.checkNull(id, "id")
      CheckUtil.checkNull(email, "email")
      // Eメールアドレスのフォーマットチェックはしていない
      val trimmedEmail = email.trim

      DB.localTx { implicit s =>
        if (isGoogleUser(id)) {
          throw new BadRequestException(resource.getString(ResourceNames.CANT_CHANGE_GOOGLE_USER_EMAIL))
        }

        if (existsSameEmail(id, trimmedEmail)) {
          val message = resource.getString(ResourceNames.ALREADY_REGISTERED_EMAIL).format(trimmedEmail)
          throw new BadRequestException(message)
        }

        val user = for {
          user <- persistence.User.find(id) if !user.disabled
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
        }
        user.getOrElse {
          throw new NotFoundException()
        }
      }
    }
  }

  /**
   * 同じメールアドレスでの登録があるかを確認する。
   *
   * @param id ログインユーザID
   * @param email E-Mailアドレス
   * @param s DBセッション
   * @return ログインユーザ以外に登録があればtrue、それ以外の場合はfalse
   */
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
    }.map(_ => ()).single.apply().isDefined
  }

  /**
   * 指定したユーザのパスワードを変更します。
   *
   * @param id ユーザID
   * @param currentPassword 現在のパスワード
   * @param newPassword 新しいパスワード
   * @return
   *         Success(Unit) 変更成功時
   *         Failure(NullPointerException) 引数がnullの場合
   *         Failure(BadRequestException) 更新対象ユーザがGoogleアカウントユーザの場合
   *         Failure(BadRequestException) パスワードが一致しなかった場合
   */
  def changeUserPassword(id: String, currentPassword: String, newPassword: String): Try[Unit] = {
    Try {
      CheckUtil.checkNull(id, "id")
      CheckUtil.checkNull(currentPassword, "currentPassword")
      CheckUtil.checkNull(newPassword, "newPassword")
      DB.localTx { implicit s =>
        if (isGoogleUser(id)) {
          throw new BadRequestException(resource.getString(ResourceNames.CANT_CHANGE_GOOGLE_USER_PASSWORD))
        }
        val currentPasswordObject = getCurrentPassword(id, currentPassword).getOrElse {
          throw new BadRequestException(resource.getString(ResourceNames.INVALID_PASSWORD))
        }
        updatePassword(id, newPassword, currentPasswordObject)
      }
    }
  }

  /**
   * 現在のパスワードオブジェクトを取得する。
   *
   * @param id ユーザID
   * @param currentPassword 現在のパスワード
   * @param s DBセッション
   * @return 取得結果
   */
  private def getCurrentPassword(
    id: String,
    currentPassword: String
  )(implicit s: DBSession): Option[persistence.Password] = {
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
        .eq(u.disabled, false)
        .and
        .isNull(p.deletedAt)
    }.map(persistence.Password(p.resultName)).single.apply()
  }

  /**
   * パスワードを更新する。
   *
   * @param id ユーザID
   * @param newPassword 新しいパスワード
   * @param currentPassword 現在のパスワードオブジェクト
   * @param s DBセッション
   * @return 変更件数
   */
  private def updatePassword(
    id: String,
    newPassword: String,
    currentPassword: persistence.Password
  )(implicit s: DBSession): Int = {
    val newPasswordHash = createPasswordHash(newPassword)
    withSQL {
      val p = persistence.Password.column
      update(persistence.Password)
        .set(p.hash -> newPasswordHash, p.updatedAt -> DateTime.now, p.updatedBy -> sqls.uuid(id))
        .where
        .eq(p.id, sqls.uuid(currentPassword.id))
    }.update.apply()
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
   * @return
   *         Success(User) 更新成功時、更新後ユーザ情報
   *         Failure(NullPointerException) 引数がnullの場合
   *         Failure(NotFoundException) ユーザが見つからない場合(無効化されている場合も含む)
   *         Failure(BadRequestException) 更新対象ユーザがGoogleアカウントユーザのアカウント名を変更する場合
   *         Failure(BadRequestException) アカウント名が既に登録されている場合
   */
  def updateUserProfile(
    id: String,
    name: Option[String],
    fullname: Option[String],
    organization: Option[String],
    title: Option[String],
    description: Option[String]
  ): Try[User] = {
    Try {
      CheckUtil.checkNull(id, "id")
      CheckUtil.checkNull(name, "name")
      CheckUtil.checkNull(fullname, "fullname")
      CheckUtil.checkNull(organization, "organization")
      CheckUtil.checkNull(title, "title")
      CheckUtil.checkNull(description, "description")
      DB.localTx { implicit s =>
        val trimmedName = StringUtil.trimAllSpaces(name.getOrElse(""))
        val trimmedFullname = StringUtil.trimAllSpaces(fullname.getOrElse(""))
        val trimmedOrganization = StringUtil.trimAllSpaces(organization.getOrElse(""))
        val trimmedTitle = StringUtil.trimAllSpaces(title.getOrElse(""))
        val checkedDescription = description.getOrElse("")

        if (isGoogleUser(id)) {
          // Googleアカウントユーザーはアカウント名の変更禁止(importスクリプトでusersテーブルのname列を使用しているため)
          persistence.User.find(id).filter(!_.disabled) match {
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

        persistence.User.find(id).filter(!_.disabled) match {
          case None => {
            throw new NotFoundException()
          }
          case Some(x) => {
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

            val address = persistence.MailAddress.findByUserId(user.id).map(_.address).getOrElse("")
            User(user, address)
          }
        }
      }
    }
  }

  /**
   * 指定したユーザのアイコンを更新します。
   *
   * @param id ユーザID
   * @param icon アイコン画像
   * @return
   *         Success(String) 更新成功時、アイコン画像ID
   *         Failure(NullPointerException) 引数がnullの場合
   *         Failure(NotFoundException) ユーザが見つからない場合(無効化されている場合も含む)
   */
  def changeIcon(id: String, icon: FileItem): Try[String] = {
    Try {
      DB.localTx { implicit s =>
        persistence.User.find(id).filter(!_.disabled) match {
          case None => {
            throw new NotFoundException()
          }
          case Some(x) => {
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

            image.id
          }
        }
      }
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
    DB.readOnly { implicit s =>
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
          .eq(u.disabled, false)
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
      }.single.apply()

      targetUser.filter(user => getSignature(user._2, user._3) == signature).map { user =>
        User(
          id = user._1.id,
          name = user._1.name,
          fullname = user._1.fullname,
          organization = user._1.organization,
          title = user._1.title,
          image = user._1.imageId,
          mailAddress = user._4,
          description = user._1.description,
          isGuest = false,
          isDisabled = false
        )
      }
    }
  }

  /**
   * APIキーとシークレットキーからシグネチャを取得する。
   *
   * @param apiKey APIキー
   * @param secretKey シークレットキー
   * @return シグネチャ
   */
  private def getSignature(apiKey: String, secretKey: String): String = {
    val sk = new SecretKeySpec(secretKey.getBytes(), "HmacSHA1");
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(sk)
    val result = mac.doFinal((apiKey + "&" + secretKey).getBytes())
    URLEncoder.encode(Base64.getEncoder.encodeToString(result), "UTF-8")
  }

  /**
   * 同名のユーザが存在するかを確認する。
   *
   * @param id ユーザID
   * @param name ユーザ名
   * @param session DBセッション
   * @return 指定ユーザ以外で同名のユーザが存在する場合はtrue、それ以外の場合はfalse
   */
  private def existsSameName(id: String, name: String)(implicit session: DBSession): Boolean = {
    val u = persistence.User.u
    withSQL {
      select(sqls"1")
        .from(persistence.User as u)
        .where.lowerEq(u.name, name).and.ne(u.id, sqls.uuid(id))
        .limit(1)
    }.map(_ => ()).single.apply().nonEmpty
  }

  /**
   * パスワードからハッシュ値を作成する。
   *
   * @param password パスワード
   * @return ハッシュ化したパスワード
   */
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
    Try {
      CheckUtil.checkNull(user, "user")
      DB.readOnly { implicit s =>
        ProfileData(user, isGoogleUser(user.id))
      }
    }
  }

  /**
   * Googleユーザか否かを判定する。
   *
   * @param id ユーザID
   * @param session DBセッション
   * @return Googleユーザであればtrue、それ以外の場合はfalse
   */
  private def isGoogleUser(id: String)(implicit session: DBSession): Boolean = {
    persistence.GoogleUser.findByUserId(id).isDefined
  }
}
