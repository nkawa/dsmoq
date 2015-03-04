package dsmoq.sdk.response

case class User(
  private val id: String,
  private val name: String,
  private val fullname: String,
  private val organization: String,
  private val title: String,
  private val image: String,
  private val mailAddress: String,
  private val description: String,
  val isGuest: Boolean,
  val isDeleted: Boolean
) {
  def getId = id
  def getName = name
  def getFullname = fullname
  def getOrganization = organization
  def getTitle = title
  def getImage = image
  def getMailAddress = mailAddress
  def getDescription = description
}