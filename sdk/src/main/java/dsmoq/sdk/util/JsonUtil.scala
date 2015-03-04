package dsmoq.sdk.util

import dsmoq.sdk.response._
import org.json4s._
import org.json4s.jackson.JsonMethods
import org.json4s.{DefaultFormats, Formats}
import scala.collection.JavaConverters._

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
  def toDatasetFile(obj: String): DatasetFile = {
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
  def toGroupAddImages(obj: String): GroupAddImages = {
    statusCheck(obj)
    toObject[Response[GroupAddImages]](obj).getData
  }
  def toGroupDeleteImage(obj: String): GroupDeleteImage = {
    statusCheck(obj)
    toObject[Response[GroupDeleteImage]](obj).getData
  }
  def toLicenses(obj: String): java.util.List[License] = {
    statusCheck(obj)
    val licenses = toObject[Response[List[License]]](obj).getData
    licenses.asJava
  }
  def toUser(obj: String): User = {
    statusCheck(obj)
    toObject[Response[User]](obj).getData
  }
  def toUsers(obj: String): java.util.List[User] = {
    statusCheck(obj)
    val users = toObject[Response[List[User]]](obj).getData
    users.asJava
  }
  def toTaskStatus(obj: String): TaskStatus = {
    statusCheck(obj)
    toObject[Response[TaskStatus]](obj).getData
  }
  private def toResponse(obj: String): Response[NoData] = {
    toObject[Response[NoData]](obj)
  }
  def statusCheck(obj: String): Unit = {
      toResponse(obj).getStatus match {
      case "Unauthorized" => throw new NotAuthorizedException()
      case "NotFound" => throw new NotFoundException()
      case "BadRequest" => {
        val response = toObject[Response[List[InputValidationErrorJson]]](obj)
        val errors = response.getData.map(x => new InputValidationError(x.name, x.message)).asJava
        throw new InputValidationException(errors)
      }
      case "NG" => throw new ApiFailedException()
      case _ => // do nothing
    }
  }
  private case class NoData()
  private case class InputValidationErrorJson(
    name: String,
    message: String
  )
  private def toObject[A](obj: String)(implicit m: Manifest[A]): A = JsonMethods.parse(obj).extract[A]
}
