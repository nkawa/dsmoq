package dsmoq.facade

import scala.util.{Failure, Try, Success}
import scalikejdbc._, SQLInterpolation._
import java.util.UUID
import java.nio.file.Paths
import dsmoq.AppConf
import dsmoq.facade.data._
import dsmoq.persistence
import dsmoq.persistence.PostgresqlHelper._
import dsmoq.exceptions.{ValidationException, NotAuthorizedException}
import org.joda.time.DateTime
import org.scalatra.servlet.FileItem
import dsmoq.forms.{AccessCrontolItem, AccessControl}

object DatasetFacade {
  /**
   * データセットを新規作成します。
   * @param params
   * @return
   */
  def create(params: DatasetData.CreateDatasetParams): Try[DatasetData.Dataset] = {
    try {
      if (params.userInfo.isGuest) throw new NotAuthorizedException
      if (params.files.getOrElse(Seq.empty).isEmpty) throw new ValidationException

      DB localTx { implicit s =>
        val myself = persistence.User.find(params.userInfo.id).get
        val myGroup = getPersonalGroup(myself.id).get

        val datasetId = UUID.randomUUID().toString
        val timestamp = DateTime.now()

        val files = params.files.getOrElse(Seq.empty).map(f => {
          val file = persistence.File.create(
            id = UUID.randomUUID.toString,
            datasetId = datasetId,
            name = f.name,
            description = "",
            fileType = 0,
            fileMime = "application/octet-stream",
            fileSize = f.size,
            createdBy = myself.id,
            createdAt = timestamp,
            updatedBy = myself.id,
            updatedAt = timestamp
          )
          val histroy = persistence.FileHistory.create(
            id = UUID.randomUUID.toString,
            fileId = file.id,
            fileType = 0,
            fileMime = "application/octet-stream",
            fileSize = f.size,
            createdBy = myself.id,
            createdAt = timestamp,
            updatedBy = myself.id,
            updatedAt = timestamp
          )
          writeFile(datasetId, file.id, histroy.id, f)
          (file, histroy)
        })
        val dataset = persistence.Dataset.create(
          id = datasetId,
          name = files.head._1.name,
          description = "",
          filesCount = files.length,
          filesSize = files.map(x => x._2.fileSize).sum,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp)
        val ownership = persistence.Ownership.create(
          id = UUID.randomUUID.toString,
          datasetId = datasetId,
          groupId = myGroup.id,
          accessLevel = persistence.AccessLevel.AllowAll,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp)
        val datasetImage = persistence.DatasetImage.create(
          id = UUID.randomUUID.toString,
          datasetId = dataset.id,
          imageId = AppConf.defaultDatasetImageId,
          isPrimary = true,
          displayOrder = 0,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp)

        Success(DatasetData.Dataset(
          id = dataset.id,
          meta = DatasetData.DatasetMetaData(
            name = dataset.name,
            description = dataset.description,
            license = dataset.licenseId,
            attributes = Seq.empty
          ),
          filesCount = dataset.filesCount,
          filesSize = dataset.filesSize,
          files = files.map(x => DatasetData.DatasetFile(
            id = x._1.id,
            name = x._1.name,
            description = x._1.description,
            size = x._2.fileSize,
            url = "", //TODO
            createdBy = params.userInfo,
            createdAt = timestamp.toString(),
            updatedBy = params.userInfo,
            updatedAt = timestamp.toString()
          )),
          images = Seq(Image(id = datasetImage.id, url = "")), //TODO
          primaryImage =  datasetImage.id,
          ownerships = Seq(DatasetData.DatasetOwnership(
            id = myself.id,
            name = myself.name,
            fullname = myself.fullname,
            organization = myself.organization,
            title = myself.title,
            image = "", //TODO
            accessLevel = ownership.accessLevel
          )),
          defaultAccessLevel = persistence.AccessLevel.Deny,
          permission = ownership.accessLevel
        ))
      }
    } catch {
      case e: RuntimeException => Failure(e)
    }
  }

