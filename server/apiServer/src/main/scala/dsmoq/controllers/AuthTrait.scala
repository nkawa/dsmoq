package dsmoq.controllers

import java.util.ResourceBundle
import javax.servlet.http.HttpServletRequest
import scala.util.{ Try, Success, Failure }
import org.scalatra.ScalatraServlet

import com.typesafe.scalalogging.LazyLogging
import org.slf4j.MarkerFactory

import dsmoq.AppConf
import dsmoq.ResourceNames
import dsmoq.exceptions.NotAuthorizedException
import dsmoq.services.{ AccountService, CheckUtil, User }

/**
 * 認証処理、セッション操作を取り扱うトレイト
 */
trait AuthTrait { this: ScalatraServlet with LazyLogging =>
  import AuthTrait._

  def resource: ResourceBundle

  /**
   * AccountServiceのインスタンス
   */
  private val accountService = new AccountService(resource)

  /**
   * セッションに登録されたユーザ情報を更新する。
   *
   * Authorizationヘッダに認証情報がある場合、セッションの更新は行わない。
   * @param user 更新するユーザ情報
   * @return
   *   Success(()) 更新に成功した場合、あるいはAuthorizationヘッダに認証情報がある場合
   *   Failure(NullPointerException) userがnullの場合
   *   Failure(IllegalStateException) 更新に失敗した場合
   */
  def updateSessionUser(user: User): Try[Unit] = {
    Try {
      CheckUtil.checkNull(user, "user")
      if (hasAuthorizationHeader()) {
        ()
      } else {
        session.setAttribute(SESSION_KEY, user)
      }
    }
  }

  /**
   * セッションをinvalidateし、クッキーからセッションIDを取り除く。
   *
   * Authorizationヘッダに認証情報がある場合、セッションの更新は行わない。
   */
  def clearSession(): Unit = {
    if (hasAuthorizationHeader()) {
      return
    }
    // sessionを参照すると新規sessionが作成されてしまうため、sessionOptionで存在チェック
    if (sessionOption.isDefined) {
      session.invalidate()
    }
    cookies.delete(SESSION_ID)
  }

  /**
   * リクエストヘッダにAuthorizationヘッダが存在し、かつapi_key、signatureの設定があるか否かを判定する。
   *
   * @return Authorizationヘッダが存在し、かつapi_key、signatureの設定がある場合true、そうでなければfalse
   */
  def hasAuthorizationHeader(): Boolean = {
    getAuthorizationHeaderInfo(request) match {
      case ApiKeyAndSignature(_, _) => true
      case _ => false
    }
  }

  /**
   * セッションからユーザを取得する。
   *
   * @return セッションからユーザを取得できた場合そのユーザ、取得できなかった場合ゲストユーザ
   */
  def getUserFromSession(): User = {
    val ret = for {
      s <- sessionOption
      sessionUser <- s.get(SESSION_KEY)
    } yield {
      val user = sessionUser.asInstanceOf[User]
      logger.info(LOG_MARKER, "Auth: Get user from Session: User found. user={}", user)
      user
    }
    ret.getOrElse {
      logger.info(LOG_MARKER, "Auth: Get user from Session: User not found. Use guest user.")
      GUEST_USER
    }
  }

  /**
   * Authorizationヘッダまたはセッションからユーザを取得する。
   *
   * Authorizationヘッダからのユーザ取得に成功した場合、そのユーザを返す。
   * Authorizationヘッダがない場合は、セッションから取得したユーザを返す。
   * Authorizationヘッダがない、かつセッションからユーザを取得できながった場合、ゲストユーザを許可するならゲストユーザを返す。
   * @param allowGuest ゲストユーザを許可するか
   * @return
   *   Success(User) 取得したユーザ
   *   Failure(NullPointerException) Sevletから取得したrequestがnullの場合
   *   Failure(NotAuthorizedException) Authorizationヘッダからのユーザ取得に失敗した場合、またはゲストユーザを許可せずユーザが取得できなかった場合
   */
  def getUser(allowGuest: Boolean): Try[User] = {
    val ret = getUser(request)
    if (allowGuest) {
      ret
    } else {
      ret.flatMap { user =>
        if (user.isGuest) {
          logger.error(LOG_MARKER, "Auth: Not Allow Guest.")
          Failure(new NotAuthorizedException(resource.getString(ResourceNames.NOT_ALLOW_GUEST)))
        } else {
          Success(user)
        }
      }
    }
  }

