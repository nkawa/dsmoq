package dsmoq.sdk.request.json

case class GetDatasetsJson(
  query: Option[String] = None,
  owners: List[String] = List.empty,
  groups: List[String] = List.empty,
  attributes: List[Attribute] = List.empty,
  limit: Option[Int] = None,
  offset: Option[Int] = None
) extends Jsonable

case class Attribute(id: String, value: String)