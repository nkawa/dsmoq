package dsmoq.services

import java.io.{
  ByteArrayInputStream, Closeable, FileOutputStream,
  InputStream, InputStreamReader,
  SequenceInputStream
}
import java.nio.charset.Charset
import java.nio.file.{
  Files, Path, Paths
}

import java.util.ResourceBundle
import java.util.UUID
import java.util.zip.ZipInputStream

import com.typesafe.scalalogging.LazyLogging
import org.slf4j.MarkerFactory

import scala.collection.mutable
import scala.language.reflectiveCalls
import scala.util.{Failure, Try, Success}

import com.github.tototoshi.csv.CSVReader
import org.apache.commons.io.input.BoundedInputStream
import org.joda.time.DateTime
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.{compact, render}
import org.json4s.{JInt, JBool}
import org.scalatra.servlet.FileItem
import scalikejdbc.{DB, DBSession, delete, update, select, SelectSQLBuilder, sqls, SubQuery, withSQL}
import scalikejdbc.interpolation.Implicits._

import dsmoq.AppConf
import dsmoq.ResourceNames
import dsmoq.persistence
import dsmoq.persistence.{
  Annotation,
  Dataset, DatasetAnnotation, DatasetImage,
  DefaultAccessLevel,
  GroupAccessLevel, GroupType,
  OwnerType, Ownership,
  PresetType, UserAccessLevel, ZipedFiles
}
import dsmoq.exceptions.{AccessDeniedException, BadRequestException, InputValidationException, NotFoundException}
import dsmoq.logic.{FileManager, StringUtil, ImageSaveLogic, ZipUtil}
import dsmoq.persistence.PostgresqlHelper._
import dsmoq.services.json.{DatasetData, Image, RangeSlice, RangeSliceSummary}
import dsmoq.services.json.DatasetData.{DatasetOwnership, DatasetZipedFile, CopiedDataset, DatasetTask}

/**
 * データセット関連の操作を取り扱うサービスクラス
 *
 * @param resource リソースバンドルのインスタンス
 */
class DatasetService(resource: ResourceBundle) extends LazyLogging {

  /**
    * ログマーカー
    */
  val LOG_MARKER = MarkerFactory.getMarker("DATASET_LOG")

  private val datasetImageDownloadRoot = AppConf.imageDownloadRoot + "datasets/"

