package dsmoq.controllers.json

import dsmoq.services.DataSetAttribute

case class SearchDatasetsParams(
  query: Option[String] = None,
  owners: List[String] = List.empty,
  groups: List[String] = List.empty,
  attributes: List[DataSetAttribute] = List.empty,
  limit: Option[Int] = None,
  offset: Option[Int] = None,
  orderby: Option[String] = None
)

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
