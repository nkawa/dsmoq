package dsmoq.controllers.json

import dsmoq.services.{User, DataSetAttribute}

case class SearchDatasetsParams(
  query: Option[String] = None,
  owners: List[String] = List.empty,
  groups: List[String] = List.empty,
  attributes: List[DataSetAttribute] = List.empty,
  limit: Option[Int] = None,
  offset: Option[Int] = None
)

case class UpdateDatasetFileMetadataParams(
  name: Option[String] = None,
  description: Option[String] = None
)

case class UpdateDatasetMetaParams(
  name: Option[String] = None,
  description: Option[String] = None,
  license: Option[String] = None,
  attributes: List[DataSetAttribute] = List.empty,
  saveLocal: Option[Boolean] = None,
  saveS3: Option[Boolean] = None
)

case class ChangePrimaryImageParams(
  imageId: Option[String] = None
)

case class UpdateDatasetGuestAccessParams(
  accessLevel: Option[Int] = None
)

case class CreateDatasetParams(
  saveLocal: Option[Boolean] = None,
  saveS3: Option[Boolean] = None
)