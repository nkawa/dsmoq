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

  case class GetGroupMembersParams(
                                    userInfo: User,
                                    groupId: String,
                                    limit: Option[String],
                                    offset: Option[String]
                                    )

  // response
  case class GroupsSummary(
                              id: String,
                              name: String,
                              description: String,
                              image: String,
                              members: Int,
                              datasets: Int
                              )

  case class Group(
                    id: String,
                    name: String,
                    description: String,
                    images: Seq[Image],
                    primaryImage: String
                    )

  case class MemberSummary(
                            id: String,
                            name: String,
                            organization: String,
                            title: String,
                            image: String,
                            role: Int
                            )
}
