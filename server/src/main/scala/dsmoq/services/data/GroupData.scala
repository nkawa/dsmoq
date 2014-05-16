package dsmoq.services.data

import org.scalatra.servlet.FileItem

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
  
  case class GetGroupDatasetsParams(
                                    userInfo: User,
                                    groupId: String,
                                    limit: Option[String],
                                    offset: Option[String]
                                    )

  case class CreateGroupParams(
                                userInfo: User,
                                name: String,
                                description: String
                                )

  case class ModifyGroupParams(
                                userInfo: User,
                                groupId: String,
                                name: String,
                                description: String
                                )

  case class AddUserToGroupParams(
                                   userInfo: User,
                                   groupId: String,
                                   userId: String,
                                   role: Int
                                   )

  case class ModifyMemberRoleParams(
                                     userInfo: User,
                                     groupId: String,
                                     memberId: String,
                                     role: Int
                                     )

  case class DeleteMemberParams(
                                 userInfo: User,
                                 groupId: String,
                                 memberId: String
                                 )

  case class DeleteGroupParams(
                                userInfo: User,
                                groupId: String
                                )

  case class AddImagesToGroupParams(
                                     userInfo: User,
                                     groupId: String,
                                     images: Option[Seq[FileItem]]
                                     )

  case class ChangeGroupPrimaryImageParams(
                                            userInfo: User,
                                            imageId: String,
                                            groupId: String
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

  case class AddMember(
                        id: String,
                        name: String,
                        organization: String,
                        role: Int
                        )
  case class GroupAddImages(
                             images: Seq[Image],
                             primaryImage: String
                             )
}
