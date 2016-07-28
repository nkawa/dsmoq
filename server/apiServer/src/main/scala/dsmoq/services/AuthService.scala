package dsmoq.services

import java.util.ResourceBundle
import javax.servlet.http.HttpServletRequest
import scala.util.{Try, Success, Failure}
import org.scalatra.ScalatraServlet

import com.typesafe.scalalogging.LazyLogging
import org.slf4j.MarkerFactory

import dsmoq.AppConf
import dsmoq.ResourceNames
import dsmoq.exceptions.NotAuthorizedException

/**
 * 認証処理、セッション操作を取り扱うサービスクラス
 * 
 * @param resource ResourceBundleのインスタンス
 * @param servlet 認証処理、セッション操作を呼び出すサーブレットのインスタンス
 */
class AuthService(resource: ResourceBundle, servlet: ScalatraServlet) extends LazyLogging {

  /**
   * AccountServiceのインスタンス
   */
  private val accountService = new AccountService(resource)

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
  val GUEST_USER = User(AppConf.guestUserId, "", "", "", "", "http://xxxx", "", "", true, false)

  /**
   * セッションに登録されたユーザ情報を更新する。
   * 
   * @param user 更新するユーザ情報
   * @throws NullPointerException 引数がnullの場合
   */
  def updateSessionUser(user: User): Try[Unit] = {
    Try {
      // servlet.session, sessionOptionが要求するimplicit valを満たすため変数定義
      implicit val request = servlet.request
      CheckUtil.checkNull(user, "user")
      servlet.session.setAttribute(SESSION_KEY, user)
    }
  }

  /**
   * セッションをinvalidateし、クッキーからセッションIDを取り除く。
   */
  def clearSession(): Unit = {
    // servlet.session, sessionOptionが要求するimplicit valを満たすため変数定義
    implicit val request = servlet.request
    // sessionを参照すると新規sessionが作成されてしまうため、sessionOptionで存在チェック
    servlet.sessionOption match {
      case Some(_) => servlet.session.invalidate()
      case None => // do nothing
    }
    servlet.cookies.delete(SESSION_ID)
  }

  /**
   * リクエストヘッダにAuthorizationヘッダが存在し、かつapi_key、signatureの設定があるか否かを判定する。
   *
   * @return Authorizationヘッダが存在し、かつapi_key、signatureの設定があるか否か
   */
  def hasAuthorizationHeader(): Boolean = {
    getAuthorizationHeaderInfo(servlet.request) match {
      case ApiKeyAndSignature(apiKey, signature) => true
      case _ => false
    }
  }

  /**
   * Authorizationヘッダの情報
   */
  sealed trait AuthorizationHeaderInfo

  /**
   * AuthorizationヘッダからAPIキー・シグネチャが取得できた場合
   *
   * @param apiKey APIキー
   * @param signature シグネチャ
   */
  case class ApiKeyAndSignature(apiKey: String, signature: String) extends AuthorizationHeaderInfo

  /**
   * APIキーが取得できなかった場合
   */
  case object ApiKeyNotFound extends AuthorizationHeaderInfo

  /**
   * シグネチャが取得できなかった場合
   */
  case object SignatureNotFound extends AuthorizationHeaderInfo

  /**
   * APIキー、シグネチャともに取得できなかった場合
   */
  case object BothNotFound extends AuthorizationHeaderInfo

