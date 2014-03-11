package com.constructiveproof.example.facade

object DatasetFacade {
  def searchDatasets(params: DatasetCatalogParams) = {
    // FIXME dummy
    val summary = DatasetSummary(100)
    val attributes = List(DatasetAttribute("xxx", "xxx"))
    val owners = List(DatasetOwner(1, "xxx", "http://xxx"))
    val groups = List(DatasetGroup(1, "xxx", "http://xxx"))
    val result = DatasetResult(
      id = 1,
      name = "xxxx",
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
}

// request
case class DatasetCatalogParams(
  query: Option[String],
  group: Option[String],
  attributes: Map[String, Seq[String]],
  limit: Option[String],
  offset: Option[String]
)

// response
case class Datasets(
  summary: DatasetSummary,
  results: List[DatasetResult]
)

case class DatasetSummary(
  total: Int,
  count: Int = 20,
  offset: Int = 0
)

case class DatasetResult(
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
