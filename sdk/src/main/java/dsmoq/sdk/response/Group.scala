package dsmoq.sdk.response

import scala.collection.JavaConverters._

case class GroupsSummary(
  private val id: String,
  private val name: String,
  private val description: String,
  private val image: String,
  private val members: Int,
  private val datasets: Int
) {
  def getId = id
  def getName = name
  def getDescription = description
  def getImage = image
  def getMemgers = members
  def getDatasets = datasets
}

case class Group(
  private val id: String,
  private val name: String,
  private val description: String,
  private val images: Seq[Image],
  private val primaryImage: String,
  val isMember: Boolean,
  private val role: Int
) {
  def getId = id
  def getName = name
  def getDescription = description
  def getImages = images.asJava
  def getPrimaryImage = primaryImage
  def getRole = role
}

case class MemberSummary(
  private val id: String,
  private val name: String,
  private val fullname: String,
  private val organization: String,
  private val title: String,
  private val description: String,
  private val image: String,
  private val role: Int
) {
  def getId = id
  def getName = name
  def getFullname = fullname
  def getOrganization = organization
  def getTitle = title
  def getDescription = description
  def getImage = image
  def getRole = role
}

case class AddMember(
  private val id: String,
  private val name: String,
  private val organization: String,
  private val role: Int
) {
  def getId = id
  def getName = name
  def getOrganization = organization
  def getRole = role
}

case class GroupAddImages(
  private val images: Seq[Image],
  private val primaryImage: String
) {
  def getImages = images.asJava
  def getPrimaryImage = primaryImage
}

case class GroupDeleteImage(
  private val primaryImage: String
) {
  def getPrimaryImage = primaryImage
}

case class GroupGetImage (
  private val id: String,
  private val name: String,
  private val url: String,
  val isPrimary: Boolean
) {
  def getId = id
  def getName = name
  def getUrl = url
}