  /**
   * Authorizationヘッダまたはセッションからユーザを取得する。
   *
   * Authorizationヘッダからのユーザ取得に成功した場合、そのユーザを返す。
   * Authorizationヘッダがない場合は、セッションから取得したユーザを返す。
   * Authorizationヘッダがない、かつセッションからユーザを取得できながった場合、ゲストユーザを返す。
   * @param request HTTPリクエスト
   * @return
   *   Success(User) 取得したユーザ
   *   Failure(NullPointerException) requestがnullの場合
   *   Failure(NotAuthorizedException) Authorizationヘッダからのユーザ取得に失敗した場合
   */
  private def getUser(request: HttpServletRequest): Try[User] = {
    Try {
      CheckUtil.checkNull(request, "request")
      getAuthorizationHeaderInfo(request) match {
        case ApiKeyAndSignature(apiKey, signature) => {
          accountService.getUserByKeys(apiKey, signature).map { user =>
            logger.info(LOG_MARKER, "Auth: Get user from Authorization Header: User found. user={}", user)
            user
          }.getOrElse {
            logger.error(LOG_MARKER, "Auth: Get user from Authorization Header: User not found. api_key={}, signature={}", apiKey, signature)
            throw new NotAuthorizedException(resource.getString(ResourceNames.INVALID_APIKEY_OR_SIGNATURE))
          }
        }
        case ApiKeyAndSignatureNotFound => {
          logger.error(LOG_MARKER, "Auth: Get user from Authorization Header: ApiKey and Signature not found.")
          throw new NotAuthorizedException(resource.getString(ResourceNames.REQUIRE_APIKEY_AND_SIGNATURE))
        }
        case InvalidAuthorizationHeader => {
          logger.error(LOG_MARKER, "Auth: Get user from Authorization Header: Invalid Authorization Header")
          throw new NotAuthorizedException(resource.getString(ResourceNames.INVALID_AUTHORIZATION_HEADER))
        }
        case NoAuthorizationHeader => {
          logger.info(LOG_MARKER, "Auth: No Authorization Header")
          // Authorizationヘッダがない場合、セッションからユーザ情報を取得する
          getUserFromSession()
        }
      }
    }
  }
}

/**
 * 認証処理、セッション操作を取り扱うトレイトのコンパニオンオブジェクト
 */
object AuthTrait {
  /**
   * ログマーカー
   */
  private val LOG_MARKER = MarkerFactory.getMarker("AUTH_LOG")

  /**
   * セッションのユーザ情報のキー
   */
  private val SESSION_KEY = "user"

  /**
   * JSESSIONID
   */
  private val SESSION_ID = "JSESSIONID"

  /**
   * ゲストユーザ情報
   */
  val GUEST_USER = User(
    id = AppConf.guestUserId,
    name = "",
    fullname = "",
    organization = "",
    title = "",
    image = "http://xxxx",
    mailAddress = "",
    description = "",
    isGuest = true,
    isDeleted = false
  )

  /**
   * HTTPヘッダからAuthorizationヘッダ内のAPIキー、シグネチャを取得する。
   *
   * @param request HTTPリクエスト
   * @return Authorizationヘッダの情報
   * @throws NullPointerException 引数がnullの場合
   */
  private def getAuthorizationHeaderInfo(request: HttpServletRequest): AuthorizationHeaderInfo = {
    CheckUtil.checkNull(request, "request")
    val header = request.getHeader("Authorization")
    if (header == null) {
      // Authorizationヘッダが存在しない
      return NoAuthorizationHeader
    }
    Try {
      header.split(',').map(x => x.trim.split('=')).map(x => (x(0), x(1))).toMap
    }.map { headers =>
      val apiKey = headers.get("api_key")
      val signature = headers.get("signature")
      (apiKey, signature) match {
        case (Some(checkedKey), Some(checkedSignature)) => {
          // api_keyとsignature両方が取得できた
          ApiKeyAndSignature(checkedKey, checkedSignature)
        }
        case _ => {
          // 取得できなかった
          ApiKeyAndSignatureNotFound
        }
      }
    }.getOrElse {
      // Authorizationヘッダの形式が不正
      InvalidAuthorizationHeader
    }
  }

  /**
   * Authorizationヘッダの情報
   */
  sealed trait AuthorizationHeaderInfo

  /**
   * AuthorizationヘッダからAPIキー・シグネチャが取得できたことを表す
   *
   * @param apiKey APIキー
   * @param signature シグネチャ
   */
  case class ApiKeyAndSignature(apiKey: String, signature: String) extends AuthorizationHeaderInfo

  /**
   * Authorizationヘッダが不正であったことを表す
   */
  case object InvalidAuthorizationHeader extends AuthorizationHeaderInfo

  /**
   * APIキーとシグネチャが取得できなかったことを表す
   */
  case object ApiKeyAndSignatureNotFound extends AuthorizationHeaderInfo

  /**
   * Authorizationヘッダそのものを取得できなかったことを表す
   */
  case object NoAuthorizationHeader extends AuthorizationHeaderInfo
}
