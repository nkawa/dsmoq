package dsmoq.services.data

object GroupData {
  // request
  case class SearchGroupsParams(
                                 userInfo: User,
                                 query: Option[String],
                                 limit: Option[String],
                                 offset: Option[String]
                                 )
  case class GetGroupParams(
                             userInfo: User,
                             groupId: String
                             )

  // response
  case class Group(
                    id: String,
                    name: String,
                    description: String,
                    images: Seq[Image],
                    primaryImage: String
                    )
}
