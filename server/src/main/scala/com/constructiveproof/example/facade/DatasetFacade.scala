package com.constructiveproof.example.facade

import com.constructiveproof.example.traits.SessionUserInfo

object DatasetFacade {
  def searchDatasets(params: SearchDatasetsParams) = {
    // FIXME dummy セッションデータ有無チェック
    val name = params.userInfo match {
      case Some(_) => "user"
      case None => "guest"
    }

    // FIXME dummy
    val summary = DatasetsSummary(100)
    val attributes = List(DatasetAttribute("xxx", "xxx"))
    val owners = List(DatasetOwner(1, "xxx", "http://xxx"))
    val groups = List(DatasetGroup(1, "xxx", "http://xxx"))
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
    val datasets = Datasets(summary, List(result))
    datasets
  }

  def getDataset(params: GetDatasetParams) = {
    // FIXME dummy セッションデータ有無チェック
    val primaryImage = params.userInfo match {
      case Some(_) => "primaryImage user"
      case None => "primaryImage guest"
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
    val datasetOwners = List(DatasetOwner(1, "xxx", "http://xxxx"))
    val datasetGroups = List(DatasetGroup(1, "xxx", "http://xxxx"))

    val dataset = Dataset(
      id = params.id,
      files = datasetFiles,
      meta = datasetMetaData,
      images = datasetImages,
      primaryImage =  primaryImage,
      owners = datasetOwners,
      groups = datasetGroups,
      defaultAccessLevel = 1,
      permission = 1
    )
    dataset
  }
}

// request
case class SearchDatasetsParams(
  query: Option[String],
  group: Option[String],
  attributes: Map[String, Seq[String]],
  limit: Option[String],
  offset: Option[String],
  userInfo: Option[User]
) extends SessionUserInfo

case class GetDatasetParams(
  id: String,
  userInfo: Option[User]
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
  owners: List[DatasetOwner],
  groups: List[DatasetGroup],
  files: Int,
  dataSize: Int,
  defaultAccessLevel: Int,
  permission: Int
)

case class Dataset(
  id: String,
  files: List[DatasetFile],
  meta: DatasetMetaData,
  images: List[DatasetImage],
  primaryImage: String,
  owners: List[DatasetOwner],
  groups: List[DatasetGroup],
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

case class DatasetOwner(
  id: Int,
  name: String,
  image: String
)

case class DatasetGroup(
  id: Int,
  name: String,
  image: String
)

case class DatasetFile(
  id: String,
  name: String
)

case class DatasetImage(
  id: String,
  url: String
)
