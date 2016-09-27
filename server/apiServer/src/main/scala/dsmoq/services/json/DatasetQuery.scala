package dsmoq.services.json

case class DatasetQuery(
  id: String,
  name: String,
  query: SearchDatasetCondition
)
