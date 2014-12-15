package dsmoq.sdk.util

import dsmoq.sdk.response._
import org.json4s._
import org.json4s.jackson.JsonMethods
import org.json4s.{DefaultFormats, Formats}

object JsonUtil {
  protected implicit val jsonFormats: Formats = DefaultFormats
  def toDatasets(obj: String): RangeSlice[DatasetsSummary] = {
    val response = toObject[Response[RangeSlice[DatasetsSummary]]](obj)
    response.data
  }
  def toDataset(obj: String): Dataset = {
    val response = toObject[Response[Dataset]](obj)
    response.data
  }
  def toDatasetAddFiles(obj: String): DatasetAddFiles = {
    val response = toObject[Response[DatasetAddFiles]](obj)
    response.data
  }
  def toDataseetFile(obj: String): DatasetFile = {
    val response = toObject[Response[DatasetFile]](obj)
    response.data
  }
  def toDatasetAddImages(obj: String): DatasetAddImages = {
    val response = toObject[Response[DatasetAddImages]](obj)
    response.data
  }
  def toDatasetTask(obj: String): DatasetTask = {
    val response = toObject[Response[DatasetTask]](obj)
    response.data
  }
  private def toObject[A](obj: String)(implicit m: Manifest[A]): A = JsonMethods.parse(obj).extract[A]
}
