package com.constructiveproof.example.facade

import com.constructiveproof.example.traits.SessionUserInfo
import scala.util.{Failure, Try, Success}
import org.scalatra.servlet.FileItem
import scalikejdbc._, SQLInterpolation._
import java.util.{Properties, UUID}
import java.nio.file.Paths
import java.io.{FileInputStream, File}

object DatasetFacade {
  def searchDatasets(params: SearchDatasetsParams): Try[Datasets] = {
    // FIXME dummy セッションデータ有無チェック
    val name = if (params.userInfo.isGuest) {
      "guest"
    } else {
      "user"
    }

    // FIXME dummy
    val summary = DatasetsSummary(100)
    val attributes = List(DatasetAttribute("xxx", "xxx"))
    val owners = List(DatasetsOwner(1, "xxx", "http://xxx"))
    val groups = List(DatasetsGroup(1, "xxx", "http://xxx"))
    val result = DatasetsResult(
      id = 1,
      name = name,
      description = "xxxx",
      image = "http://xxx",
      attributes = attributes,
      owners = owners,
      groups = groups,
      files = 3,
      dataSize = 1024,
      defaultAccessLevel = 1,
      permission = 1
    )
    Success(Datasets(summary, List(result)))
  }

  def getDataset(params: GetDatasetParams): Try[Dataset] = {
    // FIXME dummy セッションデータ有無チェック
    val primaryImage = if (params.userInfo.isGuest) {
      "primaryImage guest"
    } else {
      "primaryImage user"
    }

    // FIXME dummy data
    val datasetFiles = List(DatasetFile("1", "filename"))
    val datasetMetaData = DatasetMetaData(
      "metadataName",
      "metadataDescription",
      1,
      List(DatasetAttribute("attr_name", "attr_value"))
    )
    val datasetImages = List(DatasetImage("image_id", "http://xxx"))
    val datasetOwnerships = List(DatasetOwnership("owner_id", 1, "ownership_name", "http://xxxx"))

    Success(Dataset(
      id = params.id,
      files = datasetFiles,
      meta = datasetMetaData,
      images = datasetImages,
      primaryImage =  primaryImage,
      ownerships = datasetOwnerships,
      defaultAccessLevel = 1,
      permission = 1
    ))
  }