  private def writeFile(datasetId: String, fileId: String, historyId: String, file: FileItem) = {
    val datasetDir = Paths.get(AppConf.fileDir, datasetId).toFile
    if (!datasetDir.exists()) datasetDir.mkdir()

    val fileDir = datasetDir.toPath.resolve(fileId).toFile
    if (!fileDir.exists()) fileDir.mkdir()

    file.write(fileDir.toPath.resolve(historyId).toFile)
  }

  /**
   * データセットを検索し、該当するデータセットの一覧を取得します。
   * @param params
   * @return
   */
  def search(params: DatasetData.SearchDatasetsParams): Try[RangeSlice[DatasetData.DatasetsSummary]] = {
    try {
      val offset = params.offset.getOrElse("0").toInt
      val limit = params.limit.getOrElse("20").toInt

      DB readOnly { implicit s =>
        val groups = getJoinedGroups(params.userInfo)
        val count = countDatasets(groups)

        val summary = RangeSliceSummary(count, limit, offset)
        val results = if (count > offset) {
          val datasets = findDatasets(groups, limit, offset)
          val datasetIds = datasets.map(_._1.id)

          val owners = getOwnerGroups(datasetIds)
          val guestAccessLevels = getGuestAccessLevel(datasetIds)
          val attributes = getAttributes(datasetIds)
          val files = getFiles(datasetIds)

          datasets.map(x => {
            val ds = x._1
            val permission = x._2
            DatasetData.DatasetsSummary(
              id = ds.id,
              name = ds.name,
              description = ds.description,
              image = "http://xxx",
              attributes = List.empty, //TODO
              ownerships = List.empty, //TODO
              files = ds.filesCount,
              dataSize = ds.filesSize,
              defaultAccessLevel = guestAccessLevels.get(ds.id).getOrElse(0),
              permission = permission
            )
          })
        } else {
          List.empty
        }
        Success(RangeSlice(summary, results))
      }
    } catch {
      case e: Exception => Failure(e)
    }
  }

  /**
   * 指定したデータセットの詳細情報を取得します。
   * @param params
   * @return
   */
  def get(params: DatasetData.GetDatasetParams): Try[DatasetData.Dataset] = {
    try {
      DB readOnly { implicit s =>
        (for {
          dataset <- getDataset(params.id)
          groups <- Some(getJoinedGroups(params.userInfo))
          permission <- getPermission(params.id, groups)
          guestAccessLevel <- Some(getGuestAccessLevel(params.id))
          owners <- Some(getOwnerGroups(params.id))
          files <- Some(getFiles(params.id))
        } yield {
          DatasetData.Dataset(
            id = dataset.id,
            files = files,
            filesCount = dataset.filesCount,
            filesSize = dataset.filesSize,
            meta = DatasetData.DatasetMetaData(
              name = dataset.name,
              description = dataset.description,
              license = None,
              attributes = Seq.empty
            ),
            images = Seq.empty, //TODO
            primaryImage = "", //TODO
            ownerships = owners.map(x => DatasetData.DatasetOwnership(
              id = x._1.id,
              name = x._1.name,
              fullname = "", //TODO
              organization = "", //TODO
              title = "", //TODO
              image = "", //TODO
              accessLevel = x._2
            )),
            defaultAccessLevel = guestAccessLevel,
            permission = permission
          )
        })
        .map(x => Success(x)).getOrElse(Failure(new RuntimeException()))
      }
    } catch {
      case e: Exception => Failure(e)
    }
  }

  /**
   *
   * @param user
   * @param item
   * @return
   */
  def setAccessContorl(user: User, item: AccessControl): Try[AccessCrontolItem] = {
    try {
      if (user.isGuest) throw new NotAuthorizedException

      DB localTx { implicit s =>
        val myself = persistence.User.find(user.id).get
        var group = persistence.Group.find(item.groupId).get

        val o = persistence.Ownership.o
        withSQL(
          select(o.result.*)
            .from(persistence.Ownership as o)
            .where
              .eq(o.datasetId, sqls.uuid(item.datasetId))
              .and
              .eq(o.groupId, sqls.uuid(group.id))
        ).map(persistence.Ownership(o.resultName)).single.apply match {
          case Some(x) =>
            if (item.accessLevel != x.accessLevel) {
              persistence.Ownership(
                id = x.id,
                datasetId = x.datasetId,
                groupId = x.groupId,
                accessLevel = item.accessLevel,
                createdBy = x.createdBy,
                createdAt = x.createdAt,
                updatedBy = myself.id,
                updatedAt = DateTime.now
              ).save()
            }
          case None =>
            if (item.accessLevel > 0) {
              val ts = DateTime.now
              persistence.Ownership.create(
                id = UUID.randomUUID.toString,
                datasetId = item.datasetId,
                groupId = item.groupId,
                accessLevel = item.accessLevel,
                createdBy = myself.id,
                createdAt = ts,
                updatedBy = myself.id,
                updatedAt = ts
              )
            }
        }

        Success(AccessCrontolItem(
          id = group.id,
          name = group.name,
          image = "", //TODO
          accessLevel = item.accessLevel
        ))
      }
    } catch {
      case e: RuntimeException => Failure(e)
    }
  }

