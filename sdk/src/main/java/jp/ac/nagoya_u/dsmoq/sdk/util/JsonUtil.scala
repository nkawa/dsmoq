package jp.ac.nagoya_u.dsmoq.sdk.util

import jp.ac.nagoya_u.dsmoq.sdk.response._
import jp.ac.nagoya_u.dsmoq.sdk.response._
import org.json4s._
import org.json4s.jackson.JsonMethods
import org.json4s.{ DefaultFormats, Formats }
import scala.collection.JavaConverters._

object JsonUtil {
  private implicit val jsonFormats: Formats = DefaultFormats
  def toDatasets(obj: String): RangeSlice[DatasetsSummary] = {
    toObject[Response[RangeSlice[DatasetsSummary]]](obj).getData
  }
  def toDatasetOwnership(obj: String): RangeSlice[DatasetOwnership] = {
    toObject[Response[RangeSlice[DatasetOwnership]]](obj).getData
  }
  def toDatasetFiles(obj: String): RangeSlice[DatasetFile] = {
    toObject[Response[RangeSlice[DatasetFile]]](obj).getData
  }
  def toDatasetZippedFiles(obj: String): RangeSlice[DatasetZipedFile] = {
    toObject[Response[RangeSlice[DatasetZipedFile]]](obj).getData
  }
  def toDatasetGetImage(obj: String): RangeSlice[DatasetGetImage] = {
    toObject[Response[RangeSlice[DatasetGetImage]]](obj).getData
  }
  def toGroupGetImage(obj: String): RangeSlice[GroupGetImage] = {
    toObject[Response[RangeSlice[GroupGetImage]]](obj).getData
  }
  def toDataset(obj: String): Dataset = {
    toObject[Response[Dataset]](obj).getData
  }
  def toDatasetAddFiles(obj: String): DatasetAddFiles = {
    toObject[Response[DatasetAddFiles]](obj).getData
  }
  def toDatasetFile(obj: String): DatasetFile = {
    toObject[Response[DatasetFile]](obj).getData
  }
  def toDatasetAddImages(obj: String): DatasetAddImages = {
    toObject[Response[DatasetAddImages]](obj).getData
  }
  def toDatasetDeleteImage(obj: String): DatasetDeleteImage = {
    toObject[Response[DatasetDeleteImage]](obj).getData
  }
  def toDatasetOwnerships(obj: String): DatasetOwnerships = {
    toObject[Response[DatasetOwnerships]](obj).getData
  }
  def toDatasetTask(obj: String): DatasetTask = {
    toObject[Response[DatasetTask]](obj).getData
  }
  def toGroups(obj: String): RangeSlice[GroupsSummary] = {
    toObject[Response[RangeSlice[GroupsSummary]]](obj).getData
  }
  def toGroup(obj: String): Group = {
    toObject[Response[Group]](obj).getData
  }
  def toMembers(obj: String): RangeSlice[MemberSummary] = {
    toObject[Response[RangeSlice[MemberSummary]]](obj).getData
  }
  def toGroupAddImages(obj: String): GroupAddImages = {
    toObject[Response[GroupAddImages]](obj).getData
  }
  def toGroupDeleteImage(obj: String): GroupDeleteImage = {
    toObject[Response[GroupDeleteImage]](obj).getData
  }
  def toLicenses(obj: String): java.util.List[License] = {
    val licenses = toObject[Response[List[License]]](obj).getData
    licenses.asJava
  }
  def toUser(obj: String): User = {
    toObject[Response[User]](obj).getData
  }
  def toUsers(obj: String): java.util.List[User] = {
    val users = toObject[Response[List[User]]](obj).getData
    users.asJava
  }
  def toTaskStatus(obj: String): TaskStatus = {
    toObject[Response[TaskStatus]](obj).getData
  }
  def toCopiedDataset(obj: String): CopiedDataset = {
    toObject[Response[CopiedDataset]](obj).getData
  }
  def toStatistics(obj: String): java.util.List[StatisticsDetail] = {
    val statistics = toObject[Response[List[StatisticsDetail]]](obj).getData
    statistics.asJava
  }
  private def toObject[A](obj: String)(implicit m: Manifest[A]): A = JsonMethods.parse(obj).extract[A]
}
