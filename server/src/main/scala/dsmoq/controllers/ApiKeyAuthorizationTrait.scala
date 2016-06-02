package dsmoq.controllers

import dsmoq.services.{AccountService, User}

import javax.servlet.http.HttpServletRequest

sealed trait UserFromHeader
case class ApiUser(user: User) extends UserFromHeader
case object ApiAuthorizationFailed extends UserFromHeader
case object NoAuthorizationHeader extends UserFromHeader

trait ApiKeyAuthorizationTrait {
  
  /**
   * リクエストヘッダからログインユーザーを取得します。
   * リクエストヘッダにAuthorizationヘッダがある場合、ログインユーザーはヘッダ内の認証情報を元にログインユーザーを取得します。
   *
   * @param request HTTPリクエスト
   * @return ログインユーザー
   */
  def userFromHeader(request: HttpServletRequest): UserFromHeader = {
    getAuthorizationInfo(request) match {
      case Some((apiKey, signature)) => 
        AccountService.getUserByKeys(apiKey, signature) match {
          case Some(user) => ApiUser(user)
          case None => ApiAuthorizationFailed
        }
      case None => NoAuthorizationHeader
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