  private def getJoinedGroups(user: User)(implicit s: DBSession) = {
    if (user.isGuest) {
      Seq.empty
    } else {
      val g = persistence.Group.syntax("g")
      val m = persistence.Member.syntax("m")
      withSQL {
        select(g.id)
          .from(persistence.Group as g)
          .innerJoin(persistence.Member as m).on(m.groupId, g.id)
          .where
            .eq(m.userId, sqls.uuid(user.id))
            .and
            .isNull(g.deletedAt)
            .and
            .isNull(m.deletedAt)
      }.map(_.string("id")).list().apply()
    }
  }

  private def getPersonalGroup(userId: String)(implicit s:DBSession) = {
    val g = persistence.Group.syntax("g")
    val m = persistence.Member.syntax("m")
    withSQL {
      select(g.result.*)
        .from(persistence.Group as g)
        .innerJoin(persistence.Member as m).on(g.id, m.groupId)
        .where
          .eq(m.userId, sqls.uuid(userId))
          .and
          .eq(g.groupType, persistence.GroupType.Personal)
        .limit(1)
    }.map(rs => persistence.Group(g.resultName)(rs)).single().apply()
  }

  private def countDatasets(groups : Seq[String])(implicit s: DBSession) = {
    val ds = persistence.Dataset.syntax("ds")
    val o = persistence.Ownership.syntax("o")
    withSQL {
      select(sqls.count(sqls.distinct(ds.id)).append(sqls"count"))
        .from(persistence.Dataset as ds)
        .innerJoin(persistence.Ownership as o).on(o.datasetId, ds.id)
        .where
        .inByUuid(o.groupId, Seq.concat(groups, Seq(AppConf.guestGroupId)))
        .and
        .gt(o.accessLevel, 0)
        .and
        .isNull(ds.deletedAt)
        .and
        .isNull(o.deletedAt)
    }.map(implicit rs => rs.int("count")).single().apply().get
  }

  private def findDatasets(groups: Seq[String], limit: Int, offset: Int)(implicit s: DBSession) = {
    val ds = persistence.Dataset.syntax("ds")
    val o = persistence.Ownership.syntax("o")
    withSQL {
      select(ds.result.*, sqls.max(o.accessLevel).append(sqls"access_level"))
        .from(persistence.Dataset as ds)
        .innerJoin(persistence.Ownership as o).on(ds.id, o.datasetId)
        .where
          .inByUuid(o.groupId, Seq.concat(groups, Seq(AppConf.guestGroupId)))
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
    }.map(rs => (persistence.Dataset(ds.resultName)(rs), rs.int("access_level"))).list().apply()
  }

  private def getDataset(id: String)(implicit s: DBSession) = {
    persistence.Dataset.find(id)
  }

  private def getPermission(id: String, groups: Seq[String])(implicit s: DBSession) = {
    val o = persistence.Ownership.syntax("o")
    withSQL {
      select(sqls.max(o.accessLevel).append(sqls"access_level"))
        .from(persistence.Ownership as o)
        .where
          .eq(o.datasetId, sqls.uuid(id))
          .and
          .inByUuid(o.groupId, Seq.concat(groups, Seq(AppConf.guestGroupId)))
    }.map(_.intOpt("access_level")).single().apply().flatten
  }

