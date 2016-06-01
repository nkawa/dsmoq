package dsmoq.controllers

import dsmoq.services.{AccountService, User}

trait UserTrait {
  def userFromHeader(header: String) :Option[Option[User]] = {
    if (header == null) return None
    val headers = header.split(',').map(x => x.trim.split('=')).map(x => (x(0), x(1))).toMap
    if (headers.size != 2) return None
    val apiKey = headers("api_key")
    val signature = headers("signature")
    if (apiKey.isEmpty || signature.isEmpty) return None
    Some(AccountService.getUserByKeys(apiKey, signature))
  }
}