  /**
   * Authorizationヘッダそのものを取得できなかった場合
   */
  case object NoAuthorizationHeader extends AuthorizationHeaderInfo

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
      return NoAuthorizationHeader
    }
    val headers = header.split(',').map(x => x.trim.split('=')).map(x => (x(0), x(1))).toMap
    val apiKey = headers.get("api_key")
    val signature = headers.get("signature")
    (apiKey, signature) match {
      case (Some(checkedKey), Some(checkedSignature)) => ApiKeyAndSignature(checkedKey, checkedSignature)
      case (None, Some(checkedSignature)) => ApiKeyNotFound
      case (Some(checkedKey), None) => SignatureNotFound
      case (None, None) => BothNotFound
    }
  }

  /**
   * セッションからユーザを取得する。
   * 
   * @return
   *        セッションからユーザを取得できた場合、そのユーザを返却する。
   *        セッションからユーザを取得できなかった場合、ゲストユーザを返却する。
   */
  def getUserFromSession(): User = {
    // session.getを使用するためのimplicit conversionをimportしている
    import servlet._
    servlet.sessionOption match {
      case Some(_) => servlet.session.get(SESSION_KEY) match {
        case Some(sessionUser) => {
          val user = sessionUser.asInstanceOf[User]
          logger.info(LOG_MARKER, "Auth: Get user from Session: User found. user={}", user)
          user
        }
        case None => {
          logger.info(LOG_MARKER, "Auth: Get user from Session: User not found. Use guest user.")
          GUEST_USER
        }
      }
      case None => {
        logger.info(LOG_MARKER, "Auth: Get user from Session: User not found. Use guest user.")
        GUEST_USER
      }
    }
  }

  /**
   * Authorizationヘッダ、セッションからユーザを取得する。
   *
   * @param request HTTPリクエスト
   * @return
   *        Success(User) Authorizationヘッダからのユーザ取得に成功した場合、Authorizationヘッダがない場合
   *        Failure(NotAuthorizedException) Authorizationヘッダからのユーザ取得に失敗した場合
   *        Failure(NullPointerException) requestがnullの場合
   */
  private def getUser(request: HttpServletRequest): Try[User] = {
    Try {
      CheckUtil.checkNull(request, "request")
      getAuthorizationHeaderInfo(request) match {
        case ApiKeyAndSignature(apiKey, signature) => {
          accountService.getUserByKeys(apiKey, signature) match {
            case Some(user) => {
              logger.info(LOG_MARKER, "Auth: Get user from Authorization Header: User found. user={}", user)
              user
            }
            case None => {
              logger.error(LOG_MARKER, "Auth: Get user from Authorization Header: User not found. api_key={}, signature={}", apiKey, signature)
              throw new NotAuthorizedException(resource.getString(ResourceNames.INVALID_APIKEY_OR_SIGNATURE))
            }
          }
        }
        case ApiKeyNotFound => {
          logger.error(LOG_MARKER, "Auth: Get user from Authorization Header: ApiKey not found.")
          throw new NotAuthorizedException(resource.getString(ResourceNames.REQUIRE_APIKEY))
        }
        case SignatureNotFound => {
          logger.error(LOG_MARKER, "Auth: Get user from Authorization Header: Signature not found.")
          throw new NotAuthorizedException(resource.getString(ResourceNames.REQUIRE_SIGNATURE))
        }
        case BothNotFound => {
          logger.error(LOG_MARKER, "Auth: Get user from Authorization Header: ApiKey and Signature not found.")
          throw new NotAuthorizedException(resource.getString(ResourceNames.REQUIRE_APIKEY_AND_SIGNATURE))
        }
        case NoAuthorizationHeader => {
          logger.info(LOG_MARKER, "Auth: No Authorization Header")
          // Authorizationヘッダがない場合、セッションからユーザ情報を取得する
          getUserFromSession()
        }
      }
    }
  }

  /**
   * Authorizationヘッダ、セッションからユーザを取得する。
   *
   * @return
   *        Success(User) Authorizationヘッダからのユーザ取得に成功した場合、Authorizationヘッダがない場合
   *        Failure(NotAuthorizedException) Authorizationヘッダからのユーザ取得に失敗した場合
   *        Failure(NullPointerException) Sevletから取得したrequestがnullの場合
   */
  def getUser(): Try[User] = getUser(servlet.request)

  /**
   * Authorizationヘッダ、セッションからゲストユーザではないユーザを取得する。
   *
   * @return
   *        Success(User) Authorizationヘッダからのユーザ取得に成功した場合、Authorizationヘッダがない場合にセッションからのユーザ取得に成功した場合
   *        Failure(NotAuthorizedException) Authorizationヘッダ・セッションからのユーザ取得に失敗した場合
   *        Failure(NullPointerException) Sevletから取得したrequestがnullの場合
   */
  def getNotGuestUser(): Try[User] = {
    getUser(servlet.request) match {
      case Success(user) if user.isGuest => {
        logger.error(LOG_MARKER, "Auth: Not Allow Guest.")
        Failure(new NotAuthorizedException(resource.getString(ResourceNames.NOT_ALLOW_GUEST)))
      }
      case other => other
    }
  }
}