  private def getGuestAccessLevel(datasetId: String)(implicit s: DBSession) = {
    val o = persistence.Ownership.syntax("o")
    withSQL {
      select(o.result.accessLevel)
        .from(persistence.Ownership as o)
        .where
        .eq(o.datasetId, sqls.uuid(datasetId))
        .and
        .eq(o.groupId, sqls.uuid(AppConf.guestGroupId))
        .and
        .isNull(o.deletedAt)
    }.map(_.int(o.resultName.accessLevel)).single().apply().getOrElse(0)
  }

  private def getGuestAccessLevel(datasetIds: Seq[String])(implicit s: DBSession): Map[String, Int] = {
    if (datasetIds.nonEmpty) {
      val o = persistence.Ownership.syntax("o")
      withSQL {
        select(o.result.datasetId, o.result.accessLevel)
          .from(persistence.Ownership as o)
          .where
            .inByUuid(o.datasetId, datasetIds)
            .and
            .eq(o.groupId, sqls.uuid(AppConf.guestGroupId))
            .and
            .isNull(o.deletedAt)
      }.map(x => (x.string(o.resultName.datasetId), x.int(o.resultName.accessLevel)) ).list().apply().toMap
    } else {
      Map.empty
    }
  }

  private def getOwnerGroups(datasetId: String)(implicit s: DBSession) = {
    val o = persistence.Ownership.syntax("o")
    val g = persistence.Group.syntax("g")
    withSQL {
      select(o.result.*, g.result.*)
        .from(persistence.Ownership as o)
        .innerJoin(persistence.Group as g).on(g.id, o.groupId)
        .where
        .eq(o.datasetId, sqls.uuid(datasetId))
        .and
        .isNull(o.deletedAt)
        .and
        .isNull(g.deletedAt)
    }.map(rs => (persistence.Group(g.resultName)(rs), rs.int(o.resultName.accessLevel))).list().apply()
  }

  private def getOwnerGroups(datasetIds: Seq[String])(implicit s: DBSession) = {
    if (datasetIds.nonEmpty) {
      val o = persistence.Ownership.syntax("o")
      val g = persistence.Group.syntax("g")
      withSQL {
        select(o.result.*, g.result.*)
          .from(persistence.Ownership as o)
          .innerJoin(persistence.Group as g).on(g.id, o.groupId)
          .where
          .inByUuid(o.datasetId, datasetIds)
          .and
          .isNull(o.deletedAt)
          .and
          .isNull(g.deletedAt)
      }.map(rs => (persistence.Group(g.resultName)(rs), rs.int(o.resultName.accessLevel))).list().apply()
      .groupBy(_._1.id)
    } else {
      Map.empty
    }
  }

  private def getAttributes(datasetIds: Seq[String])(implicit s: DBSession) = {
    if (datasetIds.nonEmpty) {
      //val k = ds
//      withSQL {
//        select()
//      }.map(_.toMap()).list().apply()
      Map.empty
    } else {
      Map.empty
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

  private def getFiles(datasetId: String)(implicit s: DBSession) = {
    val f = persistence.File.f
    val u1 = persistence.User.syntax("u1")
    val u2 = persistence.User.syntax("u2")

    withSQL {
      select(f.result.*, u1.result.*, u2.result.*)
        .from(persistence.File as f)
        .innerJoin(persistence.User as u1).on(f.createdBy, u1.id)
        .innerJoin(persistence.User as u2).on(f.updatedBy, u2.id)
        .where
          .eq(f.datasetId, sqls.uuid(datasetId))
          .and
          .isNull(f.deletedAt)
        .orderBy(f.createdAt)
    }.map(rs =>
      (
        persistence.File(f.resultName)(rs),
        persistence.User(u1.resultName)(rs),
        persistence.User(u2.resultName)(rs)
      )
    ).list.apply.map(x =>
      DatasetData.DatasetFile(
        id = x._1.id,
        name = x._1.name,
        description = x._1.description,
        url = "", //TODO
        size = x._1.fileSize,
        createdBy = User(x._2),
        createdAt = x._1.createdAt.toString(),
        updatedBy = User(x._3),
        updatedAt = x._1.updatedAt.toString()
      )
    )
  }

  private def getFiles(datasetIds: Seq[String])(implicit s: DBSession) = {
    Map.empty
  }
}