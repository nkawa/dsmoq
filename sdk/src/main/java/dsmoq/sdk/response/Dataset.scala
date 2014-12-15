package dsmoq.sdk.response

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
  private val ownerships: Seq[DatasetOwnership],
  private val defaultAccessLevel: Int,
  private val permission: Int,
  private val accessCount: Long,
  private val localState: Int,
  private val s3State: Int
) {
  def getId = id
  def getFilesSize = filesSize
  def getFilesCount = filesCount
  def getFiles = files.asJava
  def getMeta = meta
  def getImages = images.asJava
  def getPrimaryImage = primaryImage
  def getOwnerShips = ownerships.asJava
  def getDefaultAccessLevel = defaultAccessLevel
  def getPermission = permission
  def getAccessCount = accessCount
  def getLocalState = localState
  def getS3State = s3State
}

case class DatasetMetaData(
  private val name: String,
  private val description: String,
  private val license : String,
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
  private val url: String,
  private val size: Long,
  private val createdBy: User,
  private val createdAt: String,
  private val updatedBy: User,
  private val updatedAt: String
) {
  def getId = id
  def getName = name
  def getDescroption = description
  def getUrl = url
  def getSize = size
  def getCreatedBy = createdBy
  def getCreatedAt = createdAt
  def getUpdatedBy = updatedBy
  def getUpdatedAt = updatedAt
}

case class DatasetOwnership (
  private val id: String,
  private val name: String,
  private val fullname: String,
  private val image: String,
  private val accessLevel: Int,
  private val ownerType: Int
) {
  def getId = id
  def getName = name
  def getFullName = fullname
  def getImage = image
  def getAccessLevel = accessLevel
  def getOwnerType = ownerType
}

case class DatasetTask (
  private val taskId: String
) {
  def getTaskId = taskId
}

case class DatasetOwnerships (
  private val ownerships: Seq[DatasetOwnership]
) {
  def getOwnerships = ownerships.asJava
}