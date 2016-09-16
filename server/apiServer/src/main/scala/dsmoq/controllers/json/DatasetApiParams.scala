package dsmoq.controllers.json

import org.json4s.CustomSerializer
import org.json4s.Extraction
import org.json4s.Formats
import org.json4s.JValue
import org.json4s.JObject
import org.json4s.JsonDSL._

import dsmoq.services.DataSetAttribute
import dsmoq.services.json.SearchDatasetCondition
import dsmoq.services.json.SearchDatasetConditionSerializer

sealed trait SearchDatasetParams

object SearchDatasetParams {
  case class Condition(
    query: SearchDatasetCondition,
    limit: Option[Int] = None,
    offset: Option[Int] = None
  ) extends SearchDatasetParams

  case class Params(
    query: Option[String] = None,
    owners: List[String] = List.empty,
    groups: List[String] = List.empty,
    attributes: List[DataSetAttribute] = List.empty,
    limit: Option[Int] = None,
    offset: Option[Int] = None,
    orderby: Option[String] = None
  ) extends SearchDatasetParams

  def apply(): SearchDatasetParams = Condition(SearchDatasetCondition.Query())

  def unapply(x: JValue)(implicit formats: Formats): Option[SearchDatasetParams] = {
    x.extractOpt[Condition] orElse x.extractOpt[Params]
  }
}

object SearchDatasetParamsSerializer extends CustomSerializer[SearchDatasetParams](implicit formats => {
  val deserializer: PartialFunction[JValue, SearchDatasetParams] = {
    case SearchDatasetParams(p) => p
  }
  val serializer: PartialFunction[Any, JValue] = {
    case x: SearchDatasetParams.Condition => {
      ("query" -> Extraction.decompose(x.query)) ~
        ("limit" -> x.limit) ~
        ("offset" -> x.offset)
    }
    case x: SearchDatasetParams.Params => {
      ("query" -> x.query) ~
        ("owners" -> Extraction.decompose(x.owners)) ~
        ("groups" -> Extraction.decompose(x.groups)) ~
        ("attributes" -> Extraction.decompose(x.attributes)) ~
        ("limit" -> x.limit) ~
        ("offset" -> x.offset) ~
        ("orderby" -> x.orderby)
    }
  }
  (deserializer, serializer)
})

case class UpdateDatasetFileMetadataParams(
  name: Option[String] = None,
  description: Option[String] = None
)

case class UpdateDatasetMetaParams(
  name: Option[String] = None,
  description: Option[String] = None,
  license: Option[String] = None,
  attributes: List[DataSetAttribute] = List.empty
)

case class ChangePrimaryImageParams(
  imageId: Option[String] = None
)

case class UpdateDatasetGuestAccessParams(
  accessLevel: Option[Int] = None
)

case class DatasetStorageParams(
  saveLocal: Option[Boolean] = None,
  saveS3: Option[Boolean] = None
)

case class SearchRangeParams(
  limit: Option[Int] = None,
  offset: Option[Int] = None
)

case class SearchAppsParams(
  excludeIds: Seq[String] = Seq.empty,
  deletedType: Option[Int] = None,
  limit: Option[Int] = None,
  offset: Option[Int] = None
)

case class ChangePrimaryAppParams(
  appId: Option[String] = None
)

case class CreateDatasetQueryParams(
  name: Option[String] = None,
  condition: SearchDatasetCondition
)
