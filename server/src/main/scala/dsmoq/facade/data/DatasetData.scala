package dsmoq.facade.data

import LoginData._
import org.scalatra.servlet.FileItem
import org.joda.time.DateTime

/**
 * Created by terurou on 2014/03/20.
 */
object DatasetData {
  // request
  case class SearchDatasetsParams(
                                   query: Option[String],
                                   group: Option[String],
                                   attributes: Map[String, Seq[String]],
                                   limit: Option[String],
                                   offset: Option[String],
                                   userInfo: User
                                   )

  case class GetDatasetParams(
                               id: String,
                               userInfo: User
                               )

  case class CreateDatasetParams(
                                  userInfo: User,
                                  files: Option[Seq[FileItem]]
                                  )

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
                      files: Seq[DatasetFile],
                      meta: DatasetMetaData,
                      images: Seq[Image],
                      primaryImage: String,
                      ownerships: Seq[DatasetOwnership],
                      defaultAccessLevel: Int,
                      permission: Int
                      )

  case class DatasetMetaData(
                              name: String,
                              description: String,
                              license : Option[String],
                              attributes: Seq[DatasetAttribute]
                              )

  case class DatasetAttribute(
                               name: String,
                               value: String
                               )

  case class DatasetFile(
    id: String,
    name: String,
    description: String,
    url: String,
    size: Long,
    createdBy: User,
    createdAt: DateTime,
    updatedBy: User,
    updatedAt: DateTime
  )

  case class DatasetOwnership (
    id: String,
    name: String,
    fullname: String,
    organization: String,
    title: String,
    image: String,
    accessLevel: Int
  )
}
