package dsmoq.facade

import scala.util.{Failure, Try, Success}
import org.scalatra.servlet.FileItem
import scalikejdbc._, SQLInterpolation._
import java.util.{Properties, UUID}
import java.nio.file.Paths
import java.io.{FileInputStream, File}
import dsmoq.AppConf
import dsmoq.traits.SessionUserInfo

import dsmoq.models.PostgresqlHelper._

object DatasetFacade {

  def search(params: SearchDatasetsParams): Try[Datasets] = {
    val offset = params.offset.getOrElse("0").toInt
    val limit = params.limit.getOrElse("20").toInt

    DB readOnly { implicit s =>
      val groups = getJoinedGroups(params.userInfo)
      val count = countDatasets(groups)

      val summary = DatasetsSummary(count, limit, offset)
      val results = if (count > offset) {
        val datasets = findDatasets(groups, limit, offset)
        val datasetIds = datasets.map(_._1.id)

        val owners = getOwners(datasetIds)
        val guestAccessLevels = getGuestAccessLevel(datasetIds)
        datasets.map(x => {
          val ds = x._1
          val permission = x._2

          DatasetsResult(
            id = ds.id,
            name = ds.name,
            description = ds.description,
            image = "http://xxx",
            attributes = List.empty, //TODO
            ownerships = List.empty, //TODO
            files = 0, //TODO
            dataSize = 1024, //TODO
            defaultAccessLevel = guestAccessLevels.get(ds.id).getOrElse(0),
            permission = permission
          )
        })
      } else {
        List.empty
      }
      Success(Datasets(summary, results))
    }
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

    // FIXME ファイルサイズ、ユーザー情報は暫定
    val datasetFiles = files.map { x =>
      DatasetFile(
        id = x("id").toString,
        name = x("name").toString,
        description = x("description").toString,
        url = x("file_path").toString,
        fileSize = 1024,
        user = x("created_by").toString
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

    val datasetID = UUID.randomUUID().toString
    // データセットのファイル情報と画像情報をタプルとして管理
    // FIXME ユーザー情報は暫定的にIDを格納
    val files = params.files match {
      case Some(x) =>
        for {
          f <- x
          if f.size != 0 && f.name != null
        } yield {
          val fileID = UUID.randomUUID().toString
          val datasetFile = DatasetFile(
            id = fileID,
            name = f.name,
            description = "file_description_" + fileID,
            url = Paths.get(datasetID, fileID + '.' + f.name.split('.').last).toString,
            fileSize = f.size,
            user = params.userInfo.id
          )
          Pair(datasetFile, f)
        }
      case None =>
        // FIXME ファイルが空の時の処理(現状は例外)
        return Failure(new Exception("No Files"))
    }
    // FIXME ファイルがないときの処理(現状は例外)
    if (files.size == 0) {
      return Failure(new Exception("No Files"))
    }

    // データセットの初期メタデータは適当に作る(名前のみ)
    val datasetName = "dataset_" + files(0)._1.name
    // FIXME ライセンスIDの指定
    val licenseID = UUID.randomUUID().toString
    val datasetMetaData = DatasetMetaData(datasetName, "", 1, List())

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

    // FIXME ファイルパス読み込み 外部ライブラリ使うか要検討
    val basePath = AppConf.fileDir

    DB localTx { implicit session =>
      // save dataset
      sql"""
        INSERT INTO datasets
          (id, name, description, license_id, default_access_level,
           created_by, updated_by)
        VALUES
          (UUID(${datasetID}), ${datasetName}, '', UUID(${licenseID}), 0,
           UUID(${params.userInfo.id}), UUID(${params.userInfo.id}))
      """.update().apply()

      // save files
      files.foreach{x =>
        // FIXME file_type, file_attributesの指定があれば
        val fileType = 0
        val fileAttributes = "{}"
        sql"""
          INSERT INTO files
            (id, dataset_id, name, description, file_type, file_attributes,
             created_by, updated_by)
          VALUES
            (UUID(${x._1.id}), UUID(${datasetID}), ${x._1.name}, ${x._1.description}, ${fileType}, JSON(${fileAttributes}),
             UUID(${params.userInfo.id}), UUID(${params.userInfo.id}))
        """.update().apply()

        sql"""
        INSERT INTO file_histories
          (file_id, file_path, created_by, updated_by)
        VALUES
          (UUID(${x._1.id}), ${x._1.url}, UUID(${params.userInfo.id}), UUID(${params.userInfo.id}))
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
        val path = Paths.get(basePath, x._1.url)
        if (!path.toFile.getParentFile.exists()) {
          path.toFile.getParentFile.mkdir()
        }
        x._2.write(path.toFile)
      }
    }

    Success(Dataset(
      id = datasetID,
      files = files.map(_._1),
      meta = datasetMetaData,
      images = List(),
      primaryImage =  null,
      ownerships = datasetOwnerships,
      defaultAccessLevel = 0,
      permission = 3
    ))
  }

  private def countDatasets(groups : Seq[String])(implicit s: DBSession) = {
    val ds = dsmoq.models.Dataset.syntax("ds")
    val o = dsmoq.models.Ownership.syntax("o")
    withSQL {
      select(sqls.count(sqls.distinct(ds.id)).append(sqls"count"))
        .from(dsmoq.models.Dataset as ds)
        .innerJoin(dsmoq.models.Ownership as o).on(o.datasetId, ds.id)
        .where
          .inByUuid(o.ownerId, groups)
          .and
          .gt(o.accessLevel, 0)
          .and
          .isNull(ds.deletedAt)
          .and
          .isNull(o.deletedAt)
    }.map(implicit rs => rs.int("count")).single().apply().get
  }

  private def getJoinedGroups(user: User)(implicit s: DBSession) = {
    Seq.concat(
      Seq(AppConf.guestGroupId),
      if (user.isGuest) {
        Seq.empty
      } else {
        val g = dsmoq.models.Group.syntax("g")
        val m = dsmoq.models.Member.syntax("m")
        withSQL {
          select(g.id)
            .from(dsmoq.models.Group as g)
            .innerJoin(dsmoq.models.Member as m).on(m.groupId, g.id)
            .where
              .eq(m.userId, sqls.uuid(user.id))
              .and
              .isNull(g.deletedAt)
              .and
              .isNull(m.deletedAt)
        }.map(_.string("id")).list().apply()
      }
    )
  }

  private def findDatasets(groups: Seq[String], limit: Int, offset: Int)(implicit s: DBSession) = {
    val ds = dsmoq.models.Dataset.syntax("ds")
    val o = dsmoq.models.Ownership.syntax("o")
    withSQL {
      select(ds.result.*, sqls.max(o.accessLevel).append(sqls"access_level"))
        .from(dsmoq.models.Dataset as ds)
        .innerJoin(dsmoq.models.Ownership as o).on(ds.id, o.datasetId)
        .where
          .inByUuid(o.ownerId, groups)
          .and
          .gt(o.accessLevel, 0)
          .and
          .isNull(ds.deletedAt)
          .and
          .isNull(o.deletedAt)
        .groupBy(ds.*)
        .orderBy(ds.updatedAt).desc
        .offset(offset)
        .limit(limit)
    }.map(rs => (dsmoq.models.Dataset(ds.resultName)(rs), rs.int("access_level"))).list().apply()
  }

  private def getGuestAccessLevel(datasetIds: Seq[String])(implicit s: DBSession) : Map[String, Int] = {
    if (datasetIds.nonEmpty) {
      val o = dsmoq.models.Ownership.syntax("o")
      withSQL {
        select(o.result.datasetId, o.result.accessLevel)
          .from(dsmoq.models.Ownership as o)
          .where
            .inByUuid(o.datasetId, datasetIds)
            .and
            .eq(o.ownerId, sqls.uuid(AppConf.guestGroupId))
            .and
            .isNull(o.deletedAt)
      }.map(x => (x.string(o.resultName.datasetId), x.int(o.resultName.accessLevel)) ).list().apply().toMap
    } else {
      Map.empty
    }
  }

  private def getOwners(datasetIds: Seq[String])(implicit s: DBSession) = {
    if (datasetIds.nonEmpty) {
      val o = dsmoq.models.Ownership.syntax("o")
      val g = dsmoq.models.Group.syntax("g")
      val tmp = withSQL {
        select(o.result.*, g.result.*)
          .from(dsmoq.models.Ownership as o)
          .innerJoin(dsmoq.models.Group as g).on(g.id, o.ownerId)
          .where
          .inByUuid(o.datasetId, datasetIds)
          .and
          .isNull(o.deletedAt)
          .and
          .isNull(g.deletedAt)
      }.map(rs => (dsmoq.models.Group(g.resultName)(rs), rs.int(o.accessLevel))).list().apply()

      tmp.groupBy(_._1.id)
//        .groupBy(x => {
//          val y = x._1
//          y.id
//        })
    } else {
      Map.empty
    }
  }

  private def getAttributes(datasetIds: Seq[String])(implicit s: DBSession) = {
    if (datasetIds.nonEmpty) {
      //val k = ds
      withSQL {
        select()
      }.map(_.toMap()).list().apply()
    } else {
      List.empty
    }

    //          // attributes
    //          val attrs = sql"""
    //            SELECT
    //              v.*,
    //              k.name
    //            FROM
    //              attribute_values AS v
    //              INNER JOIN attribute_keys AS k ON v.attribute_key_id = k.id
    //            WHERE
    //              v.dataset_id IN (${datasetIdSqls})
    //            """
    //            .map {x => (x.string(""), DatasetAttribute(name=x.string("name"), value=x.string("value"))) }
    //            .list().apply()
    //            .groupBy {x => x._1 }

  }

  private def joinIdStrings(list : Seq[String]) = {
    list.map{x => sqls"UUID(${x})"}
    //SQLSyntax.join(list.map{x => sqls"UUID(${x})"}, sqls",")
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
  id: String,
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

// TODO 返すべきユーザー情報
case class DatasetFile(
  id: String,
  name: String,
  description: String,
  url: String,
  fileSize: Long,
  user: String
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