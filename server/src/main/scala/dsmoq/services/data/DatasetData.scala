package dsmoq.services.data

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

  case class AddFilesToDatasetParams(
                                      userInfo: User,
                                      datasetId: String,
                                      files: Option[Seq[FileItem]]
                                      )
  case class ModifyDatasetFilenameParams(
                                          userInfo: User,
                                          datasetId: String,
                                          fileId: String,
                                          filename: Option[String]
                                          )
  case class DeleteDatasetFileParams(
                                      userInfo: User,
                                      datasetId: String,
                                      fileId: String
                                      )

  case class UpdateFileParams(
                               userInfo: User,
                               datasetId: String,
                               fileId: String,
                               file: Option[FileItem]
                               )

  case class ModifyDatasetMetaParams(
                                      userInfo: User,
                                      datasetId: String,
                                      name: Option[String],
                                      description: Option[String],
                                      licenseId: Option[String],
                                      attributes: Seq[(String, String)]
                                      )

  case class AddImagesToDatasetParams(
                                       userInfo: User,
                                       datasetId: String,
                                       images: Option[Seq[FileItem]]
                                       )

  case class ChangePrimaryImageParams(
                                       userInfo: User,
                                       id: Option[String],
                                       datasetId: String
                                       )

  case class DeleteImageParams(
                                userInfo: User,
                                imageId: String,
                                datasetId: String
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
    filesSize: Long,
    filesCount: Int,
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

  case class DatasetAddFiles(
                              files: Seq[DatasetFile]
                              )

  case class DatasetAddImages(
                              images: Seq[Image],
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
    organization: String,
    title: String,
    image: String,
    accessLevel: Int
  )
}
