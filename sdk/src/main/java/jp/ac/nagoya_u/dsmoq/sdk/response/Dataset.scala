package jp.ac.nagoya_u.dsmoq.sdk.response

import java.util.Optional

import scala.collection.JavaConverters._

case class DatasetsSummary(
  private val id: String,
  private val name: String,
  private val description: String,
  private val image: String,
  private val license: Option[String],
  private val attributes: Seq[DatasetAttribute],
  private val ownerships: Seq[DatasetOwnership],
  private val files: Long,
  private val dataSize: Long,
  private val defaultAccessLevel: Int,
  private val permission: Int,
  private val localState: Int,
  private val s3State: Int
) {
  def getId = id
  def getName = name
  def getDescription = description
  def getImage = image
  def getLicense = license match {
    case Some(x) => Optional.of(x)
    case None => Optional.empty()
  }
  def getAttributes = attributes.asJava
  def getOwnerships = ownerships.asJava
  def getFiles = files
  def getDataSize = dataSize
  def getDefaultAccessLevel = defaultAccessLevel
  def getPermission = permission
  def getLocalState = localState
  def getS3State = s3State
}

case class Dataset(
  private val id: String,
  private val filesSize: Long,
  private val filesCount: Int,
  private val files: Seq[DatasetFile],
  private val meta: DatasetMetaData,
  private val images: Seq[Image],
  private val primaryImage: String,
  private val featuredImage: String,
  private val ownerships: Seq[DatasetOwnership],
  private val defaultAccessLevel: Int,
  private val permission: Int,
  private val accessCount: Long,
  private val localState: Int,
  private val s3State: Int,
  private val fileLimit: Int
) {
  def getId = id
  def getFilesSize = filesSize
  def getFilesCount = filesCount
  def getFiles = files.asJava
  def getMeta = meta
  def getImages = images.asJava
  def getPrimaryImage = primaryImage
  def getFeaturedImage = featuredImage
  def getOwnerShips = ownerships.asJava
  def getDefaultAccessLevel = defaultAccessLevel
  def getPermission = permission
  def getAccessCount = accessCount
  def getLocalState = localState
  def getS3State = s3State
  def getFileLimit = fileLimit
}

case class DatasetMetaData(
  private val name: String,
  private val description: String,
  private val license: String,
  private val attributes: Seq[DatasetAttribute]
) {
  def getName = name
  def getDescription = description
  def getLicense = license
  def getAttributes = attributes.asJava
}

case class DatasetAttribute(
  private val name: String,
  private val value: String
) {
  def getName = name
  def getValue = value
}

case class DatasetAddFiles(
  private val files: Seq[DatasetFile]
) {
  def getFiles = files.asJava
}

case class DatasetAddImages(
  private val images: Seq[Image],
  private val primaryImage: String
) {
  def getImages = images.asJava
  def getPrimaryImage = primaryImage
}

case class DatasetDeleteImage(
  private val primaryImage: String
) {
  def getPrimaryImage = primaryImage
}

case class DatasetFile(
  private val id: String,
  private val name: String,
  private val description: String,
  private val url: Option[String],
  private val size: Option[Long],
  private val createdBy: Option[User],
  private val createdAt: String,
  private val updatedBy: Option[User],
  private val updatedAt: String,
  val isZip: Boolean,
  private val zipedFiles: Seq[DatasetZipedFile],
  private val zipCount: Int
) {
  def getId = id
  def getName = name
  def getDescription = description
  def getUrl = url.orNull
  def getSize = size.getOrElse(-1)
  def getCreatedBy = createdBy.orNull
  def getCreatedAt = createdAt
  def getUpdatedBy = updatedBy.orNull
  def getUpdatedAt = updatedAt
  def getZipedFiles = zipedFiles.asJava
  def getZipCount = zipCount
}

case class DatasetZipedFile(
  private val id: String,
  private val name: String,
  private val size: Option[Long],
  private val url: Option[String]
) {
  def getId = id
  def getName = name
  def getSize = size.getOrElse(-1)
  def getUrl = url.orNull
}

case class DatasetOwnership(
  private val id: String,
  private val name: String,
  private val fullname: String,
  private val organization: String,
  private val title: String,
  private val description: String,
  private val image: String,
  private val accessLevel: Int,
  private val ownerType: Int
) {
  def getId = id
  def getName = name
  def getFullName = fullname
  def getOrganization = organization
  def getTitle = title
  def getDescription = description
  def getImage = image
  def getAccessLevel = accessLevel
  def getOwnerType = ownerType
}

case class DatasetTask(
  private val taskId: String
) {
  def getTaskId = taskId
}

case class DatasetOwnerships(
  private val ownerships: Seq[DatasetOwnership]
) {
  def getOwnerships = ownerships.asJava
}

case class CopiedDataset(
  private val datasetId: String
) {
  def getDatasetId = datasetId
}

case class DatasetGetImage(
  private val id: String,
  private val name: String,
  private val url: String,
  val isPrimary: Boolean
) {
  def getId = id
  def getName = name
  def getUrl = url
}
