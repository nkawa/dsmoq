package dsmoq.services

import scala.util.{Failure, Try, Success}
import scalikejdbc._, SQLInterpolation._
import java.util.UUID
import java.nio.file.Paths
import dsmoq.AppConf
import dsmoq.services.json._
import dsmoq.persistence
import dsmoq.persistence.PostgresqlHelper._
import dsmoq.exceptions._
import org.joda.time.DateTime
import dsmoq.persistence.{GroupType, PresetType, OwnerType, DefaultAccessLevel, GroupAccessLevel, UserAccessLevel}
import dsmoq.logic.{StringUtil, ImageSaveLogic}
import scala.util.Failure
import scala.util.Success
import dsmoq.services.json.RangeSlice
import org.scalatra.servlet.FileItem
import dsmoq.services.json.RangeSliceSummary
import dsmoq.services.json.Image
import scala.collection.mutable

object DatasetService {
  // FIXME 暫定パラメータのため、将来的には削除する
  private val UserAndGroupAccessDeny = 0
  private val UserAndGroupAllowDownload = 2

  /**
   * データセットを新規作成します。
   * @param files
   * @param user
   * @return
   */
  def create(files: Seq[FileItem], user: User): Try[DatasetData.Dataset] = {
    try {
      val files_ = files.filter(_.name.nonEmpty)
      if (files_.isEmpty) throw new InputValidationException(Map("files" -> "file is empty"))

      DB localTx { implicit s =>
        val myself = persistence.User.find(user.id).get
        val myGroup = getPersonalGroup(myself.id).get

        val datasetId = UUID.randomUUID().toString
        val timestamp = DateTime.now()

        val f = files_.map(f => {
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
          val historyId = UUID.randomUUID.toString
          val histroy = persistence.FileHistory.create(
            id = historyId,
            fileId = file.id,
            fileType = 0,
            fileMime = "application/octet-stream",
            filePath = "/" + datasetId + "/" + file.id + "/" + historyId,
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
          name = f.head._1.name,
          description = "",
          licenseId = AppConf.defaultLicenseId,
          filesCount = f.length,
          filesSize = f.map(x => x._2.fileSize).sum,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp
        )
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
          files = f.map(x => DatasetData.DatasetFile(
            id = x._1.id,
            name = x._1.name,
            description = x._1.description,
            size = x._2.fileSize,
            url = AppConf.fileDownloadRoot + datasetId + "/" + x._1.id,
            createdBy = user,
            createdAt = timestamp.toString(),
            updatedBy = user,
            updatedAt = timestamp.toString()
          )),
          images = Seq(Image(
            id = AppConf.defaultDatasetImageId,
            url = AppConf.imageDownloadRoot + AppConf.defaultDatasetImageId
          )),
          primaryImage =  AppConf.defaultDatasetImageId,
          ownerships = Seq(DatasetData.DatasetOwnership(
            id = myself.id,
            name = myself.name,
            fullname = myself.fullname,
            //organization = myself.organization,
            //title = myself.title,
            image = AppConf.imageDownloadRoot + myself.imageId,
            accessLevel = ownership.accessLevel,
            ownerType = OwnerType.User
          )),
          defaultAccessLevel = DefaultAccessLevel.Deny,
          permission = ownership.accessLevel
        ))
      }
    } catch {
      case e: Throwable => Failure(e)
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
             user: User): Try[RangeSlice[DatasetData.DatasetsSummary]] = {
    try {
      val limit_ = limit.getOrElse(20)
      val offset_ = offset.getOrElse(0)

      DB readOnly { implicit s =>
        val joinedGroups = getJoinedGroups(user)
        val userGroupIds = getGroupIdsByUserName(owners)
        val groupIds = getGroupIdsByGroupName(groups)

        val count = countDataSets(joinedGroups, query, userGroupIds, groupIds, attributes)
        val records = if (count > 0) {
          findDataSets(joinedGroups, query, userGroupIds, groupIds, attributes, limit_, offset_)
        } else {
          List.empty
        }

        Success(RangeSlice(RangeSliceSummary(count, limit_, offset_), records))
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
      withSQL {
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
    } else {
      List.empty
    }
  }

  private def getGroupIdsByGroupName(names: Seq[String])(implicit s: DBSession) = {
    if (names.nonEmpty) {
      val g = persistence.Group.g
      withSQL {
        select.apply(g.id)
          .from(persistence.Group as g)
          .where
          .in(g.name, names)
          .and
          .eq(g.groupType, GroupType.Public)
          .and
          .isNull(g.deletedAt)
      }.map(rs => rs.string(1)).list().apply()
    } else {
      List.empty
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
                           attributes: Seq[DataSetAttribute], limit: Int, offset: Int)(implicit s: DBSession) = {
    val ds = persistence.Dataset.d
    val o = persistence.Ownership.o

    val datasets = withSQL {
      createDatasetSql(select.apply[Any](ds.resultAll, sqls.max(o.accessLevel).append(sqls"access_level")),
                                         joindGroups, query, ownerUsers, ownerGroups, attributes)
        .groupBy(ds.id)
        .orderBy(ds.updatedAt).desc
        .offset(offset)
        .limit(limit)
    }.map(rs => (persistence.Dataset(ds.resultName)(rs), rs.int("access_level"))).list().apply()

    val datasetIds = datasets.map(_._1.id)

    val ownerMap = getOwnerMap(datasetIds)
    val guestAccessLevelMap = getGuestAccessLevelMap(datasetIds)
    val imageIdMap = getImageIdMap(datasetIds)

    datasets.map(x => {
      val ds = x._1
      val permission = x._2
      val imageUrl = imageIdMap.get(ds.id).map(x => AppConf.imageDownloadRoot + x).getOrElse("")
      val accessLevel = guestAccessLevelMap.get(ds.id).getOrElse(DefaultAccessLevel.Deny)
      DatasetData.DatasetsSummary(
        id = ds.id,
        name = ds.name,
        description = ds.description,
        image = imageUrl,
        attributes = getAttributes(ds.id), //TODO 非効率
        ownerships = ownerMap.get(ds.id).getOrElse(List.empty),
        files = ds.filesCount,
        dataSize = ds.filesSize,
        defaultAccessLevel = accessLevel,
        permission = permission
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

    selectSql
      .from(persistence.Dataset as ds)
      .innerJoin(persistence.Ownership as o).on(o.datasetId, ds.id)
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
                    if (ownerUsers.nonEmpty) {
                      q.append(sqls.join(ownerUsers.map { sqls.eqUuid(o.groupId, _).and.eq(o.accessLevel, 3) }, sqls"or"))
                    } else {
                      q
                    }
                  }
                  .map { q =>
                    if (ownerGroups.nonEmpty) {
                      q.append(sqls.join(ownerGroups.map { sqls.eqUuid(o.groupId, _).and.eq(o.accessLevel, 3) }, sqls"or"))
                    } else {
                      q
                    }
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
                  _.append(sqls.join(attributes.map(x => sqls.eq(a.name, x.name).and.eq(da.data, x.value)), sqls"or"))
                )
              .groupBy(da.datasetId).having(sqls.eq(sqls.count(da.datasetId), attributes.length))
              .as(xda)
          ).on(ds.id, xda(da).datasetId)
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
        .map { sql =>
          query match {
            case Some(x) =>
              sql.and.like(ds.name, "%" + x + "%")
            case None =>
              sql
          }
        }
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

  private def getOwnerMap(datasetIds: Seq[String])(implicit s: DBSession): Map[String, List[DatasetData.DatasetOwnership]] = {
    if (datasetIds.nonEmpty) {
      val o = persistence.Ownership.o
      val g = persistence.Group.g
      val m = persistence.Member.m
      val u = persistence.User.u
      val gi = persistence.GroupImage.syntax("gi")
      val i = persistence.Image.i

      withSQL {
        select(o.result.datasetId, g.result.id, g.result.groupType,
              sqls"case ${g.column("group_type")} when 0 then ${g.column("name")} when 1 then ${u.column("name")} end as name",
              u.result.fullname
        )
          .from(persistence.Ownership as o)
            .innerJoin(persistence.Group as g).on(o.groupId, g.id)
            .leftJoin(persistence.Member as m).on(m.groupId, g.id)
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
        val groupId = rs.string(g.resultName.id)
        val groupType = rs.int(g.resultName.groupType)
        val groupName = rs.string("name")
        val fullname = rs.stringOpt(u.resultName.fullname)

        // TODO group_images.primeryを廃止したい（画像取得のロジックが煩雑化するため）
        // 暫定的に image は取得しない
        val ownership = DatasetData.DatasetOwnership(
          id = "",
          name = groupName,
          fullname = fullname.getOrElse(""),
          image = "",
          accessLevel = 3,
          ownerType = groupType
        )
        (datasetId, ownership)
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
        } yield {
          println(dataset)
          // 権限チェック
          // FIXME チェック時、user権限はUserAccessLevelクラス, groupの場合はGroupAccessLevelクラスの定数を使用する
          // (UserAndGroupAccessDeny 定数を削除する)
          // 旧仕様ではuser/groupは同じ権限を付与していたが、
          // 現仕様はuser/groupによって権限の扱いが異なる(groupには編集権限は付与しない)
          // 実装時間の都合と現段階の実装でも問題がない(値が同じ)ため対応していない
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
            ownerships = owners,
            defaultAccessLevel = guestAccessLevel,
            permission = permission
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

        getDataset(id) match {
          case Some(x) =>
            if (!isOwner(user.id, id)) throw new NotAuthorizedException
          case None =>
            throw new NotFoundException
        }

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        val f = files.map(f => {
          val file = persistence.File.create(
            id = UUID.randomUUID.toString,
            datasetId = id,
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
          val historyId = UUID.randomUUID.toString
          val history = persistence.FileHistory.create(
            id = historyId,
            fileId = file.id,
            fileType = 0,
            fileMime = "application/octet-stream",
            filePath = "/" + id + "/" + file.id + "/" + historyId,
            fileSize = f.size,
            createdBy = myself.id,
            createdAt = timestamp,
            updatedBy = myself.id,
            updatedAt = timestamp
          )
          writeFile(id, file.id, history.id, f)
          (file, history)
        })

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
            updatedAt = timestamp.toString()
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

        getDataset(datasetId) match {
          case Some(x) =>
            if (!isOwner(user.id, datasetId)) throw new NotAuthorizedException
          case None =>
            throw new NotFoundException
        }
        if (!existsFile(datasetId, fileId)) throw new NotFoundException

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()

        withSQL {
          val f = persistence.File.column
          update(persistence.File)
            .set(f.fileSize -> file.size, f.updatedBy -> sqls.uuid(myself.id), f.updatedAt -> timestamp)
            .where
            .eq(f.id, sqls.uuid(fileId))
        }.update().apply

        val historyId = UUID.randomUUID.toString
        val history = persistence.FileHistory.create(
          id = historyId,
          fileId = fileId,
          fileType = 0,
          fileMime = "application/octet-stream",
          filePath = "/" + datasetId + "/" + fileId + "/" + historyId,
          fileSize = file.size,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp
        )
        writeFile(datasetId, fileId, history.id, file_)

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
          updatedAt = timestamp.toString()
        ))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
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
        getDataset(datasetId) match {
          case Some(x) =>
            if (!isOwner(user.id, datasetId)) throw new NotAuthorizedException
          case None => throw new NotFoundException
        }
        if (!existsFile(datasetId, fileId)) throw new NotFoundException

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        withSQL {
          val f = persistence.File.column
          update(persistence.File)
            .set(f.name -> filename, f.description -> description,
              f.updatedBy -> sqls.uuid(myself.id), f.updatedAt -> timestamp)
            .where
            .eq(f.id, sqls.uuid(fileId))
            .and
            .eq(f.datasetId, sqls.uuid(datasetId))
        }.update().apply

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
          updatedAt = timestamp.toString()
        ))
      }
    } catch {
      case e: Exception => Failure(e)
    }
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
        getDataset(datasetId) match {
          case Some(x) =>
            if (!isOwner(user.id, datasetId)) throw new NotAuthorizedException
          case None =>
            throw new NotFoundException
        }
        if (!existsFile(datasetId,fileId)) throw new NotFoundException

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()

        withSQL {
          val f = persistence.File.column
          update(persistence.File)
            .set(f.deletedBy -> sqls.uuid(myself.id), f.deletedAt -> timestamp,
              f.updatedBy -> sqls.uuid(myself.id), f.updatedAt -> timestamp)
            .where
            .eq(f.id, sqls.uuid(fileId))
            .and
            .eq(f.datasetId, sqls.uuid(datasetId))
            .and
            .isNull(f.deletedAt)
        }.update().apply

        // datasetsのfiles_size, files_countの更新
        updateDatasetFileStatus(datasetId, myself.id, timestamp)

        Success(Unit)
      }
    } catch {
      case e: Exception => Failure(e)
    }
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
        if (errors.size != 0) {
          throw new InputValidationException(errors)
        }

        getDataset(id) match {
          case Some(x) =>
            if (!isOwner(user.id, id)) throw new NotAuthorizedException
          case None => throw new NotFoundException
        }

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()

        withSQL {
          val d = persistence.Dataset.column
          update(persistence.Dataset)
            .set(d.name -> name_, d.description -> description_, d.licenseId -> sqls.uuid(license_),
              d.updatedBy -> sqls.uuid(myself.id), d.updatedAt -> timestamp)
            .where
            .eq(d.id, sqls.uuid(id))
        }.update().apply

        val da = persistence.DatasetAnnotation.syntax("da")
        val a = persistence.Annotation.syntax("a")
        // 先に指定datasetに関連するannotation(name)を取得(あとで差分チェックするため)
        val oldAnnotations = withSQL {
          select(a.result.*)
            .from(persistence.Annotation as a)
            .innerJoin(persistence.DatasetAnnotation as da).on(sqls.eq(da.annotationId, a.id).and.isNull(da.deletedAt))
            .where
            .eq(da.datasetId, sqls.uuid(id))
            .and
            .isNull(a.deletedAt)
        }.map(rs => (rs.string(a.resultName.name).toLowerCase, rs.string(a.resultName.id))).list().apply

        // 既存DatasetAnnotation全削除
        withSQL {
          delete.from(persistence.DatasetAnnotation as da)
            .where
            .eq(da.datasetId, sqls.uuid(id))
        }.update().apply

        // annotation(name)が既存のものかチェック なければ作る
        val annotationMap = withSQL {
          select(a.result.*)
            .from(persistence.Annotation as a)
            .where
            .isNull(a.deletedAt)
        }.map(rs => (rs.string(a.resultName.name).toLowerCase, rs.string(a.resultName.id))).list().apply.toMap

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
            val datasetAnnotations = withSQL {
              select(da.result.id)
                .from(persistence.DatasetAnnotation as da)
                .where
                .eq(da.annotationId, sqls.uuid(x._2))
                .and
                .isNull(da.deletedAt)
            }.map(rs => rs.string(da.resultName.id)).list().apply
            if (datasetAnnotations.size == 0) {
              withSQL {
                delete.from(persistence.Annotation as a)
                  .where
                  .eq(a.id, sqls.uuid(x._2))
              }.update().apply
            }
          }
        }
      }
      Success(Unit)
    } catch {
      case e: Throwable => Failure(e)
    }
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
            url = AppConf.imageDownloadRoot + x.id
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
        getDataset(datasetId) match {
          case Some(x) =>
            if (!isOwner(user.id, datasetId)) throw new NotAuthorizedException
          case None => throw new NotFoundException
        }
        if (!existsImage(datasetId, imageId)) throw new NotFoundException

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
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

        Success(Unit)
      }
    } catch {
      case e: Throwable => Failure(e)
    }
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
        getDataset(datasetId) match {
          case Some(x) =>
            if (!isOwner(user.id, datasetId)) throw new NotAuthorizedException
          case None => throw new NotFoundException
        }
        if (!existsImage(datasetId, imageId)) throw new NotFoundException

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
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

        val primaryImageId = getPrimaryImageId(datasetId).getOrElse({
          // primaryImageの差し替え
          val di = persistence.DatasetImage.syntax("di")
          val i = persistence.Image.syntax("i")

          // primaryImageとなるImageを取得
          val primaryImage = withSQL {
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

          primaryImage match {
            case Some(x) =>
              val di = persistence.DatasetImage.column
              withSQL {
                update(persistence.DatasetImage)
                  .set(di.isPrimary -> true, di.updatedBy -> sqls.uuid(myself.id), di.updatedAt -> timestamp)
                  .where
                  .eq(di.id, sqls.uuid(x._1))
              }.update().apply
              x._2
            case None => ""
          }
        })
        Success(DatasetData.DatasetDeleteImage(primaryImage = primaryImageId))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
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
        getDataset(datasetId) match {
          case Some(x) =>
            if (!isOwner(user.id, datasetId)) throw new NotAuthorizedException
          case None =>
            throw new NotFoundException
        }

        val ownerships = acl.map { x =>
          x.ownerType match {
            case OwnerType.User =>
              val u = persistence.User.syntax("u")
              val m = persistence.Member.syntax("m")
              val g = persistence.Group.syntax("g")
              val groupId = withSQL {
                select(g.result.id)
                  .from(persistence.Group as g)
                  .innerJoin(persistence.Member as m).on(sqls.eq(g.id, m.groupId).and.isNull(m.deletedAt))
                  .innerJoin(persistence.User as u).on(sqls.eq(u.id, m.userId).and.isNull(u.deletedAt))
                  .where
                  .eq(u.id, sqls.uuid(x.id))
                  .and
                  .eq(g.groupType, GroupType.Personal)
                  .and
                  .isNull(g.deletedAt)
                  .and
                  .isNull(m.deletedAt)
                  .limit(1)
              }.map(rs => rs.string(g.resultName.id)).single().apply.get
              saveOrCreateOwnerships(user, datasetId, groupId, x.accessLevel)

              val user_ = persistence.User.find(x.id).get
              DatasetData.DatasetOwnership(
                id = user_.id,
                name = user_.name,
                fullname = user_.fullname,
                //organization = user.organization,
                //title = user.title,
                image = AppConf.imageDownloadRoot + user_.imageId,
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
                //organization = "",
                //title = "",
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
   * 指定したデータセットのゲストアクセスレベルを設定します。
   * @param datasetId
   * @param accessLevel
   * @param user
   * @return
   */
  def setGuestAccessLevel(datasetId: String, accessLevel: Int, user: User) = {
    try {
      DB localTx { implicit s =>
        getDataset(datasetId) match {
          case Some(x) =>
            if (!isOwner(user.id, datasetId)) throw new NotAuthorizedException
          case None =>
            throw new NotFoundException
        }

        val o = persistence.Ownership.o
        withSQL(
          select(o.result.*)
            .from(persistence.Ownership as o)
            .where
            .eq(o.datasetId, sqls.uuid(datasetId))
            .and
            .eq(o.groupId, sqls.uuid(AppConf.guestGroupId))
        ).map(persistence.Ownership(o.resultName)).single.apply match {
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

  /**
   * 指定したデータセットを削除します。
   * @param datasetId
   * @param user
   * @return
   */
  def deleteDataset(datasetId: String, user: User): Try[Unit] = {
    try {
      val timestamp = DateTime.now()
      DB localTx { implicit s =>
        getDataset(datasetId) match {
          case Some(x) => // do nothing
          case None => throw new NotFoundException
        }

        if (!isOwner(user.id, datasetId)) throw new NotAuthorizedException
        val d = persistence.Dataset.column
        withSQL {
          update(persistence.Dataset)
            .set(d.deletedAt -> timestamp, d.deletedBy -> sqls.uuid(user.id))
            .where
            .eq(d.id, sqls.uuid(datasetId))
        }.update().apply
      }
      Success(Unit)
    } catch {
      case e:Throwable => Failure(e)
    }
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
          throw new RuntimeException("access denied")
        }

        // datasetが削除されていないか
        getDataset(datasetId) match {
          case Some(_) => // do nothing
          case None => throw new NotFoundException
        }

        val file = persistence.File.find(fileId)
        val fh = persistence.FileHistory.syntax("fh")
        val filePath = withSQL {
          select(fh.result.filePath)
            .from(persistence.FileHistory as fh)
            .where
            .eq(fh.fileId, sqls.uuid(fileId))
            .and
            .isNull(fh.deletedAt)
        }.map(rs => rs.string(fh.resultName.filePath)).single().apply
        file match {
          case Some(f) => (f, filePath.get)
          case None => throw new RuntimeException("data not found.")
        }
      }

      val filePath = Paths.get(AppConf.fileDir, fileInfo._2).toFile
      if (!filePath.exists()) throw new RuntimeException("file not found")

      val file = new java.io.File(filePath.toString)
      Success((file, fileInfo._1.name))
    } catch {
      case e: Exception => Failure(e)
    }
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
    withSQL {
      select(sqls.max(o.accessLevel).append(sqls"access_level"))
        .from(persistence.Ownership as o)
        .where
          .eq(o.datasetId, sqls.uuid(id))
          .and
          .inUuid(o.groupId, Seq.concat(groups, Seq(AppConf.guestGroupId)))
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
            //organization = rs.stringOpt(u.resultName.organization).getOrElse(""),
            //title = rs.stringOpt(u.resultName.title).getOrElse(""),
            image = AppConf.imageDownloadRoot + rs.stringOpt(u.resultName.imageId).getOrElse(rs.string(gi.resultName.imageId)),
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
        //organization = rs.stringOpt(u.resultName.organization).getOrElse(""),
        //title = rs.stringOpt(u.resultName.title).getOrElse(""),
        image = AppConf.imageDownloadRoot +  rs.stringOpt(u.resultName.imageId).getOrElse(rs.string(gi.resultName.imageId)),
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
          url = AppConf.imageDownloadRoot + x._2.id
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
    ).list.apply.map(x =>
      DatasetData.DatasetFile(
        id = x._1.id,
        name = x._1.name,
        description = x._1.description,
        url = AppConf.fileDownloadRoot + datasetId + "/" + x._1.id,
        size = x._1.fileSize,
        createdBy = User(x._2, x._4),
        createdAt = x._1.createdAt.toString(),
        updatedBy = User(x._3, x._5),
        updatedAt = x._1.updatedAt.toString()
      )
    )
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
}