  /**
   * データセットを新規作成します。
   *
   * @param files データセットに追加するファイルのリスト
   * @param saveLocal データセットをLocalに保存するか否か
   * @param saveS3 データセットをS3に保存するか否か
   * @param name データセット名
   * @param user ユーザ情報
   * @return 作成したデータセットオブジェクト。エラーがあれば、例外をFailureに包んで返却する。発生しうる例外は、NullPointerExceptionである。
   */
  def create(files: Seq[FileItem], saveLocal: Boolean, saveS3: Boolean, name: String, user: User): Try[DatasetData.Dataset] = {
    try {
      CheckUtil.checkNull(files, "files")
      CheckUtil.checkNull(saveLocal, "saveLocal")
      CheckUtil.checkNull(saveS3, "saveS3")
      CheckUtil.checkNull(name, "name")
      CheckUtil.checkNull(user, "user")
      DB localTx { implicit s =>
        val myself = persistence.User.find(user.id).get
        val myGroup = getPersonalGroup(myself.id).get

        val datasetId = UUID.randomUUID().toString
        val timestamp = DateTime.now()

        val f = files.map(f => {
          val isZip = f.getName.endsWith("zip")
          val fileId = UUID.randomUUID.toString
          val historyId = UUID.randomUUID.toString
          FileManager.uploadToLocal(datasetId, fileId, historyId, f)
          val path = Paths.get(AppConf.fileDir, datasetId, fileId, historyId)
          val file = persistence.File.create(
            id = fileId,
            datasetId = datasetId,
            historyId = historyId,
            name = f.name,
            description = "",
            fileType = 0,
            fileMime = "application/octet-stream",
            fileSize = f.size,
            createdBy = myself.id,
            createdAt = timestamp,
            updatedBy = myself.id,
            updatedAt = timestamp,
            localState = if (saveLocal) { SaveStatus.SAVED } else { SaveStatus.DELETING },
            s3State = if (saveS3) { SaveStatus.SYNCHRONIZING } else { SaveStatus.NOT_SAVED }
          )
          val histroy = persistence.FileHistory.create(
            id = historyId,
            fileId = file.id,
            fileType = 0,
            fileMime = "application/octet-stream",
            filePath = "/" + datasetId + "/" + file.id + "/" + historyId,
            fileSize = f.size,
            isZip = isZip,
            realSize = if (isZip) { createZipedFiles(path, historyId, timestamp, myself).right.getOrElse(f.size) } else { f.size },
            createdBy = myself.id,
            createdAt = timestamp,
            updatedBy = myself.id,
            updatedAt = timestamp
          )

          (file, histroy)
        })

        val dataset = persistence.Dataset.create(
          id = datasetId,
          name = if (name.isEmpty) { f.head._1.name } else { name },
          description = "",
          licenseId = AppConf.defaultLicenseId,
          filesCount = f.length,
          filesSize = f.map(x => x._2.fileSize).sum,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp,
          localState = if (saveLocal) { SaveStatus.SAVED } else { SaveStatus.DELETING },
          s3State = if (saveS3) { SaveStatus.SYNCHRONIZING } else { SaveStatus.NOT_SAVED }
        )

        if (saveS3 && ! f.isEmpty) {
          createTask(datasetId, MoveToStatus.S3, myself.id, timestamp, saveLocal)
        }

        val ownership = persistence.Ownership.create(
          id = UUID.randomUUID.toString,
          datasetId = datasetId,
          groupId = myGroup.id,
          accessLevel = persistence.UserAccessLevel.Owner,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp)
        val datasetImage = persistence.DatasetImage.create(
          id = UUID.randomUUID.toString,
          datasetId = dataset.id,
          imageId = AppConf.defaultDatasetImageId,
          isPrimary = true,
          isFeatured = true,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp)

        for (id <- AppConf.defaultFeaturedImageIds) {
          persistence.DatasetImage.create(
            id = UUID.randomUUID.toString,
            datasetId = dataset.id,
            imageId = id,
            isPrimary = false,
            isFeatured = false,
            createdBy = myself.id,
            createdAt = timestamp,
            updatedBy = myself.id,
            updatedAt = timestamp)
        }

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
          files = f.map{ x => 
            val zipedFiles = if (x._2.isZip) { getZipedFiles(datasetId, x._2.id) } else { Seq.empty }
            DatasetData.DatasetFile(
              id = x._1.id,
              name = x._1.name,
              description = x._1.description,
              size = x._2.fileSize,
              url = AppConf.fileDownloadRoot + datasetId + "/" + x._1.id,
              createdBy = user,
              createdAt = timestamp.toString(),
              updatedBy = user,
              updatedAt = timestamp.toString(),
              isZip = x._2.isZip,
              zipedFiles = zipedFiles,
              zipCount = zipedFiles.size
            )
          },
          images = Seq(Image(
            id = AppConf.defaultDatasetImageId,
            url = datasetImageDownloadRoot + datasetId + "/" + AppConf.defaultDatasetImageId
          )),
          primaryImage =  AppConf.defaultDatasetImageId,
          featuredImage = AppConf.defaultDatasetImageId,
          ownerships = Seq(DatasetData.DatasetOwnership(
            id = myself.id,
            name = myself.name,
            fullname = myself.fullname,
            organization = myself.organization,
            title = myself.title,
            description = myself.description,
            image = AppConf.imageDownloadRoot + "user/" + myself.id + "/" + myself.imageId,
            accessLevel = ownership.accessLevel,
            ownerType = OwnerType.User
          )),
          defaultAccessLevel = persistence.DefaultAccessLevel.Deny,
          permission = ownership.accessLevel,
          accessCount = 0,
          localState = dataset.localState,
          s3State = dataset.s3State,
          fileLimit = AppConf.fileLimit
        ))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  private def createZipedFiles(path: Path, historyId: String, timestamp: DateTime, myself: persistence.User)(implicit s: DBSession): Either[Long, Long] = {
    for {
      zipInfos <- ZipUtil.read(path).right
    } yield {
      val zfs = for {
        zipInfo <- zipInfos.filter(!_.fileName.endsWith("/"))
      } yield {
        val centralHeader = zipInfo.centralHeader.clone
        // DL時には単独のZIPファイルとして扱うため、
        // Central Header内のLocal Headerへの参照を先頭に書き換える必要がある
        centralHeader(42) = 0
        centralHeader(43) = 0
        centralHeader(44) = 0
        centralHeader(45) = 0
        persistence.ZipedFiles.create(
          id = UUID.randomUUID().toString,
          historyId = historyId,
          name = zipInfo.fileName,
          description = "",
          fileSize = zipInfo.uncompressSize,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp,
          cenSize = zipInfo.centralHeader.length,
          dataStart = zipInfo.localHeaderOffset,
          dataSize = zipInfo.dataSizeWithLocalHeader,
          cenHeader = centralHeader
        )
      }
      zfs.map(_.fileSize).sum
    }
  }

  private def createTask(datasetId: String, commandType: Int, userId: String, timestamp: DateTime, isSave: Boolean)(implicit s: DBSession): String = {
    val id = UUID.randomUUID.toString
    persistence.Task.create(
      id = id,
      taskType = 0,
      parameter = compact(render(("commandType" -> JInt(commandType)) ~ ("datasetId" -> datasetId) ~ ("withDelete" -> JBool(!isSave)))),
      executeAt = timestamp,
      status = 0,
      createdBy = userId,
      createdAt = timestamp,
      updatedBy = userId,
      updatedAt = timestamp
    )
    id
  }

  /**
   * データセットを検索し、該当するデータセットの一覧を取得します。
 *
   * @param query
   * @param owners
   * @param groups
   * @param attributes
   * @param limit
   * @param offset
   * @param user
   * @return
   */
  def search(query: Option[String] = None,
             owners: Seq[String] = Seq.empty,
             groups: Seq[String] = Seq.empty,
             attributes: Seq[DataSetAttribute] = Seq.empty,
             limit: Option[Int] = None,
             offset: Option[Int] = None,
             orderby: Option[String] = None,
             user: User): Try[RangeSlice[DatasetData.DatasetsSummary]] = {
    try {
      val limit_ = limit.getOrElse(20)
      val offset_ = offset.getOrElse(0)

      DB readOnly { implicit s =>
        val joinedGroups = getJoinedGroups(user)

        (for {
          userGroupIds <- getGroupIdsByUserName(owners)
          groupIds <- getGroupIdsByGroupName(groups)
        } yield {
          (userGroupIds, groupIds)
        }) match {
          case Some(x) =>
            val count = countDataSets(joinedGroups, query, x._1, x._2, attributes)
            val records = if (count > 0) {
              findDataSets(joinedGroups, query, x._1, x._2, attributes, limit_, offset_, orderby, user)
            } else {
              List.empty
            }
            Success(RangeSlice(RangeSliceSummary(count, limit_, offset_), records))
          case None =>
            Success(RangeSlice(RangeSliceSummary(0, limit_, offset_), List.empty))
        }
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  private def getGroupIdsByUserName(names: Seq[String])(implicit s: DBSession) = {
    if (names.nonEmpty) {
      val g = persistence.Group.g
      val m = persistence.Member.m
      val u = persistence.User.u
      val groups = withSQL {
        select.apply(g.id)
          .from(persistence.Group as g)
          .innerJoin(persistence.Member as m).on(m.groupId, g.id)
          .innerJoin(persistence.User as u).on(u.id, m.userId)
          .where
          .in(u.name, names)
          .and
          .eq(g.groupType, GroupType.Personal)
          .and
          .isNull(g.deletedAt)
          .and
          .isNull(m.deletedAt)
          .and
          .isNull(u.deletedAt)
      }.map(rs => rs.string(1)).list().apply()
      if (groups.nonEmpty) {
        Some(groups)
      } else {
        None
      }
    } else {
      Some(List.empty)
    }
  }

  private def getGroupIdsByGroupName(names: Seq[String])(implicit s: DBSession) = {
    if (names.nonEmpty) {
      val g = persistence.Group.g
      val groups = withSQL {
        select.apply(g.id)
          .from(persistence.Group as g)
          .where
          .in(g.name, names)
          .and
          .eq(g.groupType, GroupType.Public)
          .and
          .isNull(g.deletedAt)
      }.map(rs => rs.string(1)).list().apply()
      if (groups.nonEmpty) {
        Some(groups)
      } else {
        None
      }
    } else {
      Some(List.empty)
    }
  }

  private def countDataSets(joindGroups : Seq[String], query: Option[String],
                            ownerUsers: Seq[String], ownerGroups: Seq[String],
                            attributes: Seq[DataSetAttribute])(implicit s: DBSession) = {
    withSQL {
      createDatasetSql(select.apply[Int](sqls.countDistinct(persistence.Dataset.d.id)),
                       joindGroups, query, ownerUsers, ownerGroups, attributes)
    }.map(implicit rs => rs.int(1)).single().apply().get
  }

  private def findDataSets(joindGroups : Seq[String], query: Option[String],
                           ownerUsers: Seq[String], ownerGroups: Seq[String],
                           attributes: Seq[DataSetAttribute], limit: Int, offset: Int, orderby: Option[String], user: User)(implicit s: DBSession) = {
    val ds = persistence.Dataset.d
    val o = persistence.Ownership.o
    val a = persistence.Annotation.a
    val da = persistence.DatasetAnnotation.syntax("da")
    val xda2 = SubQuery.syntax("xda2", da.resultName, a.resultName)

    val selects = orderby match {
      case Some(ord) if ord == "attribute" => select.apply[Any](ds.resultAll, sqls.max(o.accessLevel).append(sqls"access_level"), xda2(da).data)
      case _ => select.apply[Any](ds.resultAll, sqls.max(o.accessLevel).append(sqls"access_level"))
    }

    val datasets = orderby match {
      case Some(ord) if ord == "attribute" => {
        withSQL {
          createDatasetSql(selects, joindGroups, query, ownerUsers, ownerGroups, attributes)
            .groupBy(ds.id, xda2(da).data)
            .orderBy(xda2(da).data)
            .offset(offset)
            .limit(limit)
        }.map(rs => (persistence.Dataset(ds.resultName)(rs), rs.int("access_level"))).list().apply()
      }
      case _ => {
        withSQL {
          createDatasetSql(selects, joindGroups, query, ownerUsers, ownerGroups, attributes)
            .groupBy(ds.id)
            .orderBy(ds.updatedAt).desc
            .offset(offset)
            .limit(limit)
        }.map(rs => (persistence.Dataset(ds.resultName)(rs), rs.int("access_level"))).list().apply()
      }
    }

    val datasetIds = datasets.map(_._1.id)

    val ownerMap = getOwnerMap(datasetIds)
    val guestAccessLevelMap = getGuestAccessLevelMap(datasetIds)
    val imageIdMap = getImageIdMap(datasetIds)
    val featuredImageIdMap = getFeaturedImageIdMap(datasetIds)

    datasets.map(x => {
      val ds = x._1
      val permission = x._2
      val imageUrl = imageIdMap.get(ds.id).map(x => datasetImageDownloadRoot + ds.id + "/" + x).getOrElse("")
      val featuredImageUrl = featuredImageIdMap.get(ds.id).map(x => datasetImageDownloadRoot + ds.id + "/" + x).getOrElse("")
      val accessLevel = guestAccessLevelMap.get(ds.id).getOrElse(DefaultAccessLevel.Deny)
      DatasetData.DatasetsSummary(
        id = ds.id,
        name = ds.name,
        description = ds.description,
        image = imageUrl,
        featuredImage = featuredImageUrl,
        attributes = getAttributes(ds.id), //TODO 非効率
        ownerships = if (user.isGuest) { List.empty } else { ownerMap.get(ds.id).getOrElse(List.empty) } ,
        files = ds.filesCount,
        dataSize = ds.filesSize,
        defaultAccessLevel = accessLevel,
        permission = permission,
        localState = ds.localState,
        s3State = ds.s3State
      )
    })
  }

  private def createDatasetSql[A](selectSql: SelectSQLBuilder[A], joinedGroups : Seq[String],
                                  query: Option[String], ownerUsers: Seq[String], ownerGroups: Seq[String],
                                  attributes: Seq[DataSetAttribute]) = {
    val ds = persistence.Dataset.d
    val g = persistence.Group.g
    val o = persistence.Ownership.o
    val xo = SubQuery.syntax("xo", o.resultName, g.resultName)
    val a = persistence.Annotation.a
    val da = persistence.DatasetAnnotation.syntax("da")
    val xda = SubQuery.syntax("xda", da.resultName, a.resultName)
    val xda2 = SubQuery.syntax("xda2", da.resultName, a.resultName)
    val f = persistence.File.f
    val fh = persistence.FileHistory.fh
    val zf = persistence.ZipedFiles.zf
    val xf = SubQuery.syntax("xf", ds.resultName, f.resultName, fh.resultName, zf.resultName)

    selectSql
      .from(persistence.Dataset as ds)
      .innerJoin(persistence.Ownership as o).on(o.datasetId, ds.id)
      .map { sql =>
        query match {
          case Some(q) => {
            sql.innerJoin(
              select(ds.id)
                .from(persistence.Dataset as ds)
                .where
                  .withRoundBracket(s => s.upperLikeQuery(ds.name, q).or.upperLikeQuery(ds.description, q))
              .union(
                select(f.datasetId.append(sqls" id"))
                  .from(persistence.File as f)
                  .where
                    .upperLikeQuery(f.name, q)
                ).union(
                  select(f.datasetId.append(sqls" id"))
                    .from(persistence.File as f)
                    .innerJoin(persistence.FileHistory as fh).on(fh.fileId, f.id)
                    .innerJoin(persistence.ZipedFiles as zf).on(zf.historyId, fh.id)
                    .where
                      .upperLikeQuery(zf.name, q)
                )
                .as(xf)
            ).on(sqls"xf.id", ds.id)
          }
          case None => sql
        }
      }
      .map { sql =>
        if (ownerUsers.nonEmpty || ownerGroups.nonEmpty) {
          sql.innerJoin(
            select.apply[String](o.result.datasetId)
              .from(persistence.Ownership as o)
              .innerJoin(persistence.Group as g).on(o.groupId, g.id)
              .where
                .isNull(o.deletedAt).and.isNull(g.deletedAt)
                .and
                .withRoundBracket(
                  _.map { q =>
                    q.append(
                      sqls.join(
                        Seq(
                          ownerUsers.map { sqls.eqUuid(o.groupId, _).and.eq(o.accessLevel, UserAccessLevel.Owner) },
                          ownerGroups.map { sqls.eqUuid(o.groupId, _).and.eq(o.accessLevel, GroupAccessLevel.Provider) }
                        ).flatten,
                        sqls"or"
                      )
                    )
                  }
                )
              .groupBy(o.datasetId).having(sqls.eq(sqls.count(o.datasetId), ownerUsers.length + ownerGroups.length))
              .as(xo)
          ).on(ds.id, xo(o).datasetId)
        } else {
          sql
        }
      }
      .map(sql =>
        if (attributes.nonEmpty) {
          sql.innerJoin(
            select.apply(da.result.datasetId)
              .from(persistence.Annotation as a)
              .innerJoin(persistence.DatasetAnnotation as da).on(da.annotationId, a.id)
              .where
                .isNull(a.deletedAt).and.isNull(da.deletedAt)
                .and
                .withRoundBracket(
                  _.append(sqls.join(attributes.map{x =>
                    if (x.value.isEmpty) {
                      sqls.eq(a.name, x.name)
                    } else {
                      sqls.eq(a.name, x.name).and.eq(da.data, x.value)
                    }
                  }, sqls"or"))
                )
              .groupBy(da.datasetId).having(sqls.eq(sqls.count(da.datasetId), attributes.length))
              .as(xda)
          ).on(ds.id, xda(da).datasetId)
        } else {
          sql
        }
      )
      .map(sql =>
        if (attributes.nonEmpty) {
          sql.leftJoin(
            select.apply(da.result.datasetId, da.result.data)
              .from(persistence.Annotation as a)
              .innerJoin(persistence.DatasetAnnotation as da).on(da.annotationId, a.id)
              .where
              .isNull(a.deletedAt).and.isNull(da.deletedAt)
              .and
              .withRoundBracket(
                _.append(sqls.join(attributes.map{x =>
                    sqls.eq(a.name, x.name)
                }, sqls"or"))
              )
              .as(xda2)
          ).on(ds.id, xda2(da).datasetId)
        } else {
          sql
        }
      )
      .where
        .inUuid(o.groupId, Seq.concat(joinedGroups, Seq(AppConf.guestGroupId)))
        .and
        .gt(o.accessLevel, GroupAccessLevel.Deny)
        .and
        .isNull(ds.deletedAt)
  }

  private def getGuestAccessLevelMap(datasetIds: Seq[String])(implicit s: DBSession): Map[String, Int] = {
    if (datasetIds.nonEmpty) {
      val o = persistence.Ownership.syntax("o")
      withSQL {
        select(o.result.datasetId, o.result.accessLevel)
          .from(persistence.Ownership as o)
          .where
          .inUuid(o.datasetId, datasetIds)
          .and
          .eq(o.groupId, sqls.uuid(AppConf.guestGroupId))
          .and
          .isNull(o.deletedAt)
      }.map(rs => (rs.string(o.resultName.datasetId), rs.int(o.resultName.accessLevel)) ).list().apply().toMap
    } else {
      Map.empty
    }
  }

  private def getImageIdMap(datasetIds: Seq[String])(implicit s: DBSession): Map[String, String] = {
    if (datasetIds.nonEmpty) {
      val di = persistence.DatasetImage.syntax("di")
      withSQL {
        select(di.result.datasetId, di.result.imageId)
          .from(persistence.DatasetImage as di)
          .where
          .inUuid(di.datasetId, datasetIds)
          .and
          .eq(di.isPrimary, true)
          .and
          .isNull(di.deletedAt)
      }.map(rs => (rs.string(di.resultName.datasetId), rs.string(di.resultName.imageId))).list().apply().toMap
    } else {
      Map.empty
    }
  }

  private def getFeaturedImageIdMap(datasetIds: Seq[String])(implicit s: DBSession): Map[String, String] = {
    if (datasetIds.nonEmpty) {
      val di = persistence.DatasetImage.syntax("di")
      withSQL {
        select(di.result.datasetId, di.result.imageId)
          .from(persistence.DatasetImage as di)
          .where
          .inUuid(di.datasetId, datasetIds)
          .and
          .eq(di.isFeatured, true)
          .and
          .isNull(di.deletedAt)
      }.map(rs => (rs.string(di.resultName.datasetId), rs.string(di.resultName.imageId))).list().apply().toMap
    } else {
      Map.empty
    }
  }

  private def getOwnerMap(datasetIds: Seq[String])(implicit s: DBSession): Map[String, List[DatasetData.DatasetOwnership]] = {
    if (datasetIds.nonEmpty) {
      val o = persistence.Ownership.o
      val g = persistence.Group.g
      val m = persistence.Member.m
      val u = persistence.User.u
      val gi = persistence.GroupImage.syntax("gi")
      val i = persistence.Image.i

      withSQL {
        select.apply(o.result.datasetId, g.result.id, g.result.groupType,
              u.result.id, u.result.column("name"), u.result.fullname, g.result.column("name"))
          .from(persistence.Ownership as o)
            .innerJoin(persistence.Group as g).on(o.groupId, g.id)
            .leftJoin(persistence.Member as m).on(sqls.eq(m.groupId, g.id).and.eq(g.groupType, 1))
            .leftJoin(persistence.User as u).on(m.userId, u.id)
          .where
            .inUuid(o.datasetId, datasetIds)
            .and
            .append(sqls"case ${g.groupType}")
              .append(sqls"when 0 then").append(sqls.eq(o.accessLevel, persistence.GroupAccessLevel.Provider))
              .append(sqls"when 1 then").append(sqls.joinWithAnd(
                sqls.eq(o.accessLevel, persistence.UserAccessLevel.Owner),
                sqls.isNotNull(u.id),
                sqls.isNull(m.deletedAt),
                sqls.isNull(u.deletedAt)
              ))
            .append(sqls"end")
      }.map(rs => {
        val datasetId = rs.string(o.resultName.datasetId)
        if (rs.int(g.resultName.groupType) == GroupType.Personal) {
          (datasetId, DatasetData.DatasetOwnership(
            id = rs.string(u.resultName.id),
            name = rs.string(u.resultName.name),
            fullname = rs.string(u.resultName.fullname),
            image = "",
            accessLevel = UserAccessLevel.Owner,
            ownerType = OwnerType.User,
            description = "",
            title = "",
            organization = ""
          ))
        } else {
          (datasetId, DatasetData.DatasetOwnership(
            id = rs.string(g.resultName.id),
            name = rs.string(g.resultName.name),
            fullname = "",
            image = "",
            accessLevel = GroupAccessLevel.Provider,
            ownerType = OwnerType.Group,
            description = "",
            title = "",
            organization = ""
          ))
        }
      }).list().apply().groupBy(_._1).mapValues(_.map(_._2))
    } else {
      Map.empty
    }
  }

  /**
   * データセットの参照権限のチェックを行う。
   * 
   * @param datasetId データセットID
   * @param user ユーザ情報
   * @return ロールの値
   * @throws AccessDeniedException 権限に該当しなかった場合
   * @throws NullPointerException 引数がnullの場合
   */
  def checkReadPermission(datasetId: String, user: User)(implicit session: DBSession): Int = {
    CheckUtil.checkNull(datasetId, "datasetId")
    CheckUtil.checkNull(user, "user")
    CheckUtil.checkNull(session, "session")
    val groups = getJoinedGroups(user)
    val permission = getPermission(datasetId, groups)
    // FIXME チェック時、user権限はUserAccessLevelクラス, groupの場合はGroupAccessLevelクラスの定数を使用する
    // (UserAndGroupAccessLevel.DENY 定数を削除する)
    if (permission == UserAndGroupAccessLevel.DENY) {
      throw new AccessDeniedException
    }
    permission
  }

  /**
   * 指定したデータセットの詳細情報を取得します。
 *
   * @param id データセットID
   * @param user ユーザ情報
   * @return データセットオブジェクト。エラーがあれば、例外をFailureに包んで返却する。発生しうる例外は、NotFoundException、NullPointerException、AccessDeniedExceptionである。
   */
  def get(id: String, user: User): Try[DatasetData.Dataset] = {
    try {
      CheckUtil.checkNull(id, "id")
      CheckUtil.checkNull(user, "user")
      DB readOnly { implicit s =>
        // データセットが存在しない場合例外
        val dataset = getDataset(id) match {
          case Some(x) => x
          case None => throw new NotFoundException
        }
        val permission = checkReadPermission(id, user)
        val guestAccessLevel = getGuestAccessLevel(id)
        val owners = getAllOwnerships(id, user)
        val attributes = getAttributes(id)
        val images = getImages(id)
        val primaryImage = getPrimaryImageId(id).getOrElse(AppConf.defaultDatasetImageId)
        val featuredImage = getFeaturedImageId(id).getOrElse(AppConf.defaultFeaturedImageIds(0))
        val count = getAccessCount(id)
        Success(
          DatasetData.Dataset(
            id = dataset.id,
            files = Seq.empty,
            filesCount = dataset.filesCount,
            filesSize = dataset.filesSize,
            meta = DatasetData.DatasetMetaData(
              name = dataset.name,
              description = dataset.description,
              license = dataset.licenseId,
              attributes = attributes
            ),
            images = images,
            primaryImage = primaryImage,
            featuredImage = featuredImage,
            ownerships = if (user.isGuest) { List.empty } else { owners },
            defaultAccessLevel = guestAccessLevel,
            permission = permission,
            accessCount = count,
            localState = dataset.localState,
            s3State = dataset.s3State,
            fileLimit = AppConf.fileLimit
          )
        )
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  /**
   * 指定したデータセットにファイルを追加します。
   *
   * @param id データセットID
   * @param files ファイルリスト
   * @param user ユーザ情報
   * @return 追加したファイルデータオブジェクト。エラーがあれば、例外をFailureに包んで返却する。発生しうる例外は、AccessDeniedException、NotFoundException、NullPointerExceptionである。
   */
  def addFiles(id: String, files: Seq[FileItem], user: User): Try[DatasetData.DatasetAddFiles] = {
    try {
      CheckUtil.checkNull(id, "id")
      CheckUtil.checkNull(files, "files")
      CheckUtil.checkNull(user, "user")
      DB localTx { implicit s =>
        datasetAccessabilityCheck(id, user)

        val dataset = getDataset(id).get
        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        val f = files.map(f => {
          val isZip = f.getName.endsWith("zip")
          val fileId = UUID.randomUUID.toString
          val historyId = UUID.randomUUID.toString
          FileManager.uploadToLocal(id, fileId, historyId, f)
          val path = Paths.get(AppConf.fileDir, id, fileId, historyId)
          val file = persistence.File.create(
            id = fileId,
            datasetId = id,
            historyId = historyId,
            name = f.name,
            description = "",
            fileType = 0,
            fileMime = "application/octet-stream",
            fileSize = f.size,
            createdBy = myself.id,
            createdAt = timestamp,
            updatedBy = myself.id,
            updatedAt = timestamp,
            localState = if (dataset.localState == SaveStatus.SAVED || dataset.localState == SaveStatus.SYNCHRONIZING) { SaveStatus.SAVED } else { SaveStatus.DELETING },
            s3State = if (dataset.s3State == SaveStatus.NOT_SAVED) { SaveStatus.NOT_SAVED } else if (dataset.s3State == SaveStatus.DELETING) { SaveStatus.DELETING } else { SaveStatus.SYNCHRONIZING }
          )
          val history = persistence.FileHistory.create(
            id = historyId,
            fileId = file.id,
            fileType = 0,
            fileMime = "application/octet-stream",
            filePath = "/" + id + "/" + file.id + "/" + historyId,
            fileSize = f.size,
            isZip = isZip,
            realSize = if (isZip) { createZipedFiles(path, historyId, timestamp, myself).right.getOrElse(f.size) } else { f.size },
            createdBy = myself.id,
            createdAt = timestamp,
            updatedBy = myself.id,
            updatedAt = timestamp
          )

          (file, history)
        })

        if (dataset.s3State == SaveStatus.SAVED || dataset.s3State == SaveStatus.SYNCHRONIZING) {
          createTask(id, MoveToStatus.S3, myself.id, timestamp, dataset.localState == SaveStatus.SAVED)
        }
        // datasetsのfiles_size, files_countの更新
        updateDatasetFileStatus(id, myself.id, timestamp)

        Success(DatasetData.DatasetAddFiles(
          files = f.map{ x =>
            val zipedFiles = if (x._2.isZip) { getZipedFiles(id, x._2.id) } else { Seq.empty }
            DatasetData.DatasetFile(
              id = x._1.id,
              name = x._1.name,
              description = x._1.description,
              size = x._2.fileSize,
              url = AppConf.fileDownloadRoot + id + "/" + x._1.id,
              createdBy = user,
              createdAt = timestamp.toString(),
              updatedBy = user,
              updatedAt = timestamp.toString(),
              isZip = x._2.isZip,
              zipedFiles = zipedFiles,
              zipCount = zipedFiles.size
            )
          }
        ))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  /**
   * 指定したファイルを更新します。
   *
   * @param datasetId データセットID
   * @param fileId ファイルID
   * @param file 更新するファイル
   * @param user ユーザ情報
   * @return 更新したファイルオブジェクト。エラーがあれば、例外をFailureに包んで返却する。発生しうる例外は、NotFoundException、AccessDeniedException、NullPointerExceptionである。
   */
  def updateFile(datasetId: String, fileId: String, file: FileItem, user: User): Try[DatasetData.DatasetFile] = {
    try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(fileId, "fileId")
      CheckUtil.checkNull(file, "file")
      CheckUtil.checkNull(user, "user")
      DB localTx { implicit s =>
        fileAccessabilityCheck(datasetId, fileId, user)

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        val historyId = UUID.randomUUID.toString

        val dataset = getDataset(datasetId).get
        updateFileNameAndSize(
          fileId,
          historyId,
          file,
          myself.id,
          timestamp,
          if (dataset.s3State == SaveStatus.NOT_SAVED) { SaveStatus.NOT_SAVED } else if (dataset.s3State == SaveStatus.DELETING) { SaveStatus.DELETING } else { SaveStatus.SYNCHRONIZING },
          if (dataset.localState == SaveStatus.SAVED || dataset.localState == SaveStatus.SYNCHRONIZING) { SaveStatus.SAVED } else { SaveStatus.DELETING }
        )

        val isZip = file.getName.endsWith("zip")
        FileManager.uploadToLocal(datasetId, fileId, historyId, file)
        val path = Paths.get(AppConf.fileDir, datasetId, fileId, historyId)

        val history = persistence.FileHistory.create(
          id = historyId,
          fileId = fileId,
          fileType = 0,
          fileMime = "application/octet-stream",
          filePath = "/" + datasetId + "/" + fileId + "/" + historyId,
          fileSize = file.size,
          isZip = isZip,
          realSize = if (isZip) { createZipedFiles(path, historyId, timestamp, myself).right.getOrElse(file.size) } else { file.size },
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp
        )
        FileManager.uploadToLocal(datasetId, fileId, history.id, file)
        if (dataset.s3State == SaveStatus.SAVED || dataset.s3State == SaveStatus.SYNCHRONIZING) {
          createTask(datasetId, MoveToStatus.S3, myself.id, timestamp, dataset.localState == SaveStatus.SAVED)

          // S3に上がっている場合は、アップロードが完了するまで、ローカルからダウンロードしなければならない
          withSQL {
            val d = persistence.Dataset.column
            update(persistence.Dataset)
              .set(d.localState -> SaveStatus.DELETING,
                d.s3State -> SaveStatus.SYNCHRONIZING)
              .where
              .eq(d.id, sqls.uuid(datasetId))
          }.update().apply
        }

        // datasetsのfiles_size, files_countの更新
        updateDatasetFileStatus(datasetId, myself.id, timestamp)

        val result = persistence.File.find(fileId).get
        val zipedFiles = if (history.isZip) { getZipedFiles(datasetId, history.id) } else { Seq.empty }
        Success(DatasetData.DatasetFile(
          id = result.id,
          name = result.name,
          description = result.description,
          size = result.fileSize,
          url = AppConf.fileDownloadRoot + datasetId + "/" + result.id,
          createdBy = user,
          createdAt = timestamp.toString(),
          updatedBy = user,
          updatedAt = timestamp.toString(),
          isZip = history.isZip,
          zipedFiles = zipedFiles,
          zipCount = zipedFiles.size
        ))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  private def fileAccessabilityCheck(datasetId: String, fileId: String, user: User)(implicit s: DBSession) {
    datasetAccessabilityCheck(datasetId, user)
    if (!existsFile(datasetId, fileId)) throw new NotFoundException
  }

  private def datasetAccessabilityCheck(datasetId: String, user: User)(implicit s: DBSession) {
    getDataset(datasetId) match {
      case Some(x) =>
        if (!isOwner(user.id, datasetId)) throw new AccessDeniedException(resource.getString(ResourceNames.ONLY_ALLOW_DATASET_OWNER))
      case None =>
        throw new NotFoundException
    }
  }

  private def updateFileNameAndSize(fileId: String, historyId: String, file: FileItem, userId: String, timestamp: DateTime, s3State: Int, localState: Int)(implicit s: DBSession): Int =
  {
    withSQL {
      val f = persistence.File.column
      update(persistence.File)
        .set(
          f.name -> file.getName,
          f.fileSize -> file.getSize,
          f.updatedBy -> sqls.uuid(userId),
          f.updatedAt -> timestamp,
          f.historyId -> sqls.uuid(historyId),
          f.s3State -> s3State,
          f.localState -> localState
        )
        .where
        .eq(f.id, sqls.uuid(fileId))
    }.update().apply
  }

  /**
   * 指定したファイルのメタデータを更新します。
 *
   * @param datasetId
   * @param fileId
   * @param filename
   * @param description
   * @param user
   * @return
   */
  def updateFileMetadata(datasetId: String, fileId: String, filename: String,
                         description: String, user: User) = {
    try {
      DB localTx { implicit s =>
        fileAccessabilityCheck(datasetId, fileId, user)

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()

        updateFileNameAndDescription(fileId, datasetId, filename, description, myself.id, timestamp)

        val result = persistence.File.find(fileId).get
        val history = persistence.FileHistory.find(result.historyId).get
        val zipedFiles = if (history.isZip) { getZipedFiles(datasetId, history.id) } else { Seq.empty }
        Success(DatasetData.DatasetFile(
          id = result.id,
          name = result.name,
          description = result.description,
          size = result.fileSize,
          url = AppConf.fileDownloadRoot + datasetId + "/" + result.id,
          createdBy = user,
          createdAt = timestamp.toString(),
          updatedBy = user,
          updatedAt = timestamp.toString(),
          isZip = history.isZip,
          zipedFiles = zipedFiles,
          zipCount = zipedFiles.size
        ))
      }
    } catch {
      case e: Exception => Failure(e)
    }
  }

  private def updateFileNameAndDescription(fileId: String, datasetId:String, fileName:String, description: String,
                                           userId: String, timestamp: DateTime)(implicit s: DBSession): Int =
  {
    withSQL {
      val f = persistence.File.column
      update(persistence.File)
        .set(f.name -> fileName, f.description -> description,
          f.updatedBy -> sqls.uuid(userId), f.updatedAt -> timestamp)
        .where
        .eq(f.id, sqls.uuid(fileId))
        .and
        .eq(f.datasetId, sqls.uuid(datasetId))
    }.update().apply
  }

  /**
   * 指定したファイルを削除します。
 *
   * @param datasetId
   * @param fileId
   * @param user
   * @return
   */
  def deleteDatasetFile(datasetId: String, fileId: String, user: User): Try[Unit] = {
    try {
      DB localTx { implicit s =>
        fileAccessabilityCheck(datasetId, fileId, user)

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()

        deleteFile(datasetId, fileId, myself.id, timestamp)

        // datasetsのfiles_size, files_countの更新
        updateDatasetFileStatus(datasetId, myself.id, timestamp)

        Success(Unit)
      }
    } catch {
      case e: Exception => Failure(e)
    }
  }

  private def deleteFile(datasetId: String, fileId: String, userId: String, timestamp: DateTime)(implicit s: DBSession) {
    withSQL {
      val f = persistence.File.column
      update(persistence.File)
        .set(f.deletedBy -> sqls.uuid(userId), f.deletedAt -> timestamp,
          f.updatedBy -> sqls.uuid(userId), f.updatedAt -> timestamp)
        .where
        .eq(f.id, sqls.uuid(fileId))
        .and
        .eq(f.datasetId, sqls.uuid(datasetId))
        .and
        .isNull(f.deletedAt)
    }.update().apply
  }

  /**
   * データセットの保存先を変更する
   *
   * @param id データセットID
   * @param saveLocal ローカルに保存するか否か
   * @param saveS3 S3に保存するか否か
   * @param user ユーザオブジェクト
   * @return データセットの保存先変更タスクオブジェクト。エラーが発生した場合は、例外をFailureにつつんで返却する。発生しうる例外は、NotFoundException、AccessDeniedException、NullPointerExceptionである。
   */
  def modifyDatasetStorage(id:String, saveLocal: Boolean, saveS3: Boolean, user: User): Try[DatasetTask] = {
    try {
      CheckUtil.checkNull(id, "id")
      CheckUtil.checkNull(saveLocal, "saveLocal")
      CheckUtil.checkNull(saveS3, "saveS3")
      CheckUtil.checkNull(user, "user")
      DB localTx { implicit s =>
        datasetAccessabilityCheck(id, user)

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        val dataset = getDataset(id).get

        // S3 to local
        val taskId = if (dataset.localState == SaveStatus.NOT_SAVED && (dataset.s3State == SaveStatus.SAVED || dataset.s3State == SaveStatus.SYNCHRONIZING) && saveLocal) {
          updateDatasetStorage(
            dataset,
            myself.id,
            timestamp,
            SaveStatus.SYNCHRONIZING,
            if (saveS3) { SaveStatus.SAVED } else { SaveStatus.DELETING }
          )
          createTask(id, MoveToStatus.LOCAL, myself.id, timestamp, saveS3)
          // local to S3
        } else if ((dataset.localState == SaveStatus.SAVED || dataset.localState == SaveStatus.SYNCHRONIZING) && dataset.s3State == SaveStatus.NOT_SAVED && saveS3) {
          updateDatasetStorage(
            dataset,
            myself.id,
            timestamp,
            if (saveLocal) { SaveStatus.SAVED } else { SaveStatus.DELETING },
            SaveStatus.SYNCHRONIZING 
          )
          createTask(id, MoveToStatus.S3, myself.id, timestamp, saveLocal)
          // local, S3のいずれか削除
        } else if ((dataset.localState == SaveStatus.SAVED || dataset.localState == SaveStatus.SYNCHRONIZING) 
          && (dataset.s3State == SaveStatus.SAVED || dataset.s3State == SaveStatus.SYNCHRONIZING) && saveLocal != saveS3) {
          updateDatasetStorage(
            dataset,
            myself.id,
            timestamp,
            if (saveLocal) { SaveStatus.SAVED } else { SaveStatus.DELETING },
            if (saveS3) { SaveStatus.SAVED } else { SaveStatus.DELETING }
          )
          createTask(id, if (saveS3) { MoveToStatus.S3 } else { MoveToStatus.LOCAL } , myself.id, timestamp, false)
        } else {
          // no taskId
          "0"
        }
        Success(DatasetTask(taskId))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  private def updateDatasetStorage(ds: Dataset, userId: String, timestamp: DateTime, localState: Int, s3State: Int)(implicit s: DBSession) = {
    persistence.Dataset(
      id = ds.id,
      name = ds.name,
      description = ds.description,
      licenseId = ds.licenseId,
      filesCount = ds.filesCount,
      filesSize = ds.filesSize,
      createdBy = ds.createdBy,
      createdAt = ds.createdAt,
      updatedBy = userId,
      updatedAt = timestamp,
      localState = localState,
      s3State = s3State
    ).save()
  }

  /**
   * 指定したデータセットのメタデータを更新します。
   *
   * @param id データセットID
   * @param name データセットの名前
   * @param description データセットの説明
   * @param license データセットのライセンス
   * @param attributes データセットの属性一覧
   * @param user ユーザ情報
   * @return 更新後のデータセットのメタデータ。エラーがあれば、例外をFailureに包んで返却する。発生しうる例外は、BadRequestException、NotFoundException、NullPointerException、AccessDeniedExceptionである。
   */
  def modifyDatasetMeta(id: String, name: String, description: Option[String],
                        license: String, attributes: List[DataSetAttribute], user: User): Try[DatasetData.DatasetMetaData] = {
    try {
      CheckUtil.checkNull(id, "id")
      CheckUtil.checkNull(name, "name")
      CheckUtil.checkNull(description, "description")
      CheckUtil.checkNull(license, "license")
      CheckUtil.checkNull(attributes, "attributes")
      CheckUtil.checkNull(user, "user")
      val checkedDescription = description.getOrElse("")
      val trimmedAttributes = attributes.map(x => x.name -> StringUtil.trimAllSpaces(x.value))

      DB localTx { implicit s =>
        persistence.License.find(license) match {
          case None => throw new BadRequestException(resource.getString(ResourceNames.INVALID_LICENSEID).format(license))
          case Some(_) => // do nothing
        }

        datasetAccessabilityCheck(id, user)

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()

        updateDatasetDetail(id, name, checkedDescription, license, myself.id, timestamp)

        // 先に指定datasetに関連するannotation(name)を取得(あとで差分チェックするため)
        val oldAnnotations = getAnnotationsRelatedByDataset(id)

        // 既存DatasetAnnotation全削除
        deleteDatasetAnnotation(id)

        // annotation(name)が既存のものかチェック なければ作る
        val annotationMap = getAvailableAnnotations.toMap

        trimmedAttributes.foreach { x =>
          if (x._1.length != 0) {
            val annotationId = if (annotationMap.keySet.contains(x._1.toLowerCase)) {
              annotationMap(x._1.toLowerCase)
            } else {
              val annotationId = UUID.randomUUID().toString
              persistence.Annotation.create(
                id = annotationId,
                name = x._1,
                createdBy = myself.id,
                createdAt = timestamp,
                updatedBy = myself.id,
                updatedAt = timestamp
              )
              annotationId
            }

            // DatasetAnnotation再作成
            persistence.DatasetAnnotation.create(
              id = UUID.randomUUID().toString,
              datasetId = id,
              annotationId = annotationId,
              data = x._2,
              createdBy = myself.id,
              createdAt = timestamp,
              updatedBy = myself.id,
              updatedAt = timestamp
            )
          }
        }

        // データ追加前のnameが他で使われているかチェック 使われていなければ削除
        oldAnnotations.foreach {x =>
          if (!trimmedAttributes.map(_._1.toLowerCase).contains(x._1)) {
            val datasetAnnotations = getDatasetAnnotations(x._2)
            if (datasetAnnotations.size == 0) {
              deleteAnnotation(x._2)
            }
          }
        }
      }
      Success(
        DatasetData.DatasetMetaData(
          name = name,
          description = checkedDescription,
          license = license,
          attributes = trimmedAttributes.map{ case (name, value) => DatasetData.DatasetAttribute(name, value) }
        )
      )
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  private def getDatasetAnnotations(id: String)(implicit s: DBSession) = {
    val da = persistence.DatasetAnnotation.da
    withSQL {
      select(da.result.id)
        .from(persistence.DatasetAnnotation as da)
        .where
        .eq(da.annotationId, sqls.uuid(id))
        .and
        .isNull(da.deletedAt)
    }.map(rs => rs.string(da.resultName.id)).list().apply
  }

  private def getAvailableAnnotations(implicit s: DBSession) = {
    val a = persistence.Annotation.a
    withSQL {
      select(a.result.*)
        .from(persistence.Annotation as a)
        .where
        .isNull(a.deletedAt)
    }.map(rs => (rs.string(a.resultName.name).toLowerCase, rs.string(a.resultName.id))).list().apply
  }

  private def getAnnotationsRelatedByDataset(id: String)(implicit s: DBSession) = {
    val a = persistence.Annotation.a
    val da = persistence.DatasetAnnotation.da
    withSQL {
      select(a.result.*)
        .from(persistence.Annotation as a)
        .innerJoin(persistence.DatasetAnnotation as da).on(sqls.eq(da.annotationId, a.id).and.isNull(da.deletedAt))
        .where
        .eq(da.datasetId, sqls.uuid(id))
        .and
        .isNull(a.deletedAt)
    }.map(rs => (rs.string(a.resultName.name).toLowerCase, rs.string(a.resultName.id))).list().apply
  }

  private def deleteAnnotation(id: String)(implicit s: DBSession) = {
    withSQL {
      val a = persistence.Annotation.a
      delete.from(persistence.Annotation as a)
        .where
        .eq(a.id, sqls.uuid(id))
    }.update().apply
  }

  private def deleteDatasetAnnotation(id: String)(implicit s: DBSession) =
  {
    val da = persistence.DatasetAnnotation.da
    withSQL {
      delete.from(persistence.DatasetAnnotation as da)
        .where
        .eq(da.datasetId, sqls.uuid(id))
    }.update().apply
  }

  private def updateDatasetDetail(id: String, name: String, description: String, licenseId: String,
                                  userId: String, timestamp: DateTime)(implicit s: DBSession): Int = {
    withSQL {
      val d = persistence.Dataset.column
      update(persistence.Dataset)
        .set(d.name -> name, d.description -> description, d.licenseId -> sqls.uuid(licenseId),
          d.updatedBy -> sqls.uuid(userId), d.updatedAt -> timestamp)
        .where
        .eq(d.id, sqls.uuid(id))
    }.update().apply
  }

  /**
   * 指定したデータセットに画像を追加します。
   *
   * @param datasetId データセットID
   * @param images 追加する画像の一覧
   * @param user ユーザ情報
   * @return 追加した画像オブジェクト。エラーがあれば、例外をFailureに包んで返却する。発生しうる例外は、AccessDeniedException、NotFoundException、NullPointerExceptionである。
   */
  def addImages(datasetId: String, images: Seq[FileItem], user: User): Try[DatasetData.DatasetAddImages] = {
    try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(images, "images")
      CheckUtil.checkNull(user, "user")
      DB localTx { implicit s =>
        if (!isOwner(user.id, datasetId)) throw new AccessDeniedException(resource.getString(ResourceNames.ONLY_ALLOW_DATASET_OWNER))
        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        val primaryImage = getPrimaryImageId(datasetId)
        var isFirst = true

        val addedImages = images.map(i => {
          val imageId = UUID.randomUUID().toString
          val bufferedImage = javax.imageio.ImageIO.read(i.getInputStream)

          val image = persistence.Image.create(
            id = imageId,
            name = i.getName,
            width = bufferedImage.getWidth,
            height = bufferedImage.getWidth,
            filePath = "/" + ImageSaveLogic.uploadPath + "/" + imageId,
            presetType = PresetType.Default,
            createdBy = myself.id,
            createdAt = DateTime.now,
            updatedBy = myself.id,
            updatedAt = DateTime.now
          )
          val datasetImage = persistence.DatasetImage.create(
            id = UUID.randomUUID.toString,
            datasetId = datasetId,
            imageId = imageId,
            isPrimary = if (isFirst && primaryImage.isEmpty) true else false,
            isFeatured = false,
            createdBy = myself.id,
            createdAt = timestamp,
            updatedBy = myself.id,
            updatedAt = timestamp
          )
          isFirst = false
          // write image
          ImageSaveLogic.writeImageFile(imageId, i)
          (image, datasetImage.isPrimary)
        })

        Success(DatasetData.DatasetAddImages(
          images = addedImages.map { case (image, isPrimary) => 
            DatasetData.DatasetGetImage(
              id = image.id,
              name = image.name,
              url = datasetImageDownloadRoot + datasetId + "/" + image.id,
              isPrimary = isPrimary
            )
          },
          primaryImage = getPrimaryImageId(datasetId).getOrElse("")
        ))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  /**
   * 指定したデータセットのプライマリ画像を変更します。
 *
   * @param datasetId
   * @param imageId
   * @param user
   * @return
   */
  def changePrimaryImage(datasetId: String, imageId: String, user: User): Try[Unit] = {
    try {
      DB localTx { implicit s =>
        datasetAccessabilityCheck(datasetId, user)
        if (!existsImage(datasetId, imageId)) throw new NotFoundException

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        // 対象のイメージをPrimaryに変更
        turnImageToPrimary(datasetId, imageId, myself, timestamp)
        // 対象以外のイメージをPrimary以外に変更
        turnOffPrimaryOtherImage(datasetId, imageId, myself, timestamp)

        Success(Unit)
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  private def turnOffPrimaryOtherImage(datasetId: String, imageId: String, myself: persistence.User, timestamp: DateTime)(implicit s: DBSession) {
    withSQL {
      val di = persistence.DatasetImage.column
      update(persistence.DatasetImage)
        .set(di.isPrimary -> false, di.updatedBy -> sqls.uuid(myself.id), di.updatedAt -> timestamp)
        .where
        .ne(di.imageId, sqls.uuid(imageId))
        .and
        .eq(di.datasetId, sqls.uuid(datasetId))
        .and
        .isNull(di.deletedAt)
    }.update().apply
  }

  private def turnImageToPrimary(datasetId: String, imageId: String, myself: persistence.User, timestamp: DateTime)(implicit s: DBSession) {
    withSQL {
      val di = persistence.DatasetImage.column
      update(persistence.DatasetImage)
        .set(di.isPrimary -> true, di.updatedBy -> sqls.uuid(myself.id), di.updatedAt -> timestamp)
        .where
        .eq(di.imageId, sqls.uuid(imageId))
        .and
        .eq(di.datasetId, sqls.uuid(datasetId))
        .and
        .isNull(di.deletedAt)
    }.update().apply
  }

  /**
   * 指定したデータセットの画像を削除します。
 *
   * @param datasetId
   * @param imageId
   * @param user
   * @return
   */
  def deleteImage(datasetId: String, imageId: String, user: User) = {
    try {
      DB localTx { implicit s =>
        datasetAccessabilityCheck(datasetId, user)
        val cantDeleteImages = Seq(AppConf.defaultDatasetImageId)
        if (cantDeleteImages.contains(imageId)) throw new BadRequestException(resource.getString(ResourceNames.CANT_DELETE_DEFAULTIMAGE))
        if (!existsImage(datasetId, imageId)) throw new NotFoundException

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        deleteDatasetImage(datasetId, imageId, myself, timestamp)

        val primaryImageId = getPrimaryImageId(datasetId).getOrElse({
          // primaryImageの差し替え
          // primaryImageとなるImageを取得
          val primaryImage = findNextImage(datasetId)

          primaryImage match {
            case Some(x) =>
              turnImageToPrimaryById(x._1, myself, timestamp)
              x._2
            case None => ""
          }
        })
        val featuredImageId = getFeaturedImageId(datasetId).getOrElse({
          val featuredImage = findNextImage(datasetId)
          featuredImage match {
            case Some(x) =>
              turnImageToFeaturedById(x._1, myself, timestamp)
              x._2
            case None => ""
          }
        })

        Success(DatasetData.DatasetDeleteImage(
          primaryImage = primaryImageId,
          featuredImage = featuredImageId
        ))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  private def turnImageToPrimaryById(id:String, myself: persistence.User, timestamp: DateTime)(implicit s: DBSession) {
    val di = persistence.DatasetImage.column
    withSQL {
      update(persistence.DatasetImage)
        .set(di.isPrimary -> true, di.updatedBy -> sqls.uuid(myself.id), di.updatedAt -> timestamp)
        .where
        .eq(di.id, sqls.uuid(id))
    }.update().apply
  }

  private def turnImageToFeaturedById(id:String, myself: persistence.User, timestamp: DateTime)(implicit s: DBSession) {
    val di = persistence.DatasetImage.column
    withSQL {
      update(persistence.DatasetImage)
        .set(di.isFeatured -> true, di.updatedBy -> sqls.uuid(myself.id), di.updatedAt -> timestamp)
        .where
        .eq(di.id, sqls.uuid(id))
    }.update().apply
  }

  private def findNextImage(datasetId: String)(implicit s: DBSession): Option[(String, String)] = {
    val di = persistence.DatasetImage.di
    val i = persistence.Image.i
    withSQL {
      select(di.result.id, i.result.id)
        .from(persistence.Image as i)
        .innerJoin(persistence.DatasetImage as di).on(i.id, di.imageId)
        .where
        .eq(di.datasetId, sqls.uuid(datasetId))
        .and
        .isNull(di.deletedAt)
        .and
        .isNull(i.deletedAt)
        .orderBy(di.createdAt).asc
        .limit(1)
    }.map(rs => (rs.string(di.resultName.id), rs.string(i.resultName.id))).single().apply
  }

  private def deleteDatasetImage(datasetId: String, imageId: String, myself: persistence.User, timestamp: DateTime)(implicit s: DBSession) {
    withSQL {
      val di = persistence.DatasetImage.column
      update(persistence.DatasetImage)
        .set(di.deletedBy -> sqls.uuid(myself.id), di.deletedAt -> timestamp, di.isPrimary -> false,
          di.updatedBy -> sqls.uuid(myself.id), di.updatedAt -> timestamp)
        .where
        .eq(di.datasetId, sqls.uuid(datasetId))
        .and
        .eq(di.imageId, sqls.uuid(imageId))
        .and
        .isNull(di.deletedAt)
    }.update().apply
  }

  /**
   * 指定したデータセットのアクセスコントロールを設定します。
   *
   * @param datasetId データセットID
   * @param acl アクセスコントロール変更オブジェクトのリスト
   * @param user ユーザオブジェクト
   * @return 変更されたアクセスコントロールのリスト。エラーがあれば、例外をFailureに包んで返却する。発生しうる例外は、AccessDeniedException、BadRequestException、NotFoundException、NullPointerExceptionである。
   */
  def setAccessControl(datasetId: String, acl: List[DataSetAccessControlItem], user: User): Try[Seq[DatasetData.DatasetOwnership]] = {
    try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(acl, "acl")
      CheckUtil.checkNull(user, "user")
      DB localTx { implicit s =>
        datasetAccessabilityCheck(datasetId, user)

        val notOwnerChanges = acl.filter(x => x.ownerType == OwnerType.User && x.accessLevel != UserAndGroupAccessLevel.OWNER_OR_PROVIDER).map(_.id)
        val ownerChanges = acl.filter(x => x.ownerType == OwnerType.User && x.accessLevel == UserAndGroupAccessLevel.OWNER_OR_PROVIDER).map(_.id)
        // 更新後のオーナーの数は元々設定されているオーナーのうち、今回オーナー以外に変更されない件数と、今回オーナーに変更された件数を足したもの
        val updatedOwnerCount = getOwners(datasetId).filter(x => !notOwnerChanges.contains(x.id)).length + ownerChanges.length
        if (updatedOwnerCount == 0) {
          throw new BadRequestException(resource.getString(ResourceNames.NO_OWNER))
        }

        val ownerships = acl.map { x =>
          x.ownerType match {
            case OwnerType.User =>
              val groupId = findGroupIdByUserId(x.id)
              saveOrCreateOwnerships(user, datasetId, groupId, x.accessLevel)

              val user_ = persistence.User.find(x.id).get
              DatasetData.DatasetOwnership(
                id = user_.id,
                name = user_.name,
                fullname = user_.fullname,
                organization = user.organization,
                title = user.title,
                description = user.description,
                image = AppConf.imageDownloadRoot + "user/" + user_.id + "/" + user_.imageId,
                accessLevel = x.accessLevel,
                ownerType = OwnerType.User
              )
            case OwnerType.Group =>
              saveOrCreateOwnerships(user, datasetId, x.id, x.accessLevel)

              val group = persistence.Group.find(x.id).get
              DatasetData.DatasetOwnership(
                id = x.id,
                name = group.name,
                fullname = "",
                organization = "",
                title = "",
                description = group.description,
                image = "",
                accessLevel = x.accessLevel,
                ownerType = OwnerType.Group
              )
          }
        }
        Success(ownerships)
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  /**
   * データセットのオーナー一覧を取得する。
   * 
   * @param datasetId データセットID
   * @param s DBセッション
   * @return オーナーのユーザオブジェクトのリスト
   * @throws NullPointerException 引数がnullの場合
   */
  private def getOwners(datasetId: String)(implicit s: DBSession): Seq[persistence.User] = {
    CheckUtil.checkNull(datasetId, "datasetId")
    CheckUtil.checkNull(s, "s")
    // Usersテーブルのエイリアス
    val u = persistence.User.u
    // Membersテーブルのエイリアス
    val m = persistence.Member.m
    // Groupsテーブルのエイリアス
    val g = persistence.Group.g
    // Ownershpsテーブルのエイリアス
    val o = persistence.Ownership.o
    withSQL {
      select(u.result.*)
        .from(persistence.Ownership as o)
        .innerJoin(persistence.Group as g).on(sqls.eq(g.id, o.groupId).and.eq(g.groupType, GroupType.Personal))
        .innerJoin(persistence.Member as m).on(sqls.eq(m.groupId, g.id))
        .innerJoin(persistence.User as u).on(sqls.eq(u.id, m.userId))
        .where.eq(o.datasetId, sqls.uuid(datasetId))
        .and.eq(o.accessLevel, UserAndGroupAccessLevel.OWNER_OR_PROVIDER)
    }.map(persistence.User(u.resultName)).list().apply
  }

  def findGroupIdByUserId(userId: String)(implicit s: DBSession): String = {
    val u = persistence.User.u
    val m = persistence.Member.m
    val g = persistence.Group.g
    withSQL {
      select(g.result.id)
        .from(persistence.Group as g)
        .innerJoin(persistence.Member as m).on(sqls.eq(g.id, m.groupId).and.isNull(m.deletedAt))
        .innerJoin(persistence.User as u).on(sqls.eq(u.id, m.userId).and.isNull(u.deletedAt))
        .where
        .eq(u.id, sqls.uuid(userId))
        .and
        .eq(g.groupType, GroupType.Personal)
        .and
        .isNull(g.deletedAt)
        .and
        .isNull(m.deletedAt)
        .limit(1)
    }.map(rs => rs.string(g.resultName.id)).single().apply.get
  }

  /**
   * 指定したデータセットのゲストアクセスレベルを設定します。
   *
   * @param datasetId データセットID
   * @param accessLevel 設定するゲストアクセスレベル
   * @param user ユーザ情報
   * @return 設定したゲストアクセスレベル。エラーがあれば、例外をFailureに包んで返却する。発生しうる例外は、NotFoundException、AccessDeniedException、NullPointerExceptionである。
   */
  def setGuestAccessLevel(datasetId: String, accessLevel: Int, user: User): Try[DatasetData.DatasetGuestAccessLevel] = {
    try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(accessLevel, "accessLevel")
      CheckUtil.checkNull(user, "user")
      DB localTx { implicit s =>
        datasetAccessabilityCheck(datasetId, user)

        findGuestOwnership(datasetId) match {
          case Some(x) =>
            if (accessLevel != x.accessLevel) {
              persistence.Ownership(
                id = x.id,
                datasetId = x.datasetId,
                groupId = AppConf.guestGroupId,
                accessLevel = accessLevel,
                createdBy = x.createdBy,
                createdAt = x.createdAt,
                updatedBy = user.id,
                updatedAt = DateTime.now
              ).save()
            }
          case None =>
            if (accessLevel > 0) {
              val ts = DateTime.now
              persistence.Ownership.create(
                id = UUID.randomUUID.toString,
                datasetId = datasetId,
                groupId = AppConf.guestGroupId,
                accessLevel = accessLevel,
                createdBy = user.id,
                createdAt = ts,
                updatedBy = user.id,
                updatedAt = ts
              )
            }
        }
        Success(DatasetData.DatasetGuestAccessLevel(accessLevel))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  private def findGuestOwnership(datasetId: String)(implicit s: DBSession): Option[persistence.Ownership] = {
    val o = persistence.Ownership.o
    withSQL(
      select(o.result.*)
        .from(persistence.Ownership as o)
        .where
        .eq(o.datasetId, sqls.uuid(datasetId))
        .and
        .eq(o.groupId, sqls.uuid(AppConf.guestGroupId))
    ).map(persistence.Ownership(o.resultName)).single.apply
  }

  /**
   * 指定したデータセットを削除します。
 *
   * @param datasetId
   * @param user
   * @return
   */
  def deleteDataset(datasetId: String, user: User): Try[Unit] = {
    try {
      DB localTx { implicit s =>
        datasetAccessabilityCheck(datasetId, user)
        deleteDatasetById(datasetId, user)
      }
      Success(Unit)
    } catch {
      case e:Throwable => Failure(e)
    }
  }

  private def deleteDatasetById(datasetId: String, user: User)(implicit s: DBSession): Int = {
    val timestamp = DateTime.now()
    val d = persistence.Dataset.column
    withSQL {
      update(persistence.Dataset)
        .set(d.deletedAt -> timestamp, d.deletedBy -> sqls.uuid(user.id))
        .where
        .eq(d.id, sqls.uuid(datasetId))
    }.update().apply
  }

  private def isOwner(userId: String, datasetId: String)(implicit s: DBSession) = {
    val o = persistence.Ownership.o
    val g = persistence.Group.g
    val m = persistence.Member.m
    val u = persistence.User.u
    val d = persistence.Dataset.d
    withSQL {
      select(sqls"1")
        .from(persistence.Ownership as o)
        .innerJoin(persistence.Group as g).on(sqls.eq(o.groupId, g.id).and.isNull(g.deletedAt))
        .innerJoin(persistence.Member as m).on(sqls.eq(g.id, m.groupId).and.isNull(m.deletedAt))
        .innerJoin(persistence.User as u).on(sqls.eq(u.id, m.userId).and.isNull(u.deletedAt))
        .innerJoin(persistence.Dataset as d).on(sqls.eq(o.datasetId, d.id).and.isNull(d.deletedAt))
        .where
          .eq(u.id, sqls.uuid(userId))
          .and
          .eq(d.id, sqls.uuid(datasetId))
          .and
          .eq(g.groupType, persistence.GroupType.Personal)
          .and
          .eq(o.accessLevel, persistence.UserAccessLevel.Owner)
          .and
          .isNull(o.deletedAt)
        .limit(1)
    }.map(x => true).single.apply().getOrElse(false)
  }

  def getJoinedGroups(user: User)(implicit s: DBSession): Seq[String] = {
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
  sealed trait FileResult {
    val file: persistence.File
    val path: String
  }
  case class FileResultNormal(file: persistence.File, path: String) extends FileResult
  case class FileResultZip(file: persistence.File, path: String, zipFile: persistence.ZipedFiles) extends FileResult
  def findFile(fileId: String)(implicit session: DBSession): Option[FileResult] = {
    persistence.File.find(fileId).map { file =>
      val history = persistence.FileHistory.find(file.historyId).get
      FileResultNormal(file, history.filePath)
    }.orElse {
      persistence.ZipedFiles.find(fileId).map { zipFile =>
        val history = persistence.FileHistory.find(zipFile.historyId).get
        val file = persistence.File.find(history.fileId).get
        FileResultZip(file, history.filePath, zipFile)
      }
    }
  }

  /**
   * 対象のユーザが対象のデータセットのダウンロード権限を持つかをチェックする。
   * @param user ユーザ情報
   * @param datasetId データセットID
   * @return 権限がない場合、AccessDeniedExceptionをFailureに包んで返却する。
   * @throws NullPointerException 引数がnullの場合
   */
  def requireAllowDownload(user: User, datasetId: String)(implicit s: DBSession): Try[Unit] = {
    CheckUtil.checkNull(user, "user")
    CheckUtil.checkNull(datasetId, "datasetId")
    CheckUtil.checkNull(s, "s")
    val permission = if (user.isGuest) {
      getGuestAccessLevel(datasetId)
    } else {
      val groups = getJoinedGroups(user)
      // FIXME チェック時、user権限はUserAccessLevelクラス, groupの場合はGroupAccessLevelクラスの定数を使用する
      // 旧仕様ではuser/groupは同じ権限を付与していたが、
      // 現仕様はuser/groupによって権限の扱いが異なる(groupには編集権限は付与しない)
      // 実装時間の都合と現段階の実装でも問題がない(値が同じ)ため対応していない
      getPermission(datasetId, groups)
    }
    if (permission < UserAndGroupAllowDownload) {
      return Failure(new AccessDeniedException(resource.getString(ResourceNames.NO_DOWNLOAD_PERMISSION)))
    }
    Success(())
  }

  def found[T](opt: Option[T]): Try[T] = {
    opt match {
      case Some(x) => Success(x)
      case None => Failure(new NotFoundException)
    }
  }
  def hasPassword(zipedFile: persistence.ZipedFiles): Boolean = {
    (zipedFile.cenHeader(8) & 0x01) == 1
  }
  def requireNotWithPassword(file: FileResult): Try[Unit] = {
    file match {
      case FileResultNormal(_, _) => Success(())
      case FileResultZip(_, _, zipedFile) => {
        if (hasPassword(zipedFile)) {
          Failure(new NotFoundException)
        } else {
          Success(())
        }
      }
    }
  }

  def createRangeInputStream(path: Path, offset: Long, limit: Long): InputStream = {
    val is = Files.newInputStream(path)
    try {
      is.skip(offset)
      new BoundedInputStream(is, limit)
    } catch {
      case e: Exception => {
        is.close()
        throw e
      }
    }
  }
  def createUnzipInputStream(data: InputStream, centralHeader: Array[Byte], dataSize: Long, encoding: Charset): InputStream = {
    val footer = createFooter(centralHeader, dataSize)
    val sis = new SequenceInputStream(data, new ByteArrayInputStream(footer))
    val zis = new ZipInputStream(sis, encoding)
    zis.getNextEntry
    zis
  }
  def createFooter(centralHeader: Array[Byte], dataSize: Long): Array[Byte] = {
    val centralHeaderSize = centralHeader.length
    val zip64EndOfCentralDirectoryRecord = if (dataSize < 0x00000000FFFFFFFFL) {
      Array.empty[Byte]
    } else {
      Array.concat(
        Array[Byte] (
          0x50, 0x4b, 0x06, 0x06, // sig
          44, 0, 0, 0, 0, 0, 0, 0, // size of this record - 12
          45, 0, 45, 0, // version
          0, 0, 0, 0, 0, 0, 0, 0, // disk
          1, 0, 0, 0, 0, 0, 0, 0, // total entity num on this disk
          1, 0, 0, 0, 0, 0, 0, 0 // total entity num
        ),
        longToByte8(centralHeaderSize),
        longToByte8(dataSize)
      )
    }
    val zip64EndOfCentralDirectoryLocator = if (dataSize < 0x00000000FFFFFFFFL) {
      Array.empty[Byte]
    } else {
      Array.concat(
        Array[Byte] (
          0x50, 0x4b, 0x06, 0x07, // sig
          0, 0, 0, 0 // disk
        ),
        longToByte8(dataSize + centralHeaderSize),
        Array[Byte] (
          1, 0, 0, 0 // total disk num
        )
      )
    }
    val endOfCentralDirectoryRecord = Array.concat(
      Array[Byte] (
        0x50, 0x4b, 0x05, 0x06,
        0, 0, 0, 0,
        1, 0, 1, 0
      ),
      longToByte4(centralHeaderSize),
      longToByte4(scala.math.min(dataSize, 0x00000000FFFFFFFFL)),
      Array[Byte](0, 0)
    )
    Array.concat(
      centralHeader,
      zip64EndOfCentralDirectoryRecord,
      zip64EndOfCentralDirectoryLocator,
      endOfCentralDirectoryRecord
    )
  }
  private def isSJIS(str: String): Boolean = {
    try {
      val encoded = new String(str.getBytes("SHIFT_JIS"), "SHIFT_JIS")
      encoded.equals(str)
    } catch {
      case e: Exception => false
    }
  }
  def longToByte4(num: Long): Array[Byte] = {
    Array[Long](
      (num & 0x00000000000000FFL),
      (num & 0x000000000000FF00L) >> 8,
      (num & 0x0000000000FF0000L) >> 16,
      (num & 0x00000000FF000000L) >> 24
    ).map(_.toByte)
  }
  def longToByte8(num: Long): Array[Byte] = {
    Array[Long](
      (num & 0x00000000000000FFL),
      (num & 0x000000000000FF00L) >> 8,
      (num & 0x0000000000FF0000L) >> 16,
      (num & 0x00000000FF000000L) >> 24,
      (num & 0x000000FF00000000L) >> 32,
      (num & 0x0000FF0000000000L) >> 40,
      (num & 0x00FF000000000000L) >> 48,
      (num & 0xFF00000000000000L) >> 56
    ).map(_.toByte)
  }

  private def getFileHistory(fileId: String)(implicit s: DBSession): Option[String] = {
    val fh = persistence.FileHistory.syntax("fh")
    val filePath = withSQL {
      select(fh.result.filePath)
        .from(persistence.FileHistory as fh)
        .where
        .eq(fh.fileId, sqls.uuid(fileId))
        .and
        .isNull(fh.deletedAt)
    }.map(rs => rs.string(fh.resultName.filePath)).single().apply
    filePath
  }

  private def getPersonalGroup(userId: String)(implicit s: DBSession) = {
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

  private def getDataset(id: String)(implicit s: DBSession) = {
    if (StringUtil.isUUID(id)) {
      val d = persistence.Dataset.syntax("d")
      withSQL {
        select(d.result.*)
          .from(persistence.Dataset as d)
          .where
            .eq(d.id, sqls.uuid(id))
            .and
            .isNull(d.deletedAt)
      }.map(persistence.Dataset(d.resultName)).single.apply()
    } else {
      None
    }
  }

  /**
   * 対象のデータセットに対する、指定したグループが持つ最も強い権限を取得する。
   *
   * @param id データセットID
   * @param groups グループのリスト
   * @param s DBセッション
   * @return 対象のデータセットに対する最も強い権限の値
   * @throws NullPointerException 引数がnullの場合
   */
  private def getPermission(id: String, groups: Seq[String])(implicit s: DBSession): Int = {
    CheckUtil.checkNull(id, "id")
    CheckUtil.checkNull(groups, "groups")
    CheckUtil.checkNull(s, "s")
    val o = persistence.Ownership.syntax("o")
    val g = persistence.Group.g
    val permissions = withSQL {
      select(o.result.accessLevel, g.result.groupType)
        .from(persistence.Ownership as o)
        .innerJoin(persistence.Group as g).on(o.groupId, g.id)
        .where
          .eq(o.datasetId, sqls.uuid(id))
          .and
          .inUuid(o.groupId, Seq.concat(groups, Seq(AppConf.guestGroupId)))
    }.map(rs => (rs.int(o.resultName.accessLevel), rs.int(g.resultName.groupType))).list().apply
    // 上記のSQLではゲストユーザーは取得できないため、別途取得する必要がある
    val guestPermission = (getGuestAccessLevel(id), GroupType.Personal)
    (guestPermission :: permissions).map{ case (accessLevel, groupType) =>
      // Provider権限のGroupはWriteできない
      if (accessLevel == GroupAccessLevel.Provider && groupType == GroupType.Public) {
        UserAndGroupAccessLevel.ALLOW_DOWNLOAD
      } else {
        accessLevel
      }
    }.max
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

  private def getOwnerGroups(datasetIds: Seq[String], userInfo: User)(implicit s: DBSession):Map[String, Seq[DatasetData.DatasetOwnership]] = {
    if (datasetIds.nonEmpty) {
      val o = persistence.Ownership.o
      val g = persistence.Group.g
      val m = persistence.Member.m
      val u = persistence.User.u
      val gi = persistence.GroupImage.gi
      val owners = withSQL {
        select(o.result.*, g.result.*, u.result.*, gi.result.*)
          .from(persistence.Ownership as o)
          .innerJoin(persistence.Group as g)
            .on(sqls.eq(g.id, o.groupId).and.isNull(g.deletedAt))
          .leftJoin(persistence.Member as m)
            .on(sqls.eq(g.id, m.groupId)
                .and.eq(g.groupType, persistence.GroupType.Personal)
                .and.eq(m.role, persistence.GroupMemberRole.Manager)
                .and.isNull(m.deletedAt))
          .leftJoin(persistence.User as u)
            .on(sqls.eq(m.userId, u.id).and.isNull(u.deletedAt))
          .leftJoin(persistence.GroupImage as gi)
            .on(sqls.eq(g.id, gi.groupId).and.eq(gi.isPrimary, true).and.isNull(gi.deletedAt))
          .where
            .inUuid(o.datasetId, datasetIds)
            .and
            .append(sqls"(")
              .append(sqls"(")
                .eq(g.groupType, GroupType.Personal)
                .and
                .eq(o.accessLevel, UserAccessLevel.Owner)
              .append(sqls")")
              .or
              .append(sqls"(")
                .eq(g.groupType, GroupType.Public)
                .and
                .eq(o.accessLevel, GroupAccessLevel.Provider)
              .append(sqls")")
            .append(sqls")")
            .and
            .isNull(o.deletedAt)
      }.map(rs =>
        (
          rs.string(o.resultName.datasetId),
          DatasetData.DatasetOwnership(
            id = rs.stringOpt(u.resultName.id).getOrElse(rs.string(g.resultName.id)),
            name = rs.stringOpt(u.resultName.name).getOrElse(rs.string(g.resultName.name)),
            fullname = rs.stringOpt(u.resultName.fullname).getOrElse(""),
            organization = rs.stringOpt(u.resultName.organization).getOrElse(""),
            title = rs.stringOpt(u.resultName.title).getOrElse(""),
            description = rs.stringOpt(u.resultName.description).getOrElse(""),
            image = AppConf.imageDownloadRoot +
              (if (rs.stringOpt(u.resultName.id).isEmpty) { "groups/" } else { "user/" }) +
              rs.stringOpt(u.resultName.id).getOrElse(rs.string(g.resultName.id)) + "/" +
              rs.stringOpt(u.resultName.imageId).getOrElse(rs.string(gi.resultName.imageId)),
            accessLevel = rs.int(o.resultName.accessLevel),
            ownerType = rs.stringOpt(u.resultName.id) match {
              case Some(x) => OwnerType.User
              case None => OwnerType.Group
            }
          )
        )
      ).list().apply()
      .groupBy(_._1)
      .map(x => (x._1, x._2.map(_._2)))

      // グループ、ログインユーザー(あれば)、他のユーザーの順にソート
      // mutable map使用
      val sortedOwners = scala.collection.mutable.Map.empty[String, Seq[DatasetData.DatasetOwnership]]
      owners.foreach{x =>
        val groups = x._2.filter(_.ownerType == OwnerType.Group).sortBy(_.fullname)
        val loginUser = x._2.filter(_.id == userInfo.id)
        val other = x._2.diff(groups).diff(loginUser).sortBy(_.fullname)
        sortedOwners.put(x._1, groups ++ loginUser ++ other)
      }
      // mutable -> immutable
      sortedOwners.toMap
    } else {
      Map.empty
    }
  }

  private def getAllOwnerships(datasetId: String, userInfo: User)(implicit s: DBSession) = {
    // ゲストアカウント情報はownersテーブルに存在しないので、このメソッドからは取得されない
    val o = persistence.Ownership.o
    val g = persistence.Group.g
    val m = persistence.Member.m
    val u = persistence.User.u
    val gi = persistence.GroupImage.gi
    val owners = withSQL {
      select(o.result.*, g.result.*, u.result.*, gi.result.*)
        .from(persistence.Ownership as o)
        .innerJoin(persistence.Group as g)
          .on(sqls.eq(g.id, o.groupId).and.isNull(g.deletedAt))
        .leftJoin(persistence.Member as m)
          .on(sqls.eq(g.id, m.groupId)
              .and.eq(g.groupType, persistence.GroupType.Personal)
              .and.eq(m.role, persistence.GroupMemberRole.Manager)
              .and.isNull(m.deletedAt))
        .leftJoin(persistence.User as u)
          .on(sqls.eq(m.userId, u.id).and.isNull(u.deletedAt))
        .leftJoin(persistence.GroupImage as gi)
          .on(sqls.eq(g.id, gi.groupId).and.eq(gi.isPrimary, true).and.isNull(gi.deletedAt))
        .where
          .eq(o.datasetId, sqls.uuid(datasetId))
          .and
          .append(sqls"(")
            .append(sqls"(")
              .eq(g.groupType, GroupType.Personal)
              .and
              .gt(o.accessLevel, UserAccessLevel.Deny)
            .append(sqls")")
            .or
            .append(sqls"(")
              .eq(g.groupType, GroupType.Public)
              .and
              .gt(o.accessLevel, GroupAccessLevel.Deny)
            .append(sqls")")
          .append(sqls")")
          .and
          .isNull(o.deletedAt)
    }.map(rs =>
      DatasetData.DatasetOwnership(
        id = rs.stringOpt(u.resultName.id).getOrElse(rs.string(g.resultName.id)),
        name = rs.stringOpt(u.resultName.name).getOrElse(rs.string(g.resultName.name)),
        fullname = rs.stringOpt(u.resultName.fullname).getOrElse(""),
        organization = rs.stringOpt(u.resultName.organization).getOrElse(""),
        title = rs.stringOpt(u.resultName.title).getOrElse(""),
        description = rs.stringOpt(u.resultName.description).getOrElse(""),
        image = AppConf.imageDownloadRoot +
          (if (rs.stringOpt(u.resultName.id).isEmpty) { "groups/" } else { "user/" }) +
          rs.stringOpt(u.resultName.id).getOrElse(rs.string(g.resultName.id)) + "/" +
          rs.stringOpt(u.resultName.imageId).getOrElse(rs.string(gi.resultName.imageId)),
        accessLevel = rs.int(o.resultName.accessLevel),
        ownerType = rs.stringOpt(u.resultName.id) match {
          case Some(x) => OwnerType.User
          case None => OwnerType.Group
        }
      )
    ).list().apply()
    // ソート(ログインユーザーがownerであればそれが一番最初に、それ以外はアクセスレベル→ownerTypeの順に降順に並ぶ)
    // ログインユーザーとそれ以外のownershipsとで分ける
    val owner = owners.filter(x => x.id == userInfo.id && x.accessLevel == UserAccessLevel.Owner)
    val partial = owners.diff(owner)

    // accessLevel, ownerTypeから順序付け用重みを計算してソート
    val sortedPartial = partial.map(x => (x, x.accessLevel * 10 - x.ownerType))
        .sortBy(s => (s._2, s._1.fullname)).reverse.map(_._1)
    owner ++ sortedPartial
  }

  private def getAttributes(datasetId: String)(implicit s: DBSession) = {
    val da = persistence.DatasetAnnotation.syntax("da")
    val a = persistence.Annotation.syntax("d")
    withSQL {
      select(da.result.*, a.result.*)
      .from(persistence.DatasetAnnotation as da)
      .innerJoin(persistence.Annotation as a).on(sqls.eq(da.annotationId, a.id).and.isNull(a.deletedAt))
      .where
      .eq(da.datasetId, sqls.uuid(datasetId))
      .and
      .isNull(da.deletedAt)
    }.map(rs =>
      (
        persistence.DatasetAnnotation(da.resultName)(rs),
        persistence.Annotation(a.resultName)(rs)
        )
      ).list.apply.map(x =>
        DatasetData.DatasetAttribute(
          name = x._2.name,
          value = x._1.data
        )
      )
  }

  private def getImages(datasetId: String)(implicit s: DBSession) = {
    val di = persistence.DatasetImage.di
    val i = persistence.Image.i

    withSQL {
      select(di.result.*, i.result.*)
        .from(persistence.DatasetImage as di)
        .innerJoin(persistence.Image as i).on(di.imageId, i.id)
        .where
          .eq(di.datasetId, sqls.uuid(datasetId))
          .and
          .isNull(di.deletedAt)
          .and
          .isNull(i.deletedAt)
        .orderBy(i.name, i.createdAt)
    }.map(rs =>
      (
        persistence.DatasetImage(di.resultName)(rs),
        persistence.Image(i.resultName)(rs)
        )
      ).list.apply.map(x =>
        Image(
          id = x._2.id,
          url = datasetImageDownloadRoot + datasetId + "/" + x._2.id
        )
      )
  }

  private def getFiles(datasetId: String, limit: Int, offset: Int)(implicit s: DBSession): Seq[DatasetData.DatasetFile] = {
    val f = persistence.File.f
    val u1 = persistence.User.syntax("u1")
    val u2 = persistence.User.syntax("u2")
    val ma1 = persistence.MailAddress.syntax("ma1")
    val ma2 = persistence.MailAddress.syntax("ma2")
    withSQL {
      select(f.result.*, u1.result.*, u2.result.*, ma1.result.address, ma2.result.address)
        .from(persistence.File as f)
        .innerJoin(persistence.User as u1).on(f.createdBy, u1.id)
        .innerJoin(persistence.User as u2).on(f.updatedBy, u2.id)
        .innerJoin(persistence.MailAddress as ma1).on(u1.id, ma1.userId)
        .innerJoin(persistence.MailAddress as ma2).on(u2.id, ma2.userId)
        .where
          .eq(f.datasetId, sqls.uuid(datasetId))
          .and
          .isNull(f.deletedAt)
        .orderBy(f.name, f.createdAt)
        .offset(offset)
        .limit(limit)
    }.map(rs =>
      (
        persistence.File(f.resultName)(rs),
        persistence.User(u1.resultName)(rs),
        persistence.User(u2.resultName)(rs),
        rs.string(ma1.resultName.address),
        rs.string(ma2.resultName.address)
      )
    ).list.apply.map(x =>{
      val history = persistence.FileHistory.find(x._1.historyId).get
      val zipCount = if (history.isZip) { getZipedFiles(datasetId, history.id).size } else { 0 }
      DatasetData.DatasetFile(
        id = x._1.id,
        name = x._1.name,
        description = x._1.description,
        url = AppConf.fileDownloadRoot + datasetId + "/" + x._1.id,
        size = x._1.fileSize,
        createdBy = User(x._2, x._4),
        createdAt = x._1.createdAt.toString(),
        updatedBy = User(x._3, x._5),
        updatedAt = x._1.updatedAt.toString(),
        isZip = history.isZip,
        zipedFiles = Seq.empty,
        zipCount = zipCount
      )
    }
    )
  }

  def getFileAmount(datasetId: String)(implicit s: DBSession): Int = {
    val f = persistence.File.f
    val u1 = persistence.User.syntax("u1")
    val u2 = persistence.User.syntax("u2")
    val ma1 = persistence.MailAddress.syntax("ma1")
    val ma2 = persistence.MailAddress.syntax("ma2")
    withSQL {
      select(sqls.count)
        .from(persistence.File as f)
        .innerJoin(persistence.User as u1).on(f.createdBy, u1.id)
        .innerJoin(persistence.User as u2).on(f.updatedBy, u2.id)
        .innerJoin(persistence.MailAddress as ma1).on(u1.id, ma1.userId)
        .innerJoin(persistence.MailAddress as ma2).on(u2.id, ma2.userId)
        .where
          .eq(f.datasetId, sqls.uuid(datasetId))
          .and
          .isNull(f.deletedAt)
    }.map(_.int(1)).single.apply.getOrElse(0)
  }

  def getZipedFiles(datasetId: String, historyId: String)(implicit s: DBSession): Seq[DatasetZipedFile] = {
    val zf = persistence.ZipedFiles.zf
    val zipedFiles = withSQL {
      select
      .from(ZipedFiles as zf)
      .where
        .eq(zf.historyId, sqls.uuid(historyId))
    }.map(persistence.ZipedFiles(zf.resultName)).list.apply
    if (zipedFiles.exists(hasPassword)) {
      return Seq.empty
    }
    zipedFiles.map{x =>
      DatasetZipedFile(
        id = x.id,
        name = x.name,
        size = x.fileSize,
        url = AppConf.fileDownloadRoot + datasetId + "/" + x.id
      )
    }.toSeq
  }

  private def updateDatasetFileStatus(datasetId: String, userId:String, timestamp: DateTime)(implicit s: DBSession) = {
    val f = persistence.File.f
    val allFiles = withSQL {
      select(f.result.*)
        .from(persistence.File as f)
        .where
        .eq(f.datasetId, sqls.uuid(datasetId))
        .and
        .isNull(f.deletedAt)
    }.map(persistence.File(f.resultName)).list().apply
    val totalFileSize = allFiles.foldLeft(0L)((a: Long, b: persistence.File) => a + b.fileSize)

    withSQL {
      val d = persistence.Dataset.column
      update(persistence.Dataset)
        .set(d.filesCount -> allFiles.size, d.filesSize -> totalFileSize,
          d.updatedBy -> sqls.uuid(userId), d.updatedAt -> timestamp)
        .where
        .eq(d.id, sqls.uuid(datasetId))
    }.update().apply
  }

  private def getPrimaryImageId(datasetId: String)(implicit s: DBSession) = {
    val di = persistence.DatasetImage.syntax("di")
    val i = persistence.Image.syntax("i")
    withSQL {
      select(i.result.id)
        .from(persistence.Image as i)
        .innerJoin(persistence.DatasetImage as di).on(i.id, di.imageId)
        .where
        .eq(di.datasetId, sqls.uuid(datasetId))
        .and
        .eq(di.isPrimary, true)
        .and
        .isNull(di.deletedAt)
        .and
        .isNull(i.deletedAt)
    }.map(rs => rs.string(i.resultName.id)).single().apply
  }

  private def getFeaturedImageId(datasetId: String)(implicit s: DBSession) = {
    val di = persistence.DatasetImage.syntax("di")
    val i = persistence.Image.syntax("i")
    withSQL {
      select(i.result.id)
        .from(persistence.Image as i)
        .innerJoin(persistence.DatasetImage as di).on(i.id, di.imageId)
        .where
        .eq(di.datasetId, sqls.uuid(datasetId))
        .and
        .eq(di.isFeatured, true)
        .and
        .isNull(di.deletedAt)
        .and
        .isNull(i.deletedAt)
    }.map(rs => rs.string(i.resultName.id)).single().apply
  }

  private def getAccessCount(datasetId: String)(implicit s: DBSession): Long = {
    val dal = persistence.DatasetAccessLog.dal
    persistence.DatasetAccessLog.countBy(sqls.eqUuid(dal.datasetId, datasetId))
  }

  private def existsImage(datasetId: String, imageId: String)(implicit s: DBSession) = {
    val i = persistence.Image.syntax("i")
    val di = persistence.DatasetImage.syntax("di")
    withSQL {
      select(i.result.id)
        .from(persistence.Image as i)
        .innerJoin(persistence.DatasetImage as di).on(i.id, di.imageId)
        .where
        .eq(di.datasetId, sqls.uuid(datasetId))
        .and
        .eq(i.id, sqls.uuid(imageId))
        .and
        .isNull(di.deletedAt)
        .and
        .isNull(i.deletedAt)
    }.map(rs => rs.string(i.resultName.id)).single().apply match {
      case Some(x) => true
      case None => false
    }
  }

  private def existsFile(datasetId: String, fileId: String)(implicit s: DBSession) = {
    val f = persistence.File.syntax("f")
    val d = persistence.Dataset.syntax("d")
    withSQL {
      select(f.result.id)
        .from(persistence.File as f)
        .innerJoin(persistence.Dataset as d).on(d.id, f.datasetId)
        .where
        .eq(f.datasetId, sqls.uuid(datasetId))
        .and
        .eq(f.id, sqls.uuid(fileId))
        .and
        .isNull(f.deletedAt)
        .and
        .isNull(d.deletedAt)
    }.map(rs => rs.string(f.resultName.id)).single().apply.isDefined
  }

  private def saveOrCreateOwnerships(userInfo: User, datasetId: String, groupId: String, accessLevel: Int)(implicit s: DBSession) {
    val myself = persistence.User.find(userInfo.id).get
    val timestamp = DateTime.now()

    val o = persistence.Ownership.o
    withSQL(
      select(o.result.*)
        .from(persistence.Ownership as o)
        .where
        .eq(o.datasetId, sqls.uuid(datasetId))
        .and
        .eq(o.groupId, sqls.uuid(groupId))
    ).map(persistence.Ownership(o.resultName)).single.apply match {
      case Some(x) =>
        if (accessLevel != x.accessLevel) {
          persistence.Ownership(
            id = x.id,
            datasetId = x.datasetId,
            groupId = x.groupId,
            accessLevel = accessLevel,
            createdBy = myself.id,
            createdAt = x.createdAt,
            updatedBy = myself.id,
            updatedAt = timestamp
          ).save()
        }
      case None =>
        if (accessLevel > 0) {
          persistence.Ownership.create(
            id = UUID.randomUUID.toString,
            datasetId = datasetId,
            groupId = groupId,
            accessLevel = accessLevel,
            createdBy = myself.id,
            createdAt = timestamp,
            updatedBy = myself.id,
            updatedAt = timestamp
          )
        }
    }
  }

  def copyDataset(datasetId: String, user: User): Try[CopiedDataset] = {
    try {
      DB localTx { implicit s =>
        datasetAccessabilityCheck(datasetId, user)

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()

        val newDatasetId = UUID.randomUUID.toString
        val dataset = persistence.Dataset.find(datasetId).get
        persistence.Dataset.create(
          id = newDatasetId,
          name = "Copy of " + dataset.name,
          description = dataset.description,
          licenseId = dataset.licenseId,
          filesCount = 0,
          filesSize = 0,
          localState = dataset.localState,
          s3State = dataset.s3State,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp
        )

        val da = persistence.DatasetAnnotation.da
        val annotations = withSQL {
          select
            .from(DatasetAnnotation as da)
            .where
              .eq(da.datasetId, sqls.uuid(datasetId))
        }.map(persistence.DatasetAnnotation(da.resultName)).list().apply

        annotations.foreach { annotation =>
          persistence.DatasetAnnotation.create(
            id = UUID.randomUUID().toString,
            datasetId = newDatasetId,
            annotationId = annotation.annotationId,
            data = annotation.data,
            createdBy = myself.id,
            createdAt = timestamp,
            updatedBy = myself.id,
            updatedAt = timestamp
          )
        }

        val di = persistence.DatasetImage.di
        val images = withSQL {
          select
            .from(DatasetImage as di)
            .where
              .eq(di.datasetId, sqls.uuid(datasetId))
        }.map(persistence.DatasetImage(di.resultName)).list().apply

        images.foreach { image =>
          persistence.DatasetImage.create(
            id = UUID.randomUUID().toString,
            datasetId = newDatasetId,
            imageId = image.imageId,
            isPrimary = image.isPrimary,
            isFeatured = image.isFeatured,
            createdBy = myself.id,
            createdAt = timestamp,
            updatedBy = myself.id,
            updatedAt = timestamp
          )
        }

        val o = persistence.Ownership.o
        val ownerships = withSQL {
          select
            .from(Ownership as o)
            .where
              .eq(o.datasetId, sqls.uuid(datasetId))
        }.map(persistence.Ownership(o.resultName)).list().apply

        ownerships.foreach { ownership =>
          persistence.Ownership.create(
            id = UUID.randomUUID().toString,
            datasetId = newDatasetId,
            groupId = ownership.groupId,
            accessLevel = ownership.accessLevel,
            createdBy = myself.id,
            createdAt = timestamp,
            updatedBy = myself.id,
            updatedAt = timestamp
          )
        }

        Success(CopiedDataset(newDatasetId))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  def importAttribute(datasetId: String, file: FileItem, user: User): Try[Unit] = {
    try
    {
      val csv = use(new InputStreamReader(file.getInputStream)) { in =>
        CSVReader.open(in).all()
      }
      val nameMap = csv.map(x => (x(0), x(1))).toMap

      DB localTx { implicit s =>
        val a = persistence.Annotation.a
        val da = persistence.DatasetAnnotation.da
        val exists = withSQL {
          select
            .from(Annotation as a)
            .where
              .in(a.name, csv.map(_(0)))
        }.map(persistence.Annotation(a.resultName)).list().apply()

        val notExists = csv.filter(x => ! exists.map(_.name).contains(x(0)))
        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()

        val created = notExists.map { annotation =>
          persistence.Annotation.create(
            id = UUID.randomUUID().toString,
            name = annotation(0),
            createdBy = myself.id,
            createdAt = timestamp,
            updatedBy = myself.id,
            updatedAt = timestamp
          )
        }

        val existRels = withSQL {
          select
            .from(DatasetAnnotation as da)
            .join(Annotation as a).on(da.annotationId, a.id)
            .where
              .eqUuid(da.datasetId, datasetId)
              .and
              .in(a.name, exists.map(_.name))
        }.map(persistence.DatasetAnnotation(da.resultName)).list().apply

        (exists.filter(x => ! existRels.map(_.annotationId).contains(x.id)) ++ created).foreach { annotation =>

          persistence.DatasetAnnotation.create(
            id = UUID.randomUUID().toString,
            datasetId = datasetId,
            annotationId = annotation.id,
            data = nameMap(annotation.name),
            createdBy = myself.id,
            createdAt = timestamp,
            updatedBy = myself.id,
            updatedAt = timestamp
          )
        }

        Success(Unit)
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  private def use[T1 <: Closeable, T2](resource: T1)(f: T1 => T2): T2 = {
    try {
      f(resource)
    } finally {
      try {
        resource.close()
      } catch {
        case e: Exception =>
      }
    }
  }

  /**
   * 属性をcsv形式で取得する。
   *
   * @param datasetId データセットID
   * @param user ユーザ情報
   * @return CSVファイル。エラーがあれば、例外をFailureに包んで返却する。発生しうる例外は、NotFoundException、NullPointerException、AccessDeniedExceptionである。
   */
  def exportAttribute(datasetId: String, user: User): Try[java.io.File] = {
    try
    {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(user, "user")
      DB readOnly { implicit s =>
        checkReadPermission(datasetId, user)
        val a = persistence.Annotation.a
        val da = persistence.DatasetAnnotation.da
        val attributes = withSQL {
          select
            .from(Annotation as a)
            .join(DatasetAnnotation as da).on(a.id, da.annotationId)
            .where
              .eq(da.datasetId, sqls.uuid(datasetId))
        }.map(rs => List(rs.string(a.resultName.name), rs.string(da.resultName.data)).mkString(",") + System.getProperty("line.separator")).list().apply()

        val file = Paths.get(AppConf.tempDir, "export.csv").toFile

        use(new FileOutputStream(file)) { out =>
          attributes.foreach { x => out.write(x.getBytes) }
        }

        Success(file)
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  /**
   * データセットのアクセスレベルの一覧を取得する。
   * 
   * @param datasetId データセットID
   * @param limit 検索上限
   * @param offset 検索の開始位置
   * @param user ユーザ情報
   * @return アクセスレベルの一覧(offset, limitつき)。エラーがあれば、例外をFailureに包んで返却する。発生しうる例外は、NotFoundException、AccessDeniedException、NullPointerExceptionである。
   */
  def searchOwnerships(datasetId: String, offset: Option[Int], limit: Option[Int], user: User): Try[RangeSlice[DatasetOwnership]] = {
    try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(offset, "offset")
      CheckUtil.checkNull(limit, "limit")
      CheckUtil.checkNull(user, "user")
      DB readOnly { implicit s =>
        checkReadPermission(datasetId, user)
        val o = persistence.Ownership.o
        val u = persistence.User.u
        val g = persistence.Group.g
        val m = persistence.Member.m
        val gi = persistence.GroupImage.gi
        val count = withSQL {
          select(sqls.countDistinct(g.id))
            .from(persistence.Ownership as o)
            .innerJoin(persistence.Group as g).on(sqls.eq(o.groupId, g.id).and.eq(g.groupType, GroupType.Public))
            .innerJoin(persistence.GroupImage as gi).on(gi.groupId, g.id)
            .where
            .eq(o.datasetId, sqls.uuid(datasetId))
            .and
            .isNull(o.deletedBy)
            .and
            .isNull(o.deletedAt)
            .and
            .gt(o.accessLevel, 0)
            .union(
              select(sqls.countDistinct(u.id))
                .from(persistence.Ownership as o)
                .innerJoin(persistence.Group as g).on(sqls.eq(o.groupId, g.id).and.eq(g.groupType, GroupType.Personal))
                .innerJoin(persistence.Member as m).on(sqls.eq(g.id, m.groupId).and.isNull(m.deletedAt))
                .innerJoin(persistence.User as u).on(sqls.eq(u.id, m.userId).and.isNull(u.deletedAt))
                .where
                .eq(o.datasetId, sqls.uuid(datasetId))
                .and
                .isNull(o.deletedBy)
                .and
                .isNull(o.deletedAt)
                .and
                .gt(o.accessLevel, 0)
            )
        }.map(rs => rs.int(1)).list.apply.foldLeft(0)(_ + _)

        val list = withSQL {
          select(g.id, o.accessLevel, g.name, gi.imageId, g.description, sqls"null as fullname, '2' as type, null as organization, null as title, false as own")
            .from(persistence.Ownership as o)
            .innerJoin(persistence.Group as g).on(sqls.eq(o.groupId, g.id).and.eq(g.groupType, GroupType.Public))
            .innerJoin(persistence.GroupImage as gi).on(sqls.eq(gi.groupId, g.id).and.eq(gi.isPrimary, true).and.isNull(gi.deletedBy))
            .where
              .eq(o.datasetId, sqls.uuid(datasetId))
              .and
              .isNull(o.deletedBy)
              .and
              .isNull(o.deletedAt)
              .and
              .gt(o.accessLevel, 0)
            .union(
              select(u.id, o.accessLevel, u.name, u.imageId, u.description, u.fullname, sqls"'1' as type", u.organization, u.title, sqls.eqUuid(u.id, user.id).and.eq(o.accessLevel, 3).append(sqls"own"))
                .from(persistence.Ownership as o)
                .innerJoin(persistence.Group as g).on(sqls.eq(o.groupId, g.id).and.eq(g.groupType, GroupType.Personal))
                .innerJoin(persistence.Member as m).on(sqls.eq(g.id, m.groupId).and.isNull(m.deletedAt))
                .innerJoin(persistence.User as u).on(sqls.eq(u.id, m.userId).and.isNull(u.deletedAt))
                .where
                  .eq(o.datasetId, sqls.uuid(datasetId))
                  .and
                  .isNull(o.deletedBy)
                  .and
                  .isNull(o.deletedAt)
                  .and
                  .gt(o.accessLevel, 0)
            )
            .orderBy(sqls"own desc")
            .offset(offset.getOrElse(0))
            .limit(limit.getOrElse(20))
        }.map(
            rs => (rs.string("id"),
              rs.int("access_level"),
              rs.string("name"),
              rs.string("image_id"),
              rs.string("description"),
              rs.string("fullname"),
              rs.int("type"),
              rs.string("organization"),
              rs.string("title"))
          ).list.apply.map { o =>
          DatasetOwnership(
            id = o._1,
            name = o._3,
            fullname = o._6,
            image = AppConf.imageDownloadRoot +
              (if (o._7 == 1) { "user/" } else { "groups/" }) +
              o._1 + "/" + o._4,
            accessLevel = o._2,
            ownerType = o._7,
            description = o._5,
            organization = o._8,
            title = o._9
          )
        }.toSeq
        Success(
          RangeSlice(
            summary = RangeSliceSummary(
              total = count,
              offset = offset.getOrElse(0),
              count = limit.getOrElse(20)
            ),
            results = list
          )
        )
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  /**
   * データセットの画像一覧を取得する。
   *
   * @param datasetId データセットID
   * @param limit 検索上限
   * @param offset 検索の開始位置
   * @param user ユーザー情報
   * @return データセットが保持する画像の一覧(総件数、limit、offset付き)。エラーがあれば、例外をFailureに包んで返却する。発生しうる例外は、AccessDeniedException、NotFoundException、NullPointerExceptionである。
   */
  def getImages(datasetId: String, offset: Option[Int], limit: Option[Int], user: User): Try[RangeSlice[DatasetData.DatasetGetImage]] = {
    try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(offset, "offset")
      CheckUtil.checkNull(limit, "limit")
      CheckUtil.checkNull(user, "user")
      DB readOnly { implicit s =>
        checkReadPermission(datasetId, user)
        val di = persistence.DatasetImage.di
        val i = persistence.Image.i
        val totalCount = withSQL {
          select(sqls"count(1)")
            .from(persistence.DatasetImage as di)
            .innerJoin(persistence.Image as i).on(di.imageId, i.id)
            .where
            .eqUuid(di.datasetId, datasetId)
            .and
            .isNull(di.deletedBy)
            .and
            .isNull(di.deletedAt)
        }.map(rs => rs.int(1)).single.apply
        val result = withSQL {
          select(i.result.*, di.result.isPrimary)
            .from(persistence.DatasetImage as di)
            .innerJoin(persistence.Image as i).on(di.imageId, i.id)
            .where
              .eqUuid(di.datasetId, datasetId)
              .and
              .isNull(di.deletedBy)
              .and
              .isNull(di.deletedAt)
              .offset(offset.getOrElse(0))
              .limit(limit.getOrElse(20))
        }.map(rs => (rs.string(i.resultName.id), rs.string(i.resultName.name), rs.boolean(di.resultName.isPrimary))).list.apply.map{ x =>
          DatasetData.DatasetGetImage(
            id = x._1,
            name = x._2,
            url = datasetImageDownloadRoot + datasetId + "/" + x._1,
            isPrimary = x._3
          )
        }

        Success(RangeSlice(
          RangeSliceSummary(
            total = totalCount.getOrElse(0),
            count = limit.getOrElse(20),
            offset = offset.getOrElse(0)
          ),
          result
        ))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  def changeFeaturedImage(datasetId: String, imageId: String, user: User): Try[Unit] = {
    try {
      DB localTx { implicit s =>
        datasetAccessabilityCheck(datasetId, user)
        if (!existsImage(datasetId, imageId)) throw new NotFoundException

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        // 対象のイメージをFeaturedに変更
        turnImageToFeatured(datasetId, imageId, myself, timestamp)
        // 対象以外のイメージをFeatured以外に変更
        turnOffFeaturedOtherImage(datasetId, imageId, myself, timestamp)

        Success(Unit)
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  private def turnOffFeaturedOtherImage(datasetId: String, imageId: String, myself: persistence.User, timestamp: DateTime)(implicit s: DBSession) {
    withSQL {
      val di = persistence.DatasetImage.column
      update(persistence.DatasetImage)
        .set(di.isFeatured -> false, di.updatedBy -> sqls.uuid(myself.id), di.updatedAt -> timestamp)
        .where
        .ne(di.imageId, sqls.uuid(imageId))
        .and
        .eq(di.datasetId, sqls.uuid(datasetId))
        .and
        .isNull(di.deletedAt)
    }.update().apply
  }

  private def turnImageToFeatured(datasetId: String, imageId: String, myself: persistence.User, timestamp: DateTime)(implicit s: DBSession) {
    withSQL {
      val di = persistence.DatasetImage.column
      update(persistence.DatasetImage)
        .set(di.isFeatured -> true, di.updatedBy -> sqls.uuid(myself.id), di.updatedAt -> timestamp)
        .where
        .eq(di.imageId, sqls.uuid(imageId))
        .and
        .eq(di.datasetId, sqls.uuid(datasetId))
        .and
        .isNull(di.deletedAt)
    }.update().apply
  }

  /**
    * 内部処理振り分けのための、ファイル情報を取得する
    *
    * @param datasetId データセットID
    * @param fileId ファイルID
    * @param user ユーザ情報
    * @return ファイル情報を保持するケースオブジェクト
    */
  private def getFileInfo(datasetId: String, fileId: String, user: User): Try[DatasetService.FileInfo] = {
    logger.trace(LOG_MARKER, "Called getFileInfo, datasetId={}, fileId={}, user={]", datasetId, fileId, user)

    val findResult = DB readOnly { implicit s =>
      for {
        _ <- requireAllowDownload(user, datasetId)
        _ <- found(getDataset(datasetId))
        file <- found(findFile(fileId))
      } yield {
        file
      }
    }

    for {
      fileInfo <- findResult
      _ <- requireNotWithPassword(fileInfo)
    } yield {
      val isFileExistsOnLocal = fileInfo.file.localState == SaveStatus.SAVED
      val isFileSync = fileInfo.file.localState == SaveStatus.DELETING
      val isFileExistsOnS3 = fileInfo.file.s3State == SaveStatus.SYNCHRONIZING
      val isDownloadFromLocal = isFileExistsOnLocal || (isFileExistsOnS3 && isFileSync)
      (fileInfo, isDownloadFromLocal) match {
        case (FileResultNormal(file, path), true) => {
          DatasetService.FileInfoLocalNormal(file, path)
        }
        case (FileResultNormal(file, path), false) => {
          DatasetService.FileInfoS3Normal(file, path)
        }
        case (FileResultZip(file, path, zippedFile), true) => {
          DatasetService.FileInfoLocalZipped(file, path, zippedFile)
        }
        case (FileResultZip(file, path, zippedFile), false) => {
          DatasetService.FileInfoS3Zipped(file, path, zippedFile)
        }
        case _ => {
          logger.error(LOG_MARKER, "Unknown file info, fileInfo={}, isDownloadFromLocal={}", fileInfo, isDownloadFromLocal.toString)

          throw new UnsupportedOperationException
        }
      }
    }
  }

  /**
    * ファイルダウンロード向けにケースオブジェクトを返す。
    *
    * @param fileInfo ファイル情報
    * @param requireData ファイル内容を返すストリームが必要な場合はtrue
    * @return ファイルダウンロード向けに必要項目を保持するケースオブジェクト
    */
  private def getDownloadFileByFileInfo(fileInfo: DatasetService.FileInfo, requireData: Boolean = true): Try[DatasetService.DownloadFile] = Try {
    logger.trace(LOG_MARKER, "Called getDownloadFileByFileInfo, fileInfo={}, requireData={}", fileInfo, requireData.toString)

    fileInfo match {
      case DatasetService.FileInfoLocalNormal(file, path) => {
        val downloadFile = FileManager.downloadFromLocal(path.substring(1))
        val is = if (requireData) { Files.newInputStream(downloadFile.toPath) } else { null }
        DatasetService.DownloadFileLocalNormal(is, file.name, file.fileSize)
      }
      case DatasetService.FileInfoS3Normal(file, path) => {
        val url = FileManager.downloadFromS3Url(path.substring(1), file.name)
        DatasetService.DownloadFileS3Normal(url)
      }
      case DatasetService.FileInfoLocalZipped(file, path, zippedFile) => {
        val is = if (requireData) {
          createRangeInputStream(
            path = Paths.get(AppConf.fileDir, path.substring(1)),
            offset = zippedFile.dataStart,
            limit = zippedFile.dataSize)
        } else { null }

        val encoding = if (isSJIS(zippedFile.name)) { Charset.forName("Shift-JIS") } else { Charset.forName("UTF-8") }
        try {
          val zis = if (requireData) {
            createUnzipInputStream(
              data = is,
              centralHeader = zippedFile.cenHeader,
              dataSize = zippedFile.dataSize,
              encoding = encoding)
          } else { null }

          DatasetService.DownloadFileLocalZipped(zis, zippedFile.name, zippedFile.dataSize)
        } catch {
          case e: Exception => {
            logger.error(LOG_MARKER, "Error occurred.", e)

            is.close()
            throw e
          }
        }
      }
      case DatasetService.FileInfoS3Zipped(file, path, zippedFile) => {
        val is = if (requireData) {
          FileManager.downloadFromS3(
            filePath = path.substring(1),
            start = zippedFile.dataStart,
            end = zippedFile.dataStart + zippedFile.dataSize - 1)
        } else { null }
        val encoding = if (isSJIS(zippedFile.name)) { Charset.forName("Shift-JIS") } else { Charset.forName("UTF-8") }
        try {
          val zis = if (requireData) {
            createUnzipInputStream(
              data = is,
              centralHeader = zippedFile.cenHeader,
              dataSize = zippedFile.dataSize,
              encoding = encoding)
          } else { null }

          DatasetService.DownloadFileS3Zipped(zis, zippedFile.name, zippedFile.dataSize)
        } catch {
          case e: Exception => {
            logger.error(LOG_MARKER, "Error occurred.", e)

            is.close()
            throw e
          }
        }
      }
    }
  }

  /**
    * ファイルダウンロード向けにケースオブジェクトを返す。
    * 返すケースオブジェクトには、ファイル内容のストリームを保持する。
    *
    * @param datasetId データセットID
    * @param fileId ファイルID
    * @param user ユーザ情報
    * @return ファイルダウンロード向けに必要項目を保持するケースオブジェクト
    */
  def getDownloadFileWithStream(datasetId: String, fileId: String, user: User): Try[DatasetService.DownloadFile] = {
    val fileInfo = getFileInfo(datasetId, fileId, user)
    fileInfo.flatMap(getDownloadFileByFileInfo(_, true))
  }

  /**
    * ファイルダウンロード向けにケースオブジェクトを返す。
    * 返すケースオブジェクトには、ファイル内容のストリームを保持しない。
    *
    * @param datasetId データセットID
    * @param fileId ファイルID
    * @param user ユーザ情報
    * @return ファイルダウンロード向けに必要項目を保持するケースオブジェクト
    */
  def getDownloadFileWithoutStream(datasetId: String, fileId: String, user: User): Try[DatasetService.DownloadFile] = {
    val fileInfo = getFileInfo(datasetId, fileId, user)
    fileInfo.flatMap(getDownloadFileByFileInfo(_, false))
  }

  /**
   * 指定したデータセットが保持するファイル情報の一覧を返す。
   * 
   * @param datasetId データセットID
   * @param limit 検索上限
   * @param offset 検索の開始位置
   * @param user ユーザー情報
   * @return データセットが保持するファイル情報の一覧(総件数、limit、offset付き)。エラーがあれば、例外をFailureに包んで返却する。発生しうる例外は、NotFoundException、AccessDeniedException、NullPointerExceptionである。
   */
  def getDatasetFiles(datasetId: String, limit: Option[Int], offset: Option[Int], user: User): Try[RangeSlice[DatasetData.DatasetFile]] = {
    try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(limit, "limit")
      CheckUtil.checkNull(offset, "offset")
      CheckUtil.checkNull(user, "user")
      DB readOnly { implicit s =>
        val dataset = getDataset(datasetId) match {
          case Some(x) => x
          case None => throw new NotFoundException
        }
        checkReadPermission(datasetId, user)
        val validatedLimit = limit.map{ x =>
          if(x < 0) { 0 } else if (AppConf.fileLimit < x) { AppConf.fileLimit } else { x }
        }.getOrElse(AppConf.fileLimit)
        val validatedOffset = offset.getOrElse(0)
        val count = getFileAmount(datasetId)
        // offsetが0未満は空リストを返却する
        if (validatedOffset < 0) {
          Success(RangeSlice(RangeSliceSummary(count, 0, validatedOffset), Seq.empty[DatasetData.DatasetFile]))
        } else {
          val files = getFiles(datasetId, validatedLimit, validatedOffset)
          Success(RangeSlice(RangeSliceSummary(count, files.size, validatedOffset), files))
        }
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  /**
   * 指定したデータセットのZipファイルが内部に保持するファイル情報の一覧を返す。
   * 
   * @param datasetId データセットID
   * @param fileId (zipファイルの)ファイルID
   * @param limit 検索上限
   * @param offset 検索の開始位置
   * @param user ユーザー情報
   * @return Zipファイルが内部に保持するファイル情報の一覧(総件数、limit、offset付き)。エラーがあれば、例外をFailureに包んで返却する。発生しうる例外は、NotFoundException、AccessDeniedException、BadRequestException、NullPointerExceptionである。
   */
  def getDatasetZippedFiles(datasetId: String, fileId: String, limit: Option[Int], offset: Option[Int], user: User): Try[RangeSlice[DatasetData.DatasetZipedFile]] = {
    try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(fileId, "fileId")
      CheckUtil.checkNull(limit, "limit")
      CheckUtil.checkNull(offset, "offset")
      CheckUtil.checkNull(user, "user")
      DB readOnly { implicit s =>
        val dataset = getDataset(datasetId) match {
          case Some(x) => x
          case None => throw new NotFoundException
        }
        val history = persistence.File.find(fileId).flatMap(file => persistence.FileHistory.find(file.historyId)) match {
          case Some(x) => 
            if (x.isZip) { x }
            else { throw new BadRequestException(resource.getString(ResourceNames.CANT_TAKE_OUT_BECAUSE_NOT_ZIP)) }
          case None => throw new BadRequestException(resource.getString(ResourceNames.FILE_NOT_FOUND))
        }
        checkReadPermission(datasetId, user)
        val validatedLimit = limit.map{ x =>
          if(x < 0) { 0 } else if (AppConf.fileLimit < x) { AppConf.fileLimit } else { x }
        }.getOrElse(AppConf.fileLimit)
        val validatedOffset = offset.getOrElse(0)
        val count = getZippedFileAmount(datasetId, history.id)
        // offsetが0未満は空リストを返却する
        if (validatedOffset < 0) {
          Success(RangeSlice(RangeSliceSummary(count, 0, validatedOffset), Seq.empty[DatasetData.DatasetZipedFile]))
        } else {
          val files = getZippedFiles(datasetId, history.id, validatedLimit, validatedOffset)
          Success(RangeSlice(RangeSliceSummary(count, files.size, validatedOffset), files))
        }
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  private def getZippedFiles(datasetId: String, historyId: String, limit: Int, offset: Int)(implicit s: DBSession): Seq[DatasetZipedFile] = {
    val zf = persistence.ZipedFiles.zf
    val zipedFiles = withSQL {
      select
      .from(ZipedFiles as zf)
      .where
        .eq(zf.historyId, sqls.uuid(historyId))
        .offset(offset)
        .limit(limit)
    }.map(persistence.ZipedFiles(zf.resultName)).list.apply
    if (zipedFiles.exists(hasPassword)) {
      return Seq.empty
    }
    zipedFiles.map{x =>
      DatasetZipedFile(
        id = x.id,
        name = x.name,
        size = x.fileSize,
        url = AppConf.fileDownloadRoot + datasetId + "/" + x.id
      )
    }.toSeq
  }

  private def getZippedFileAmount(datasetId: String, historyId: String)(implicit s: DBSession): Int = {
    val zf = persistence.ZipedFiles.zf
    withSQL {
      select(sqls.count)
      .from(ZipedFiles as zf)
      .where
        .eq(zf.historyId, sqls.uuid(historyId))
    }.map(_.int(1)).single.apply.getOrElse(0)
  }
}

object DatasetService {
  /**
    * DatasetService内で処理判別するためのケースオブジェクト
    * ファイル情報を持つ
    */
  sealed trait FileInfo

  /**
    * ファイル情報：ローカルに保持する通常ファイル
    *
    * @param file ファイル情報
    * @param path ファイルパス
    */
  case class FileInfoLocalNormal(file: persistence.File, path: String) extends FileInfo

  /**
    * ファイル情報：S3上に保持する通常ファイル
    *
    * @param file ファイル情報
    * @param path ファイルパス
    */
  case class FileInfoS3Normal(file: persistence.File, path: String) extends FileInfo

  /**
    * ファイル情報：ローカルに保持するZIPファイル内の個別ファイル
    *
    * @param file ファイル情報
    * @param path ファイルパス
    */
  case class FileInfoLocalZipped(file: persistence.File, path: String, zippedFile: persistence.ZipedFiles) extends FileInfo

  /**
    * ファイル情報：S3上に保持するZIPファイル内の個別ファイル
    *
    * @param file ファイル情報
    * @param path ファイルパス
    */
  case class FileInfoS3Zipped(file: persistence.File, path: String, zippedFile: persistence.ZipedFiles) extends FileInfo

  /**
    * ファイルダウンロード向けに必要項目を保持するケースオブジェクト
    */
  sealed trait DownloadFile

  /**
    * ファイルダウンロード：ローカルに保持する通常ファイル
    *
    * @param fileData ファイル内容を返すストリーム
    * @param fileName ファイル名
    * @param fileSize ファイルサイズ
    */
  case class DownloadFileLocalNormal(fileData: InputStream, fileName: String, fileSize: Long) extends DownloadFile

  /**
    * ファイルダウンロード：ローカルに保持するZIPファイル内の個別ファイル
    *
    * @param fileData ファイル内容を返すストリーム
    * @param fileName ファイル名
    * @param fileSize ファイルサイズ
    */
  case class DownloadFileLocalZipped(fileData: InputStream, fileName: String, fileSize: Long) extends DownloadFile

  /**
    * ファイルダウンロード：S3上に保持する通常ファイル
    *
    * @param redirectUrl S3上のファイルへのリダイレクトURL
    */
  case class DownloadFileS3Normal(redirectUrl: String) extends DownloadFile

  /**
    * ファイルダウンロード：S3上に保持するZIPファイル内の個別ファイル
    *
    * @param fileData ファイル内容を返すストリーム
    * @param fileName ファイル名
    * @param fileSize ファイルサイズ
    */
  case class DownloadFileS3Zipped(fileData: InputStream, fileName: String, fileSize: Long) extends DownloadFile

}
