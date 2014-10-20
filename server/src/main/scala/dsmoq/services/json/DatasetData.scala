package dsmoq.services.json

import dsmoq.services.User
import org.scalatra.servlet.FileItem

object DatasetData {
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
    permission: Int,
    accessCount: Long
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
