package jp.ac.nagoya_u.dsmoq.sdk.request.json

private[request] case class GetMembersJson(limit: Option[Int] = Some(20), offset: Option[Int] = Some(0)) extends Jsonable
