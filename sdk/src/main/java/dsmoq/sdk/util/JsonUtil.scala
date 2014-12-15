package dsmoq.sdk.util

import dsmoq.sdk.response.{DatasetTask, DatasetsSummary, Response, RangeSlice}
import dsmoq.sdk.response.DatasetTask
import dsmoq.sdk.response.Response
import org.json4s._
import org.json4s.jackson.JsonMethods
import org.json4s.{DefaultFormats, Formats}

object JsonUtil {
  protected implicit val jsonFormats: Formats = DefaultFormats
  def toDataset(obj: String): RangeSlice[DatasetsSummary] = {
    val response = toObject[Response[RangeSlice[DatasetsSummary]]](obj)
    response.data
  }
  def toDatasetTask(obj: String): DatasetTask = {
    val response = toObject[Response[DatasetTask]](obj)
    response.data
  }
  private def toObject[A](obj: String)(implicit m: Manifest[A]): A = JsonMethods.parse(obj).extract[A]
}
