package dsmoq.sdk.util

import dsmoq.sdk.response._
import org.json4s._
import org.json4s.jackson.JsonMethods
import org.json4s.{DefaultFormats, Formats}

object JsonUtil {
  private implicit val jsonFormats: Formats = DefaultFormats
  def toDatasets(obj: String): RangeSlice[DatasetsSummary] = {
    statusCheck(obj)
   toObject[Response[RangeSlice[DatasetsSummary]]](obj).getData
  }
  def toDataset(obj: String): Dataset = {
    statusCheck(obj)
    toObject[Response[Dataset]](obj).getData
  }
  def toDatasetAddFiles(obj: String): DatasetAddFiles = {
    statusCheck(obj)
    toObject[Response[DatasetAddFiles]](obj).getData
  }
  def toDataseetFile(obj: String): DatasetFile = {
    statusCheck(obj)
    toObject[Response[DatasetFile]](obj).getData
  }
  def toDatasetAddImages(obj: String): DatasetAddImages = {
    statusCheck(obj)
    toObject[Response[DatasetAddImages]](obj).getData
  }
  def toDatasetDeleteImage(obj: String): DatasetDeleteImage = {
    statusCheck(obj)
    toObject[Response[DatasetDeleteImage]](obj).getData
  }
  def toDatasetOwnerships(obj: String): DatasetOwnerships = {
    statusCheck(obj)
    toObject[Response[DatasetOwnerships]](obj).getData
  }
  def toDatasetTask(obj: String): DatasetTask = {
    statusCheck(obj)
    toObject[Response[DatasetTask]](obj).getData
  }
  def toGroups(obj: String): RangeSlice[GroupsSummary] = {
    statusCheck(obj)
    toObject[Response[RangeSlice[GroupsSummary]]](obj).getData
  }
  def toGroup(obj: String): Group = {
    statusCheck(obj)
    toObject[Response[Group]](obj).getData
  }
  def toMembers(obj: String): RangeSlice[MemberSummary] = {
    statusCheck(obj)
    toObject[Response[RangeSlice[MemberSummary]]](obj).getData
  }
  private def toResponse(obj: String): Response[NoData] = {
    toObject[Response[NoData]](obj)
  }
  def statusCheck(obj: String): Unit = {
    val response = toResponse(obj)
    if (response.getStatus != "OK") throw new ApiFailedException(response.getStatus)
  }
  private case class NoData()
  private def toObject[A](obj: String)(implicit m: Manifest[A]): A = JsonMethods.parse(obj).extract[A]
}
