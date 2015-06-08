package dsmoq.services

import java.io.{ByteArrayInputStream, FileOutputStream, InputStreamReader, RandomAccessFile}
import java.nio.charset.Charset
import java.nio.file.{StandardCopyOption, Files, Path, Paths}
import java.util.UUID
import java.util.zip.{ZipFile, ZipInputStream}

import scala.collection.mutable
import scala.util.{Failure, Try, Success}

import com.github.tototoshi.csv.CSVReader
import org.joda.time.DateTime
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.{compact, render}
import org.json4s.{JInt, JBool}
import org.scalatra.servlet.FileItem
import scalax.io.Resource
import scalikejdbc._

import dsmoq.AppConf
import dsmoq.persistence
import dsmoq.persistence.{
  Annotation,
  Dataset, DatasetAnnotation, DatasetImage,
  DefaultAccessLevel,
  GroupAccessLevel, GroupType,
  OwnerType, Ownership,
  PresetType, UserAccessLevel, ZipedFiles
}
import dsmoq.exceptions.{AccessDeniedException, InputValidationException, NotAuthorizedException, NotFoundException}
import dsmoq.logic.{FileManager, StringUtil, ImageSaveLogic, ZipUtil}
import dsmoq.persistence.PostgresqlHelper._
import dsmoq.services.json.{DatasetData, Image, RangeSlice, RangeSliceSummary}
import dsmoq.services.json.DatasetData.{DatasetOwnership, DatasetZipedFile, CopiedDataset, DatasetTask}

object DatasetService {
  // FIXME 暫定パラメータのため、将来的には削除する
  private val UserAndGroupAccessDeny = 0
  private val UserAndGroupAllowDownload = 2

  private val MoveToS3 = 0
  private val MoveToLocal = 1

  private val datasetImageDownloadRoot = AppConf.imageDownloadRoot + "datasets/"

