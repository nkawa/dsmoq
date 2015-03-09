package dsmoq.sdk.request.json

private[request] case class GetRangeJson(
  limit: Option[Int] = Some(20),
  offset: Option[Int] = Some(0)
) extends Jsonable