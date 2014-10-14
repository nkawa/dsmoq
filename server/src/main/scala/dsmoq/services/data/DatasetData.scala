package dsmoq.services.data

import LoginData._
import org.scalatra.servlet.FileItem
import org.joda.time.DateTime

object DatasetData {
  // request
  case class SearchDatasetsParams(
    query: Option[String],
    owners: List[String],
    groups: List[String],
    attributes: List[Attribute],
    limit: Option[Int],
    offset: Option[Int]
  )

  case class Attribute(
    name: String,
    value: String
  )

  case class GetDatasetParams(
    id: String,
    userInfo: User
  )

  case class CreateDatasetParams(
    userInfo: User,
    files: Option[Seq[FileItem]]
  )

  case class AddFilesToDatasetParams(
    userInfo: User,
    datasetId: String,
    files: Option[Seq[FileItem]]
  )
  case class ModifyDatasetMetadataParams(
    datasetId: String,
    fileId: String,
    filename: Option[String] = None,
    description: Option[String] = None
  )
  case class DeleteDatasetFileParams(
    userInfo: User,
    datasetId: String,
    fileId: String
  )

  case class UpdateFileParams(
     userInfo: User,
     datasetId: String,
     fileId: String,
     file: Option[FileItem]
  )

  case class ModifyDatasetMetaParams(
    name: Option[String] = None,
    description: Option[String] = None,
    license: Option[String] = None,
    attributes: Seq[(String, String)] = Seq.empty
  )

  case class AddImagesToDatasetParams(
    userInfo: User,
    datasetId: String,
    images: Option[Seq[FileItem]]
  )

  case class ChangePrimaryImageParams(
    userInfo: User,
    id: Option[String],
    datasetId: String
  )

  case class DeleteImageParams(
    userInfo: User,
    imageId: String,
    datasetId: String
  )

  case class AccessControlParams(
    datasetId: String,
    userInfo: User,
    ids: Seq[String],
    types: Seq[String],
    accessLevels: Seq[String]
  )

  // response
  case class DatasetsSummary(
    id: String,
    name: String,
    description: String,
    image: String,
    license: Option[String] = None,
    attributes: Seq[DatasetAttribute],
    ownerships: Seq[DatasetOwnership],
    files: Long,
    dataSize: Long,
    defaultAccessLevel: Int,
    permission: Int
  )

  case class Dataset(
    id: String,
    filesSize: Long,
    filesCount: Int,
    files: Seq[DatasetFile],
    meta: DatasetMetaData,
    images: Seq[Image],
    primaryImage: String,
    ownerships: Seq[DatasetOwnership],
    defaultAccessLevel: Int,
    permission: Int
  )

  case class DatasetMetaData(
    name: String,
    description: String,
    license : String,
    attributes: Seq[DatasetAttribute]
  )

  case class DatasetAttribute(
    name: String,
    value: String
  )

  case class DatasetAddFiles(
    files: Seq[DatasetFile]
  )

  case class DatasetAddImages(
    images: Seq[Image],
    primaryImage: String
  )

  case class DatasetDeleteImage(
    primaryImage: String
  )

  case class DatasetFile(
    id: String,
    name: String,
    description: String,
    url: String,
    size: Long,
    createdBy: User,
    createdAt: String,
    updatedBy: User,
    updatedAt: String
  )

  case class DatasetOwnership (
    id: String,
    name: String,
    fullname: String,
    //organization: String,
    //title: String,
    image: String,
    accessLevel: Int,
    ownerType: Int
  )
}
