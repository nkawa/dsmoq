package com.constructiveproof.example.facade

import com.constructiveproof.example.traits.SessionUserInfo
import scala.util.{Failure, Try, Success}
import org.scalatra.servlet.FileItem
import scalikejdbc._, SQLInterpolation._
import java.util.{Properties, UUID}
import java.nio.file.Paths
import java.io.{FileInputStream, File}
import com.constructiveproof.example.AppConf

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
    val ownerships = List(DatasetOwnership("owner_id", 1, "ownership_name", "http://xxxx"))
    val result = DatasetsResult(
      id = 1,
      name = name,
      description = "xxxx",
      image = "http://xxx",
      attributes = attributes,
      ownerships = ownerships,
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

    // データ取得
    // FIXME 権限を考慮したデータの取得
    val dataset = DB readOnly { implicit session =>
      sql"""
        SELECT DISTINCT
          datasets.*
        FROM
          datasets
        WHERE
          datasets.id = UUID(${params.id})
      """.map(_.toMap()).single().apply()
    } match {
      case Some(x) => x
      case None =>
        // FIXME データがない時の処理(現状は例外)
        return Failure(new Exception("No Dataset"))
    }

    // FIXME SELECTで取得するデータの指定(必要であれば)
    val attributes = DB readOnly { implicit session =>
      sql"""
      SELECT
        v.*, k.name
      FROM
        attribute_values AS v
      INNER JOIN
        attribute_keys AS k
      ON
        v.attribute_key_id = k.id
      WHERE
        v.dataset_id = UUID(${params.id})
      """.map(_.toMap()).list().apply()
    }

    // FIXME SELECTで取得するデータの指定(必要であれば)
    // FIXME 権限の仕様検討中のため、変わる可能性あり
    // LEFT JOIN：ユーザー/グループに画像が設定されていない可能性を考慮
    val ownerships = DB readOnly { implicit session =>
      sql"""
        SELECT
          ownerships.*, users.fullname AS name
        FROM
          ownerships
        INNER JOIN
          users
        ON
          users.id = ownerships.owner_id AND owner_type = 1
        WHERE
          dataset_id = UUID(${params.id})
        UNION
        SELECT
          ownerships.*, groups.name AS name
        FROM
          ownerships
        INNER JOIN
          groups
        ON
          groups.id = ownerships.owner_id AND owner_type = 2
        WHERE
          dataset_id = UUID(${params.id})
      """.map(_.toMap()).list().apply()
    }

    // FIXME SELECTで取得するデータの指定(必要であれば)
    val files = DB readOnly { implicit session =>
      sql"""
        SELECT
          files.*, file_histories.file_path
        FROM
          files
        INNER JOIN
          file_histories
        ON
          files.id = file_histories.file_id
        WHERE
          files.dataset_id = UUID(${params.id})
      """.map(_.toMap()).list().apply()
    }

    // FIXME SELECTで取得するデータの指定(必要であれば)
    val images = DB readOnly { implicit session =>
      sql"""
        SELECT
          images.*, relation.is_primary, relation.show_order
        FROM
          images
        INNER JOIN
          dataset_image_relation AS relation
        ON
          images.id = relation.image_id
        WHERE relation.dataset_id = UUID(${params.id})
      """.map(_.toMap()).list().apply()
    }

    // FIXME download用のURL、アップロードしたユーザー(先に）、ファイルサイズ(historyにカラム追加)、ファイルの説明(あとで)が必要になる(権限は見る？)
    // アップロードユーザーはUUID返す？それともユーザー情報？→聞く
    val datasetFiles = files.map { x =>
      DatasetFile(
        x("id").toString,
        x("name").toString
      )
    }
    val datasetMetaData = DatasetMetaData(
      dataset("name").toString,
      dataset("description").toString,
      1,
      attributes.map{ x =>
        DatasetAttribute(
          x("name").toString,
          x("val").toString
        )
      }
    )
    val datasetImages = images.map { x =>
      DatasetImage(
        x("id").toString,
        x("file_path").toString
      )
    }
    // FIXME 画像URLはダミー
    val datasetOwnerships = ownerships.map { x =>
      DatasetOwnership(
        x("id").toString,
        x("owner_type").toString.toInt,
        x("name").toString,
        "http://dummy"
      )
    }

    Success(Dataset(
      id = dataset("id").toString,
      files = datasetFiles,
      meta = datasetMetaData,
      images = datasetImages,
      primaryImage =  primaryImage,
      ownerships = datasetOwnerships,
      defaultAccessLevel = dataset("default_access_level").toString.toInt,
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
    val basePath = AppConf.fileDir

    // 後で使用するデータセットの画像保存用のIDと画像情報をタプルとして管理
    val datasetFiles = for {
      f <- files
    } yield {
      DatasetFile(f._1, f._2.name)
    }

    // データセットの初期メタデータは適当に作る(名前のみ)
    val datasetID = UUID.randomUUID().toString
    val name = "dataset_" + files(0)._2.name
    // FIXME ライセンスIDの指定
    val licenseID = UUID.randomUUID().toString
    val datasetMetaData = DatasetMetaData(name, "", 1, List())

    // ユーザーの画像情報はDBから引く
    val imageInfo = DB readOnly { implicit session =>
      sql"""
        SELECT
          images.*
        FROM
          users
        INNER JOIN
          images
        ON
          users.image_id = images.id
        WHERE
          users.id = UUID(${params.userInfo.id})
      """.map(_.toMap).single.apply()
    } match {
      case Some(x) => x
      // FIXME ユーザーのイメージがないときの処理(現状は例外)
      case None => return Failure(new Exception("No Files"))
    }
    val datasetOwnerships = List(DatasetOwnership(
      params.userInfo.id,
      1,
      params.userInfo.name,
      imageInfo("file_path").toString
    ))

    DB localTx { implicit session =>
      // save dataset
      sql"""
        INSERT INTO datasets
          (id, name, description, license_id, default_access_level,
           created_by, updated_by)
        VALUES
          (UUID(${datasetID}), ${name}, '', UUID(${licenseID}), 0,
           UUID(${params.userInfo.id}), UUID(${params.userInfo.id}))
      """.update().apply()

      // save files
      files.foreach{x =>
        // FIXME file_type, file_attributesの指定があれば
        val fileDescription = "file_description_" + x._1
        val filetype = 0
        val fileAttributes = "{}"
        sql"""
          INSERT INTO files
            (id, dataset_id, name, description, file_type, file_attributes,
             created_by, updated_by)
          VALUES
            (UUID(${x._1}), UUID(${datasetID}), ${x._2.name}, ${fileDescription}, ${filetype}, JSON(${fileAttributes}),
             UUID(${params.userInfo.id}), UUID(${params.userInfo.id}))
        """.update().apply()

        val filePath = Paths.get(datasetID, x._1 + '.' + x._2.name.split('.').last).toString
        sql"""
        INSERT INTO file_histories
          (file_id, file_path, created_by, updated_by)
        VALUES
          (UUID(${x._1}), ${filePath}, UUID(${params.userInfo.id}), UUID(${params.userInfo.id}))
       """.update().apply()
      }

      // save ownerships onwer_type=1(user)
      sql"""
      INSERT INTO ownerships
        (dataset_id, owner_type, owner_id, access_level, created_by, updated_by)
      VALUES
        (UUID(${datasetID}), 1, UUID(${params.userInfo.id}), 3, UUID(${params.userInfo.id}), UUID(${params.userInfo.id}))
      """.update().apply()

      // ファイル保存パス：設定パス + /datasetID/ + fileID
      files.foreach {x =>
        val path = Paths.get(basePath, datasetID, x._1 + '.' + x._2.name.split('.').last)
        if (!path.toFile.getParentFile.exists()) {
          path.toFile.getParentFile.mkdir()
        }
        x._2.write(path.toFile)
      }
    }

    Success(Dataset(
      id = datasetID,
      files = datasetFiles,
      meta = datasetMetaData,
      images = List(),
      primaryImage =  null,
      ownerships = datasetOwnerships,
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
  ownerships: List[DatasetOwnership],
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