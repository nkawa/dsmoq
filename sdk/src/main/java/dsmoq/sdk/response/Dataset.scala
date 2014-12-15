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
                      id: String,
                      filesSize: Long,
                      filesCount: Int,
                      files: Array[DatasetFile],
                      meta: DatasetMetaData,
                      images: Array[Image],
                      primaryImage: String,
                      ownerships: Array[DatasetOwnership],
                      defaultAccessLevel: Int,
                      permission: Int,
                      accessCount: Long,
                      localState: Int,
                      s3State: Int
                      )

  case class DatasetMetaData(
                              name: String,
                              description: String,
                              license : String,
                              attributes: Array[DatasetAttribute]
                              )

  case class DatasetAttribute(
                               name: String,
                               value: String
                               )

  case class DatasetAddFiles(
                              files: Array[DatasetFile]
                              )

  case class DatasetAddImages(
                               images: Array[Image],
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
                                image: String,
                                accessLevel: Int,
                                ownerType: Int
                                )

case class DatasetTask (
  private val taskId: String
) {
  def getTaskId = taskId
}