  def createDataset(params: CreateDatasetParams): Try[Dataset] = {
    // FIXME ログインユーザーかどうかの処理(現状は例外)
    if (params.userInfo.isGuest) {
      return Failure(new Exception("No Session"))
    }

    val files = params.files match {
      case Some(x) =>
        for {
          f <- x
          if f.size != 0 && f.name != null
        } yield {
          Pair(UUID.randomUUID().toString, f)
        }
      case None =>
        // FIXME ファイルが空の時の処理(現状は例外)
        return Failure(new Exception("No Files"))
    }
    // FIXME ファイルがないときの処理(現状は例外)
    if (files.size == 0) {
      return Failure(new Exception("No Files"))
    }

    // FIXME ファイルパス読み込み 外部ライブラリ使うか要検討
    // properties使ったやっつけ実装
    val basePath = {
      val properties = new Properties()
      properties.load(new FileInputStream("./src/main/resources/conf.properties"))
      properties.get("file_root").toString
    }

    // 後のデータセットの画像保存用のIDと画像情報をタプルとして管理
    val datasetFiles = for {
      f <- files
    } yield {
      DatasetFile(f._1, f._2.name)
    }

    // データセットの初期メタデータは適当に作る
    val datasetID = UUID.randomUUID().toString
    val name = "dataset_" + files(0)._2.name
    val description = "dataset_description_" + files(0)._1
    // FIXME ライセンスIDの指定
    val licenseID = UUID.randomUUID().toString
    val datasetAttributes = List(DatasetAttribute("file_name", files(0)._2.name))
    val datasetMetaData = DatasetMetaData(name, description, 1, datasetAttributes)

    DB localTx { implicit session =>
      // save dataset
      sql"""
        INSERT INTO datasets
          (id, name, description, license_id, default_access_level)
        VALUES
          (UUID(${datasetID}), ${name}, ${description}, UUID(${licenseID}), 0)
      """.update().apply()

      // save attributes
      datasetAttributes.foreach {x =>
        val attributeKeyID = UUID.randomUUID().toString
        sql"""
          INSERT INTO attribute_keys (id, name)
          VALUES (UUID(${attributeKeyID}), ${x.name})
        """.update().apply()

        sql"""
        INSERT INTO attribute_values
          (attribute_key_id, dataset_id, val)
        VALUES
          (UUID(${attributeKeyID}), UUID(${datasetID}), ${x.value})
        """.update().apply()
      }

      // save files
      files.foreach{x =>
        // FIXME file_type, file_attributesの指定があれば
        val fileDescription = "file_description_" + x._1
        val filetype = 0
        val fileAttributes = "{}"
        sql"""
          INSERT INTO files
            (id, dataset_id, name, description, file_type, file_attributes)
          VALUES
            (UUID(${x._1}), UUID(${datasetID}), ${x._2.name}, ${fileDescription}, ${filetype}, JSON(${fileAttributes}))
        """.update().apply()

        sql"""
        INSERT INTO file_histories
          (file_id, file_path)
        VALUES
          (UUID(${x._1}), ${basePath + x._2.name})
       """.update().apply()
      }

      // save ownerships onwer_type=1(user)
      sql"""
      INSERT INTO ownership
        (dataset_id, owner_type, owner_id, access_level)
      VALUES
        (UUID(${datasetID}), 1, UUID(${params.userInfo.id}), 3)
      """.update().apply()

      // ファイル保存パス：設定パス + /datasetID/ + fileID
      files.foreach {x =>
        val path = Paths.get(basePath, datasetID, x._1)
        if (!path.toFile.getParentFile.exists()) {
          path.toFile.getParentFile.mkdir()
        }
        x._2.write(path.toFile)
      }
    }

    // FIXME 暫定
    val datasetOwnerships = List(DatasetOwnership("owner_id", 1, "ownership_name", "http://xxxx"))

    Success(Dataset(
      id = datasetID,
      files = datasetFiles,
      meta = datasetMetaData,
      images = List(),
      primaryImage =  null,
      ownerships = List(),
      defaultAccessLevel = 0,
      permission = 3
    ))
  }
}

// request
case class SearchDatasetsParams(
  query: Option[String],
  group: Option[String],
  attributes: Map[String, Seq[String]],
  limit: Option[String],
  offset: Option[String],
  userInfo: User
) extends SessionUserInfo

case class GetDatasetParams(
  id: String,
  userInfo: User
) extends SessionUserInfo

case class CreateDatasetParams(
  userInfo: User,
  files: Option[Seq[FileItem]]
)

// response
case class Datasets(
  summary: DatasetsSummary,
  results: List[DatasetsResult]
)

case class DatasetsSummary(
  total: Int,
  count: Int = 20,
  offset: Int = 0
)

case class DatasetsResult(
  id: Int,
  name: String,
  description: String,
  image: String,
  license: Int = 1,
  attributes: List[DatasetAttribute],
  owners: List[DatasetsOwner],
  groups: List[DatasetsGroup],
  files: Int,
  dataSize: Int,
  defaultAccessLevel: Int,
  permission: Int
)

case class DatasetsOwner(
  id: Int,
  name: String,
  image: String
)

case class DatasetsGroup(
  id: Int,
  name: String,
  image: String
)

case class Dataset(
  id: String,
  files: Seq[DatasetFile],
  meta: DatasetMetaData,
  images: List[DatasetImage],
  primaryImage: String,
  ownerships: List[DatasetOwnership],
  defaultAccessLevel: Int,
  permission: Int
)

case class DatasetMetaData(
  name: String,
  description: String,
  license : Int,
  attributes: List[DatasetAttribute]
)

case class DatasetAttribute(
  name: String,
  value: String
)

case class DatasetFile(
  id: String,
  name: String
)

case class DatasetImage(
  id: String,
  url: String
)

case class DatasetOwnership(
  id: String,
  ownerType: Int,
  name: String,
  image: String
)