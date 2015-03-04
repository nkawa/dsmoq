package dsmoq.sdk.request.json

case class GetDatasetsJson(
  query: Option[String] = None,
  owners: List[String] = List.empty,
  groups: List[String] = List.empty,
  attributes: List[Attribute] = List.empty,
  limit: Option[Int] = Some(20),
  offset: Option[Int] = Some(0)
) extends Jsonable

case class Attribute(id: String, value: String)