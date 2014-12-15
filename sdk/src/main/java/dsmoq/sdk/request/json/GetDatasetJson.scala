package dsmoq.sdk.request.json

case class GetDatasetJson(
  var query: Option[String] = None,
  var owners: List[String] = List.empty,
  var groups: List[String] = List.empty,
  var attributes: List[Attribute] = List.empty,
  var limit: Option[Int] = None,
  var offset: Option[Int] = None
) extends Jsonable

case class Attribute(var id: String,var value: String)