  /**
   * データセットを新規作成します。
   * @param files
   * @param user
   * @return
   */
  def create(files: Seq[FileItem], saveLocal: Option[Boolean], saveS3: Option[Boolean], name: Option[String], user: User): Try[DatasetData.Dataset] = {
    try {
      val files_ = files.filter(_.name.nonEmpty)
      val saveLocal_ = saveLocal.getOrElse(true)
      val saveS3_ = saveS3.getOrElse(false)
      val name_ = name.getOrElse("")

      val errors = mutable.LinkedHashMap.empty[String, String]
      if (! saveLocal_ && ! saveS3_) { errors.put("storage", "both check is false. select either") }
      if (name_.isEmpty && files_.isEmpty) { errors.put("name", "name or file is necessary") }
      if (errors.nonEmpty) throw new InputValidationException(errors)

      DB localTx { implicit s =>
        val myself = persistence.User.find(user.id).get
        val myGroup = getPersonalGroup(myself.id).get

        val datasetId = UUID.randomUUID().toString
        val timestamp = DateTime.now()

        val f = files_.map(f => {
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
            localState = if (saveLocal_) { 1 } else { 3 },
            s3State = if (saveS3_) { 2 } else { 0 }
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
          name = if (name_.isEmpty) { f.head._1.name } else { name_ },
          description = "",
          licenseId = AppConf.defaultLicenseId,
          filesCount = f.length,
          filesSize = f.map(x => x._2.fileSize).sum,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp,
          localState = if (saveLocal_) { 1 } else { 3 },
          s3State = if (saveS3_) { 2 } else { 0 }
        )

        if (saveS3_ && ! f.isEmpty) {
          createTask(datasetId, MoveToS3, myself.id, timestamp, saveLocal_)
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
          files = f.map(x => DatasetData.DatasetFile(
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
            zipedFiles = if (x._2.isZip) { getZipedFiles(datasetId, x._2.id) } else { Seq.empty }
          )),
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
          s3State = dataset.s3State
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
        // TODO: ZIP64
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
   * 指定したデータセットの詳細情報を取得します。
   * @param id
   * @param user
   * @return
   */
  def get(id: String, user: User): Try[DatasetData.Dataset] = {
    try {
      DB readOnly { implicit s =>
        // データセットが存在しない場合例外
        val dataset = getDataset(id) match {
          case Some(x) => x
          case None => throw new NotFoundException
        }
        (for {
          groups <- Some(getJoinedGroups(user))
          permission <- getPermission(id, groups)
          guestAccessLevel <- Some(getGuestAccessLevel(id))
          owners <- Some(getAllOwnerships(id, user))
          files <- Some(getFiles(id))
          attributes <- Some(getAttributes(id))
          images <- Some(getImages(id))
          primaryImage <- getPrimaryImageId(id)
          featuredImage <- getFeaturedImageId(id)
          count <- Some(getAccessCount(id))
        } yield {
          // FIXME チェック時、user権限はUserAccessLevelクラス, groupの場合はGroupAccessLevelクラスの定数を使用する
          // (UserAndGroupAccessDeny 定数を削除する)
          if (permission == UserAndGroupAccessDeny) {
            throw new NotAuthorizedException
          }
          DatasetData.Dataset(
            id = dataset.id,
            files = files,
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
            s3State = dataset.s3State
          )
        })
        .map(x => Success(x)).getOrElse(Failure(new NotAuthorizedException()))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  /**
   * 指定したデータセットにファイルを追加します。
   * @param id データセットID
   * @param files ファイルリスト
   * @param user
   * @return
   */
  def addFiles(id: String, files: Seq[FileItem], user: User): Try[DatasetData.DatasetAddFiles] = {
    try {
      DB localTx { implicit s =>
        // input validation
        if (files.isEmpty) throw new InputValidationException(Map("files" -> "file is empty"))

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
            localState = if (dataset.localState == 1 || dataset.localState == 2) { 1 } else { 3 },
            s3State = if (dataset.s3State == 0) { 0 } else if (dataset.s3State == 3) { 3 } else { 2 }
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

        if (dataset.s3State == 1 || dataset.s3State == 2) {
          createTask(id, MoveToS3, myself.id, timestamp, dataset.localState == 1)
        }
        // datasetsのfiles_size, files_countの更新
        updateDatasetFileStatus(id, myself.id, timestamp)

        Success(DatasetData.DatasetAddFiles(
          files = f.map(x => DatasetData.DatasetFile(
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
            zipedFiles = if (x._2.isZip) { getZipedFiles(id, x._2.id) } else { Seq.empty }
          ))
        ))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  /**
   * 指定したファイルを更新します。
   * @param datasetId
   * @param fileId
   * @param file
   * @param user
   * @return
   */
  def updateFile(datasetId: String, fileId: String, file: Option[FileItem], user: User) = {
    try {
      DB localTx { implicit s =>
        val file_ = file match {
          case Some(x) => x
          case None => throw new InputValidationException(Map("file" -> "file is empty"))
        }
        if (file_.getSize <= 0) throw new InputValidationException(Map("file" -> "file is empty"))

        fileAccessabilityCheck(datasetId, fileId, user)

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        val historyId = UUID.randomUUID.toString

        val dataset = getDataset(datasetId).get
        updateFileNameAndSize(
          fileId,
          historyId,
          file_,
          myself.id,
          timestamp,
          if (dataset.s3State == 0) { 0 } else if (dataset.s3State == 3) { 3 } else { 2 },
          if (dataset.localState == 1 || dataset.localState == 2) { 1 } else { 3 }
        )

        val isZip = file_.getName.endsWith("zip")
        FileManager.uploadToLocal(datasetId, fileId, historyId, file_)
        val path = Paths.get(AppConf.fileDir, datasetId, fileId, historyId)

        val history = persistence.FileHistory.create(
          id = historyId,
          fileId = fileId,
          fileType = 0,
          fileMime = "application/octet-stream",
          filePath = "/" + datasetId + "/" + fileId + "/" + historyId,
          fileSize = file.size,
          isZip = isZip,
          realSize = if (isZip) { createZipedFiles(path, historyId, timestamp, myself).right.getOrElse(file_.size) } else { file_.size },
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp
        )
        FileManager.uploadToLocal(datasetId, fileId, history.id, file_)
        if (dataset.s3State == 1 || dataset.s3State == 2) {
          createTask(datasetId, MoveToS3, myself.id, timestamp, dataset.localState == 1)

          // S3に上がっている場合は、アップロードが完了するまで、ローカルからダウンロードしなければならない
          withSQL {
            val d = persistence.Dataset.column
            update(persistence.Dataset)
              .set(d.localState -> 3,
                d.s3State -> 2)
              .where
              .eq(d.id, sqls.uuid(datasetId))
          }.update().apply
        }

        // datasetsのfiles_size, files_countの更新
        updateDatasetFileStatus(datasetId, myself.id, timestamp)

        val result = persistence.File.find(fileId).get
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
          zipedFiles = if (history.isZip) { getZipedFiles(datasetId, history.id) } else { Seq.empty }
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
        if (!isOwner(user.id, datasetId)) throw new NotAuthorizedException
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
      // input validation
      val errors = mutable.LinkedHashMap.empty[String, String]

      if (filename.isEmpty) {
        errors.put("name", "name is empty")
      }

      if (errors.nonEmpty) {
        throw new InputValidationException(errors)
      }

      DB localTx { implicit s =>
        fileAccessabilityCheck(datasetId, fileId, user)

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()

        updateFileNameAndDescription(fileId, datasetId, filename, description, myself.id, timestamp)

        val result = persistence.File.find(fileId).get
        val history = persistence.FileHistory.find(result.historyId).get
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
          zipedFiles = if (history.isZip) { getZipedFiles(datasetId, history.id) } else { Seq.empty }
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
   * @param id
   * @param saveLocal
   * @param saveS3
   * @param user
   * @return
   */
  def modifyDatasetStorage(id:String, saveLocal: Option[Boolean], saveS3: Option[Boolean], user: User): Try[DatasetTask] = {
    try {
      val saveLocal_ = saveLocal.getOrElse(true)
      val saveS3_ = saveS3.getOrElse(false)

      if (! saveLocal_ && ! saveS3_) throw new InputValidationException(List(("storage" -> "both check is false. select either")))

      DB localTx { implicit s =>
        datasetAccessabilityCheck(id, user)

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        val dataset = getDataset(id).get

        // S3 to local
        val taskId = if (dataset.localState == 0 && (dataset.s3State == 1 || dataset.s3State == 2) && saveLocal_) {
          updateDatasetStorage(
            dataset,
            myself.id,
            timestamp,
            2,
            if (saveS3_) { 1 } else { 3 }
          )
          createTask(id, MoveToLocal, myself.id, timestamp, saveS3_)
          // local to S3
        } else if ((dataset.localState == 1 || dataset.localState == 2) && dataset.s3State == 0 && saveS3_) {
          updateDatasetStorage(
            dataset,
            myself.id,
            timestamp,
            if (saveLocal_) { 1 } else { 3 },
            2
          )
          createTask(id, MoveToS3, myself.id, timestamp, saveLocal_)
          // local, S3のいずれか削除
        } else if ((dataset.localState == 1 || dataset.localState == 2) && (dataset.s3State == 1 || dataset.s3State == 2) && saveLocal_ != saveS3_) {
          updateDatasetStorage(
            dataset,
            myself.id,
            timestamp,
            if (saveLocal_) { 1 } else { 3 },
            if (saveS3_) { 1 } else { 3 }
          )
          createTask(id, if (saveS3_) { MoveToS3 } else { MoveToLocal } , myself.id, timestamp, false)
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
   * @param id
   * @param name
   * @param description
   * @param license
   * @param attributes
   * @param user
   * @return
   */
  def modifyDatasetMeta(id: String, name: Option[String], description: Option[String],
                        license: Option[String], attributes: List[DataSetAttribute], user: User): Try[Unit] = {
    try {
      val name_ = StringUtil.trimAllSpaces(name.getOrElse(""))
      val description_ = description.getOrElse("")
      val license_ = license.getOrElse("")
      val attributes_ = attributes.map(x => x.name -> StringUtil.trimAllSpaces(x.value))

      DB localTx { implicit s =>
        // input parameter check
        val errors = mutable.LinkedHashMap.empty[String, String]
        if (name_.isEmpty) {
          errors.put("name", "name is empty")
        }
        if (license_.isEmpty) {
            errors.put("license", "license is empty")
        } else {
          // licenseの存在チェック
          if (StringUtil.isUUID(license_)) {
            if (persistence.License.find(license_).isEmpty) {
              errors.put("license", "license is invalid")
            }
          } else {
            errors.put("license", "license is invalid")
          }
        }

        // featured attributeは一つでなければならない
        if (attributes_.filter(_._1 == "featured").length >= 2) {
          errors.put("attribute", "featured attribute must be unique")
        }

        if (errors.size != 0) {
          throw new InputValidationException(errors)
        }

        datasetAccessabilityCheck(id, user)

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()

        updateDatasetDetail(id, name_, description_, license_, myself.id, timestamp)

        // 先に指定datasetに関連するannotation(name)を取得(あとで差分チェックするため)
        val oldAnnotations = getAnnotationsRelatedByDataset(id)

        // 既存DatasetAnnotation全削除
        deleteDatasetAnnotation(id)

        // annotation(name)が既存のものかチェック なければ作る
        val annotationMap = getAvailableAnnotations.toMap

        attributes_.foreach { x =>
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
          if (!attributes_.map(_._1.toLowerCase).contains(x._1)) {
            val datasetAnnotations = getDatasetAnnotations(x._2)
            if (datasetAnnotations.size == 0) {
              deleteAnnotation(x._2)
            }
          }
        }
      }
      Success(Unit)
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
   * @param datasetId
   * @param images
   * @param user
   * @return
   */
  def addImages(datasetId: String, images: Seq[FileItem], user: User): Try[DatasetData.DatasetAddImages] = {
    try {
      val images_ = images.filter(_.name.nonEmpty)

      if (images_.isEmpty) throw new InputValidationException(Map("image" -> "image is empty"))

      DB localTx { implicit s =>
        if (!isOwner(user.id, datasetId)) throw new NotAuthorizedException

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        val primaryImage = getPrimaryImageId(datasetId)
        var isFirst = true

        val images = images_.map(i => {
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
          image
        })

        Success(DatasetData.DatasetAddImages(
          images = images.map(x => Image(
            id = x.id,
            url = datasetImageDownloadRoot + datasetId + "/" + x.id
          )),
          primaryImage = getPrimaryImageId(datasetId).getOrElse("")
        ))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  /**
   * 指定したデータセットのプライマリ画像を変更します。
   * @param datasetId
   * @param imageId
   * @param user
   * @return
   */
  def changePrimaryImage(datasetId: String, imageId: String, user: User): Try[Unit] = {
    try {
      if (imageId.isEmpty) throw new InputValidationException(Map("id" -> "ID is empty"))

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
        if (cantDeleteImages.contains(imageId)) throw new InputValidationException(Map("imageId" -> "default image can't delete"))
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
   * @param datasetId
   * @param acl
   * @param user
   * @return
   */
  def setAccessControl(datasetId: String, acl: List[DataSetAccessControlItem], user: User): Try[Seq[DatasetData.DatasetOwnership]] = {
    try {
      DB localTx { implicit s =>
        datasetAccessabilityCheck(datasetId, user)

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
   * @param datasetId
   * @param accessLevel
   * @param user
   * @return
   */
  def setGuestAccessLevel(datasetId: String, accessLevel: Int, user: User) = {
    try {
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
        Success(Unit)
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

  def getDownloadFile(datasetId: String, fileId: String, user: User) = {
    try {
      val fileInfo = DB readOnly { implicit s =>
        // 権限によりダウンロード可否の決定
        val permission = if (user.isGuest) {
          getGuestAccessLevel(datasetId)
        } else {
          val groups = getJoinedGroups(user)
          // FIXME チェック時、user権限はUserAccessLevelクラス, groupの場合はGroupAccessLevelクラスの定数を使用する
          // (UserAndGroupAccessDeny, UserAndGroupAllowDownload 定数を削除する)
          // 旧仕様ではuser/groupは同じ権限を付与していたが、
          // 現仕様はuser/groupによって権限の扱いが異なる(groupには編集権限は付与しない)
          // 実装時間の都合と現段階の実装でも問題がない(値が同じ)ため対応していない
          getPermission(datasetId, groups).getOrElse(UserAndGroupAccessDeny)
        }
        if (permission < UserAndGroupAllowDownload) {
          throw new AccessDeniedException
        }

        // datasetが削除されていないか
        val dataset = getDataset(datasetId) match {
          case Some(x) => x
          case None => throw new NotFoundException
        }

        val file = persistence.File.find(fileId)
        file match {
          case Some(f) => {
            val history = persistence.FileHistory.find(f.historyId).get
            (f, history.filePath, dataset, None)
          }
          case None => {
            val zipedFile = persistence.ZipedFiles.find(fileId)
            zipedFile match {
              case Some(zf) => {
                val history = persistence.FileHistory.find(zf.historyId).get
                val zipFile = persistence.File.find(history.fileId).get
                val zipFilePath = history.filePath
                (zipFile, zipFilePath, dataset, Some(zf))
              }
              case None => throw new NotFoundException
            }
          }
        }
      }
      if (fileInfo._1.localState == 1 || (fileInfo._1.s3State == 2 && fileInfo._1.localState == 3)) {
        fileInfo._4 match {
          case Some(zipedFile) => {
            val f = Paths.get(AppConf.fileDir, fileInfo._2.substring(1)).toFile
            val ra = new RandomAccessFile(f, "r")
            val dataArea = try {
              val dataArea = new Array[Byte](zipedFile.dataSize.toInt)
              ra.seek(zipedFile.dataStart)
              ra.readFully(dataArea)
              dataArea
            } catch {
              case e: Exception => {
                e.printStackTrace()
                throw e
              }
            } finally {
              ra.close()
            }
            val full = Array.concat(
              dataArea,
              zipedFile.cenHeader,
              Array[Byte] (
                0x50, 0x4b, 0x05, 0x06,
                0, 0, 0, 0,
                1, 0, 1, 0
              ),
              longToByte4(zipedFile.cenSize),
              longToByte4(zipedFile.dataSize),
              Array[Byte](0, 0)
            )
            val tempDirPath = Paths.get(AppConf.tempDir)
            val tempZipFile = Files.createTempFile(tempDirPath, "temp_", ".zip")
            Files.copy(new ByteArrayInputStream(full), tempZipFile, StandardCopyOption.REPLACE_EXISTING)
            val encoding = if (isSJIS(zipedFile.name)) { Charset.forName("Shift-JIS") } else { Charset.forName("UTF-8") }
            val tempFile = Files.createTempFile(tempDirPath, "temp_", "")
            val z = new ZipFile(tempZipFile.toFile, encoding)
            try {
              val entry = z.entries().nextElement()
              Files.copy(z.getInputStream(entry), tempFile, StandardCopyOption.REPLACE_EXISTING)
            } finally {
              z.close()
            }
            Files.delete(tempZipFile)
            Success((true, tempFile.toFile, "", zipedFile.name, None))
          }
          case None => {
            val file = FileManager.downloadFromLocal(fileInfo._2.substring(1))
            Success((true, file, "", fileInfo._1.name, None))
          }
        }
      } else {
        fileInfo._4 match {
          case Some(zipedFile) => {
            val bytes = FileManager.downloadFromS3Bytes(fileInfo._2.substring(1), zipedFile.dataStart, zipedFile.dataStart + zipedFile.dataSize - 1)
            val full = bytes ++
              zipedFile.cenHeader ++
              Array[Byte] (80, 75, 5, 6, 0, 0, 0, 0, 1, 0, 1, 0)
            val file = Paths.get(AppConf.tempDir, "temp.zip").toFile
            use(new FileOutputStream(file)) { f =>
              f.write(full)
              IntToByte4(zipedFile.cenSize.toInt, f)
              IntToByte4(zipedFile.dataSize.toInt, f)
              f.write(Array[Byte] (0, 0))
            }

            val encoding = if (isSJIS(zipedFile.name)) { Charset.forName("Shift-JIS") } else { Charset.forName("UTF-8") }

            val z = new ZipFile(file, encoding)
            val entry = z.entries().nextElement()
            val outFile = Paths.get(AppConf.tempDir, zipedFile.name.split(Array[Char]('\\', '/')).last).toFile
            use(new FileOutputStream(outFile)) { out =>
              out.write(Resource.fromInputStream(z.getInputStream(entry)).byteArray)
            }
            Success((true, outFile, "", zipedFile.name, None))
          }
          case None => {
            val url = FileManager.downloadFromS3Url(fileInfo._2.substring(1), fileInfo._1.name)
            Success((false, new java.io.File("."), url, fileInfo._1.name, None))
          }
        }
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        Failure(e)
      }
    }
  }

  private def isSJIS(str: String): Boolean = {
    try {
      val encoded = new String(str.getBytes("SHIFT_JIS"), "SHIFT_JIS")
      encoded.equals(str)
    } catch {
      case e: Exception => false
    }
  }

  private def longToByte4(num: Long): Array[Byte] = {
    Array[Long](
      (num & 0x00000000000000FF),
      (num & 0x000000000000FF00) >> 8,
      (num & 0x0000000000FF0000) >> 16,
      (num & 0x00000000FF000000) >> 24
    ).map(_.toByte)
  }
  private def IntToByte4(num: Int, f: FileOutputStream) = {
    if (num < (1 << 8)) {
      f.write(num)
      f.write(0)
      f.write(0)
      f.write(0)
    } else if (num < (1 << 16)) {
      f.write(num & 0x000000FF)
      f.write((num & 0x0000FF00) >> 8)
      f.write(0)
      f.write(0)
    } else if (num < (1 << 24)) {
      f.write(num & 0x000000FF)
      f.write((num & 0x0000FF00) >> 8)
      f.write((num & 0x00FF0000) >> 16)
      f.write(0)
    } else {
      f.write(num & 0x000000FF)
      f.write((num & 0x0000FF00) >> 8)
      f.write((num & 0x00FF0000) >> 16)
      f.write((num & 0xFF000000) >> 24)
    }
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

  private def getPermission(id: String, groups: Seq[String])(implicit s: DBSession) = {
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
    // Provider権限のGroupはWriteできない
    (guestPermission :: permissions) match {
      case x :: xs => Some((guestPermission :: permissions).map(x => if (x._1 == GroupAccessLevel.Provider && x._2 == GroupType.Public) { 2 } else { x._1 }).max)
      case Nil => None
    }
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

  private def getFiles(datasetId: String)(implicit s: DBSession) = {
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
        zipedFiles = if (history.isZip) { getZipedFiles(datasetId, history.id) } else { Seq.empty[DatasetZipedFile] }
      )
    }
    )
  }

  def getZipedFiles(datasetId: String, historyId: String)(implicit s: DBSession) = {
    val zf = persistence.ZipedFiles.zf
    withSQL {
      select
      .from(ZipedFiles as zf)
      .where
        .eq(zf.historyId, sqls.uuid(historyId))
    }.map(persistence.ZipedFiles(zf.resultName)).list.apply.map{x =>
      DatasetZipedFile(
        id = x.id,
        name = x.name,
        size = x.fileSize,
        url = AppConf.fileDownloadRoot + datasetId + "/" + x.id
      )}.toSeq
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

  def importAttribute(datasetId: String, file: Option[FileItem], user: User): Try[Unit] = {
    try
    {
      val file_ = file match {
        case Some(x) => x
        case None => throw new InputValidationException(Map("file" -> "file is empty"))
      }
      if (file_.getSize <= 0) throw new InputValidationException(Map("file" -> "file is empty"))

      val csv = use(new InputStreamReader(file_.getInputStream)) { in =>
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

  private def use[T1 <: Closable, T2](resource: T1)(f: T1 => T2): T2 = {
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

  def exportAttribute(datasetId: String, user: User): Try[java.io.File] = {
    try
    {
      DB readOnly { implicit s =>
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

  def searchOwnerships(datasetId: String, offset: Option[Int], limit: Option[Int], user: User): Try[RangeSlice[DatasetOwnership]] = {
    try {
      DB readOnly { implicit s =>
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

  def getImages(datasetId: String, offset: Option[Int], limit: Option[Int], user: User): Try[RangeSlice[DatasetData.DatasetGetImage]] = {
    try {
      DB readOnly { implicit s =>
        if (!isOwner(user.id, datasetId)) throw new NotAuthorizedException

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
}
