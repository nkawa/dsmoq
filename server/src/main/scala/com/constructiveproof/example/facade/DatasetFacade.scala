package com.constructiveproof.example.facade

import com.constructiveproof.example.traits.SessionUserInfo
import scala.util.{Try, Success}

object DatasetFacade {
  def searchDatasets(params: SearchDatasetsParams): Try[Datasets] = {
    // FIXME dummy セッションデータ有無チェック
    val name = if (params.userInfo.isGuest) {
      "guest"
    } else {
      "user"
    }

    // FIXME dummy
    val summary = DatasetsSummary(100)
    val attributes = List(DatasetAttribute("xxx", "xxx"))
    val owners = List(DatasetsOwner(1, "xxx", "http://xxx"))
    val groups = List(DatasetsGroup(1, "xxx", "http://xxx"))
    val result = DatasetsResult(
      id = 1,
      name = name,
      description = "xxxx",
      image = "http://xxx",
      attributes = attributes,
      owners = owners,
      groups = groups,
      files = 3,
      dataSize = 1024,
      defaultAccessLevel = 1,
      permission = 1
    )
    Success(Datasets(summary, List(result)))
  }

  def getDataset(params: GetDatasetParams): Try[Dataset] = {
    // FIXME dummy セッションデータ有無チェック
    val primaryImage = if (params.userInfo.isGuest) {
      "primaryImage guest"
    } else {
      "primaryImage user"
    }

    // FIXME dummy data
    val datasetFiles = List(DatasetFile("1", "filename"))
    val datasetMetaData = DatasetMetaData(
      "metadataName",
      "metadataDescription",
      1,
      List(DatasetAttribute("attr_name", "attr_value"))
    )
    val datasetImages = List(DatasetImage("image_id", "http://xxx"))
    val datasetOwnerships = List(DatasetOwnership("owner_id", 1, "ownership_name", "http://xxxx"))

    Success(Dataset(
      id = params.id,
      files = datasetFiles,
      meta = datasetMetaData,
      images = datasetImages,
      primaryImage =  primaryImage,
      ownerships = datasetOwnerships,
      defaultAccessLevel = 1,
      permission = 1
    ))
  }
}

// request
case class SearchDatasetsParams(
  query: Option[String],
  group: Option[String],
  attributes: Map[String, Seq[String]],
  limit: Option[String],
  offset: Option[String],
  userInfo: User
) extends SessionUserInfo

case class GetDatasetParams(
  id: String,
  userInfo: User
) extends SessionUserInfo

// response
case class Datasets(
  summary: DatasetsSummary,
  results: List[DatasetsResult]
)

case class DatasetsSummary(
  total: Int,
  count: Int = 20,
  offset: Int = 0
)

case class DatasetsResult(
  id: Int,
  name: String,
  description: String,
  image: String,
  license: Int = 1,
  attributes: List[DatasetAttribute],
  owners: List[DatasetsOwner],
  groups: List[DatasetsGroup],
  files: Int,
  dataSize: Int,
  defaultAccessLevel: Int,
  permission: Int
)

case class DatasetsOwner(
  id: Int,
  name: String,
  image: String
)

case class DatasetsGroup(
  id: Int,
  name: String,
  image: String
)

case class Dataset(
  id: String,
  files: List[DatasetFile],
  meta: DatasetMetaData,
  images: List[DatasetImage],
  primaryImage: String,
  ownerships: List[DatasetOwnership],
  defaultAccessLevel: Int,
  permission: Int
)

case class DatasetMetaData(
  name: String,
  description: String,
  license : Int,
  attributes: List[DatasetAttribute]
)

case class DatasetAttribute(
  name: String,
  value: String
)

case class DatasetFile(
  id: String,
  name: String
)

case class DatasetImage(
  id: String,
  url: String
)

case class DatasetOwnership(
  id: String,
  ownerType: Int,
  name: String,
  image: String
)