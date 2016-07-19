package dsmoq.controllers

import dsmoq.services.{AccountService, User}

import com.typesafe.scalalogging.LazyLogging
import org.slf4j.MarkerFactory

import java.util.ResourceBundle
import javax.servlet.http.HttpServletRequest

sealed trait UserFromHeader
case class ApiUser(user: User) extends UserFromHeader
case object ApiAuthorizationFailed extends UserFromHeader
case object NoAuthorizationHeader extends UserFromHeader

trait ApiKeyAuthorizationTrait extends LazyLogging {
 
  /**
   * AccountServiceのインスタンス
   */
  val accountService: AccountService

  /**
    * ログマーカー
    */
  private val LOG_MARKER = MarkerFactory.getMarker("AUTH_LOG")

  /**
   * リクエストヘッダからログインユーザーを取得します。
   * リクエストヘッダにAuthorizationヘッダがある場合、ログインユーザーはヘッダ内の認証情報を元にログインユーザーを取得します。
   *
   * @param request HTTPリクエスト
   * @return ログインユーザー
   */
  def userFromHeader(request: HttpServletRequest): UserFromHeader = {
    logger.info(LOG_MARKER, "Get user from header: request={}", request)
    getAuthorizationInfo(request) match {
      case Some((apiKey, signature)) => 
        accountService.getUserByKeys(apiKey, signature) match {
          case Some(user) => {
            logger.info(LOG_MARKER, "Get user from header: User found. user={}", user)
            ApiUser(user)
          }
          case None => {
            logger.error(LOG_MARKER, "Get user from header: User not found. api_key={}, signature={}", apiKey, signature)
            ApiAuthorizationFailed
          }
        }
      case None => {
        logger.info(LOG_MARKER, "Get user from header: No Authorization header.")
        NoAuthorizationHeader
      }
    }
  }

  /**
   * リクエストヘッダにAuthorizationヘッダが存在し、かつapi_key、signatureの設定があるか否かを判定する。
   *
   * @param request HTTPリクエスト
   * @return Authorizationヘッダが存在し、かつapi_key、signatureの設定があるか否か
   */
  def hasAuthorizationHeader(request: HttpServletRequest): Boolean = {
    getAuthorizationInfo(request).nonEmpty
  }

  private def getAuthorizationInfo(request: HttpServletRequest): Option[(String, String)] = {
    val header = request.getHeader("Authorization")
    if (header == null) return None
    val headers = header.split(',').map(x => x.trim.split('=')).map(x => (x(0), x(1))).toMap
    if (headers.size != 2) return None
    val apiKey = headers("api_key")
    val signature = headers("signature")
    if (apiKey.isEmpty || signature.isEmpty) return None
    Some((apiKey, signature))
  }
}
