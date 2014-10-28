package dsmoq.services.json

import dsmoq.services.User
import org.scalatra.servlet.FileItem

object GroupData {
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
                    primaryImage: String,
                    isMember: Boolean,
                    role: Int
                    )

  case class MemberSummary(
    id: String,
    name: String,
    fullname: String,
    organization: String,
    title: String,
    description: String,
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

  case class GroupDeleteImage(
                               primaryImage: String
                               )
}
