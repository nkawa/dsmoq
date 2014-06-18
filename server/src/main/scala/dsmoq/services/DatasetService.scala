package dsmoq.services

import scala.util.{Failure, Try, Success}
import scalikejdbc._, SQLInterpolation._
import java.util.UUID
import java.nio.file.Paths
import dsmoq.AppConf
import dsmoq.services.data._
import dsmoq.persistence
import dsmoq.persistence.PostgresqlHelper._
import dsmoq.exceptions._
import org.joda.time.DateTime
import org.scalatra.servlet.FileItem
import dsmoq.persistence.{GroupType, PresetType, OwnerType, DefaultAccessLevel, GroupAccessLevel, UserAccessLevel}
import dsmoq.logic.{StringUtil, ImageSaveLogic}
import scala.util.Failure
import scala.Some
import scala.util.Success
import dsmoq.services.data.RangeSlice
import org.scalatra.servlet.FileItem
import dsmoq.forms.AccessControl
import dsmoq.services.data.RangeSliceSummary
import dsmoq.services.data.Image
import scala.collection.mutable

object DatasetService {
  // FIXME 暫定パラメータ
  // 以前の設計でgroupにも編集権限を付与するとしていた設計都合上の実装
  // 現在はgroupには編集権限は付与せず、user/groupによって権限値の意味が異なるため、
  // userかgroupかによって権限判定処理を修正する必要がある
  // (現状は同じ値を使用しているため動きはする)
  private val UserAndGroupAccessDeny = 0
  private val UserAndGroupAllowDownload = 2
  private val UserAndGroupAccessAllowAll = 3

  /**
   * データセットを新規作成します。
   * @param params
   * @return
   */
  def create(params: DatasetData.CreateDatasetParams): Try[DatasetData.Dataset] = {
    try {
      if (params.userInfo.isGuest) throw new NotAuthorizedException
      val files = params.files match {
        case Some(x) => x.filter(_.name.length != 0)
        case None => Seq.empty
      }
      if (files.size == 0) throw new InputValidationException(Map("files" -> "file is empty"))

      DB localTx { implicit s =>
        val myself = persistence.User.find(params.userInfo.id).get
        val myGroup = getPersonalGroup(myself.id).get

        val datasetId = UUID.randomUUID().toString
        val timestamp = DateTime.now()

        val f = files.map(f => {
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
            createdBy = params.userInfo,
            createdAt = timestamp.toString(),
            updatedBy = params.userInfo,
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
            organization = myself.organization,
            title = myself.title,
            image = AppConf.imageDownloadRoot + myself.imageId,
            accessLevel = ownership.accessLevel,
            ownerType = OwnerType.User
          )),
          defaultAccessLevel = DefaultAccessLevel.Deny,
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
      // input parameter check
      val offset = try {
        params.offset.getOrElse("0").toInt
      } catch {
        case e: Exception => throw new InputValidationException(Map("offset" -> "wrong parameter"))
      }
      val limit = try {
        params.limit.getOrElse("20").toInt
      } catch {
        case e: Exception => throw new InputValidationException(Map("limit" -> "wrong parameter"))
      }

      DB readOnly { implicit s =>
        val groups = getJoinedGroups(params.userInfo)
        // サブクエリ決定
        val subQuery = if (params.owner.isDefined && params.group.isDefined) {
          if (!params.userInfo.isGuest && isGroupMember(params.userInfo, params.group.get)) {
            filterOwnerAndGroupForUser(params.owner.get, params.group.get)
          } else {
            filterOwnerAndGroup(params.owner.get, params.group.get)
          }
        } else {
          params.owner match {
            case Some(x) => filterOwner(x)
            case None => params.group match {
              case Some(y) =>
                if (!params.userInfo.isGuest && isGroupMember(params.userInfo, params.group.get)) {
                  filterGroupForUser(y)
                } else {
                  filterGroup(y)
                }
              case None => allOwnerships()
            }
          }
        }
        val count = countDatasets(groups, subQuery)

        val summary = RangeSliceSummary(count, limit, offset)
        val results = if (count > offset) {
          getDatasetSummary(groups, subQuery, limit, offset, params.userInfo)
        } else {
          List.empty
        }
        Success(RangeSlice(summary, results))
      }
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def getDatasetSummary(groups: Seq[String], subQuery: TableAsAliasSQLSyntax, limit: Int, offset: Int, userInfo: User)(implicit s: DBSession) = {
    val datasets = findDatasets(groups, subQuery, limit, offset)
    val datasetIds = datasets.map(_._1.id)

    val owners = getOwnerGroups(datasetIds, userInfo)
    val guestAccessLevels = getGuestAccessLevel(datasetIds)
    val imageIds = getImageId(datasetIds)

    datasets.map(x => {
      val ds = x._1
      val permission = x._2
      val imageUrl = imageIds.get(ds.id) match {
        case Some(x) => AppConf.imageDownloadRoot + x
        case None => ""
      }
      DatasetData.DatasetsSummary(
        id = ds.id,
        name = ds.name,
        description = ds.description,
        image = imageUrl,
        attributes = getAttributes(ds.id),
        ownerships = owners.get(ds.id).getOrElse(Seq.empty),
        files = ds.filesCount,
        dataSize = ds.filesSize,
        defaultAccessLevel = guestAccessLevels.get(ds.id).getOrElse(0),
        permission = permission
      )
    })
  }

  /**
   * 指定したデータセットの詳細情報を取得します。
   * @param params
   * @return
   */
  def get(params: DatasetData.GetDatasetParams): Try[DatasetData.Dataset] = {
    try {
      DB readOnly { implicit s =>
        // データセットが存在しない場合例外
        val dataset = getDataset(params.id) match {
          case Some(x) => x
          case None => throw new NotFoundException
        }
        (for {
          groups <- Some(getJoinedGroups(params.userInfo))
          permission <- getPermission(params.id, groups)
          guestAccessLevel <- Some(getGuestAccessLevel(params.id))
          owners <- Some(getAllOwnerships(params.id, params.userInfo))
          files <- Some(getFiles(params.id))
          attributes <- Some(getAttributes(params.id))
          images <- Some(getImages(params.id))
          primaryImage <- getPrimaryImageId(params.id)
        } yield {
          println(dataset)
          // 権限チェック
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
      case e: Exception => Failure(e)
    }
  }

  /**
   *
   * @param user
   * @param item
   * @return
   */
  def setGroupAccessControl(user: User, item: AccessControl) = {
    try {
      if (user.isGuest) throw new NotAuthorizedException

      val accessLevel = try {
        item.accessLevel match {
          case Some(x) => x.toInt
          case None => throw new InputValidationException(Map("accessLevel" -> "access level is empty"))
        }
      } catch {
       case e: InputValidationException => throw e
       case e: Exception => throw new InputValidationException(Map("accessLevel" -> "access level is invalid"))
      }

      DB localTx { implicit s =>
        getDataset(item.datasetId) match {
          case Some(x) =>
            if (!isOwner(user.id, item.datasetId)) throw new NotAuthorizedException
          case None => throw new NotFoundException
        }

        val o = persistence.Ownership.o
        withSQL(
          select(o.result.*)
            .from(persistence.Ownership as o)
            .where
              .eq(o.datasetId, sqls.uuid(item.datasetId))
              .and
              .eq(o.groupId, sqls.uuid(item.groupId))
        ).map(persistence.Ownership(o.resultName)).single.apply match {
          case Some(x) =>
            if (item.accessLevel != x.accessLevel) {
              persistence.Ownership(
                id = x.id,
                datasetId = x.datasetId,
                groupId = x.groupId,
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
                datasetId = item.datasetId,
                groupId = item.groupId,
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
      case e: RuntimeException => Failure(e)
    }
  }

  def setAccessControl(params: DatasetData.AccessControlParams): Try[Seq[DatasetData.DatasetOwnership]] = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException
    // input validation
    if (params.ids.size != params.types.size || params.types.size != params.accessLevels.size) {
      throw new InputValidationException(Map("ids" -> "params are not same size"))
    }
    val accessLevels = try {
      params.accessLevels.map(_.toInt)
    } catch {
      case e: Exception => throw new InputValidationException(Map("accessLevel" -> "access level is not number"))
    }
    val types = try {
      params.types.map(_.toInt)
    } catch {
      case e: Exception => throw new InputValidationException(Map("type" -> "type is not number"))
    }

    DB localTx { implicit s =>
      getDataset(params.datasetId) match {
        case Some(x) =>
          if (!isOwner(params.userInfo.id, params.datasetId)) throw new NotAuthorizedException
        case None => throw new NotFoundException
      }

      val ownerships = (0 to params.ids.length - 1).map{ i =>
        val id = params.ids(i)
        val accessLevel = accessLevels(i)
        types(i) match {
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
                .eq(u.id, sqls.uuid(id))
                .and
                .eq(g.groupType, GroupType.Personal)
                .and
                .isNull(g.deletedAt)
                .and
                .isNull(m.deletedAt)
                .limit(1)
            }.map(rs => rs.string(g.resultName.id)).single().apply.get
            saveOrCreateOwnerships(params.userInfo, params.datasetId, groupId, accessLevel)

            val user = persistence.User.find(id).get
            DatasetData.DatasetOwnership(
              id = id,
              name = user.name,
              fullname = user.fullname,
              organization = user.organization,
              title = user.title,
              image = AppConf.imageDownloadRoot + user.imageId,
              accessLevel = accessLevel,
              ownerType = OwnerType.User
            )
          case OwnerType.Group =>
            saveOrCreateOwnerships(params.userInfo, params.datasetId, id, accessLevel)

            val group = persistence.Group.find(id).get
            DatasetData.DatasetOwnership(
              id = id,
              name = group.name,
              fullname = "",
              organization = "",
              title = "",
              image = "",
              accessLevel = accessLevel,
              ownerType = OwnerType.Group
            )
        }
      }
      Success(ownerships)
    }
  }

  def addFiles(params: DatasetData.AddFilesToDatasetParams): Try[DatasetData.DatasetAddFiles] = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException
    // input validation
    val files = params.files match {
      case Some(x) => x.filter(_.name.length != 0)
      case None => Seq.empty
    }
    if (files.size == 0) throw new InputValidationException(Map("files" -> "file is empty"))

    DB localTx { implicit s =>
      getDataset(params.datasetId) match {
        case Some(x) =>
          if (!isOwner(params.userInfo.id, params.datasetId)) throw new NotAuthorizedException
        case None => throw new NotFoundException
      }

      val myself = persistence.User.find(params.userInfo.id).get
      val timestamp = DateTime.now()
      val f = files.map(f => {
        val file = persistence.File.create(
          id = UUID.randomUUID.toString,
          datasetId = params.datasetId,
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
          filePath = "/" + params.datasetId + "/" + file.id + "/" + historyId,
          fileSize = f.size,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp
        )
        writeFile(params.datasetId, file.id, history.id, f)
        (file, history)
      })

      // datasetsのfiles_size, files_countの更新
      updateDatasetFileStatus(params.datasetId, myself.id, timestamp)

      Success(DatasetData.DatasetAddFiles(
        files = f.map(x => DatasetData.DatasetFile(
          id = x._1.id,
          name = x._1.name,
          description = x._1.description,
          size = x._2.fileSize,
          url = AppConf.fileDownloadRoot + params.datasetId + "/" + x._1.id,
          createdBy = params.userInfo,
          createdAt = timestamp.toString(),
          updatedBy = params.userInfo,
          updatedAt = timestamp.toString()
        ))
      ))
    }
  }

  def updateFile(params: DatasetData.UpdateFileParams) = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    val file = params.file match {
      case Some(x) => x
      case None => throw new InputValidationException(Map("file" -> "file is empty"))
    }
    if (file.name.length == 0) throw new InputValidationException(Map("file" -> "file is empty"))

    DB localTx { implicit s =>
      getDataset(params.datasetId) match {
        case Some(x) =>
          if (!isOwner(params.userInfo.id, params.datasetId)) throw new NotAuthorizedException
        case None => throw new NotFoundException
      }
      if (!isValidFile(params.datasetId, params.fileId)) throw new NotFoundException

      val myself = persistence.User.find(params.userInfo.id).get
      val timestamp = DateTime.now()

      withSQL {
        val f = persistence.File.column
        update(persistence.File)
          .set(f.fileSize -> file.size, f.updatedBy -> sqls.uuid(myself.id), f.updatedAt -> timestamp)
          .where
          .eq(f.id, sqls.uuid(params.fileId))
      }.update().apply

      val historyId = UUID.randomUUID.toString
      val history = persistence.FileHistory.create(
        id = historyId,
        fileId = params.fileId,
        fileType = 0,
        fileMime = "application/octet-stream",
        filePath = "/" + params.datasetId + "/" + params.fileId + "/" + historyId,
        fileSize = file.size,
        createdBy = myself.id,
        createdAt = timestamp,
        updatedBy = myself.id,
        updatedAt = timestamp
      )
      writeFile(params.datasetId, params.fileId, history.id, file)

      // datasetsのfiles_size, files_countの更新
      updateDatasetFileStatus(params.datasetId, myself.id, timestamp)

      val result = persistence.File.find(params.fileId).get
      Success(DatasetData.DatasetFile(
        id = result.id,
        name = result.name,
        description = result.description,
        size = result.fileSize,
        url = AppConf.fileDownloadRoot + params.datasetId + "/" + result.id,
        createdBy = params.userInfo,
        createdAt = timestamp.toString(),
        updatedBy = params.userInfo,
        updatedAt = timestamp.toString()
      ))
    }
  }

  def modifyFileMetadata(params: DatasetData.ModifyDatasetMetadataParams) = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    // input validation
    val errors = mutable.LinkedHashMap.empty[String, String]
    val filename = StringUtil.trimAllSpaces(params.filename.getOrElse(""))
    if (filename.isEmpty) {
      errors.put("name", "name is empty")
    }
    val description = params.description.getOrElse("")

    if (errors.size != 0) {
      throw new InputValidationException(errors)
    }

    try {
      DB localTx { implicit s =>
        getDataset(params.datasetId) match {
          case Some(x) =>
            if (!isOwner(params.userInfo.id, params.datasetId)) throw new NotAuthorizedException
          case None => throw new NotFoundException
        }
        if (!isValidFile(params.datasetId, params.fileId)) throw new NotFoundException

        val myself = persistence.User.find(params.userInfo.id).get
        val timestamp = DateTime.now()
        withSQL {
          val f = persistence.File.column
          update(persistence.File)
            .set(f.name -> filename, f.description -> description,
              f.updatedBy -> sqls.uuid(myself.id), f.updatedAt -> timestamp)
            .where
            .eq(f.id, sqls.uuid(params.fileId))
            .and
            .eq(f.datasetId, sqls.uuid(params.datasetId))
        }.update().apply

        val result = persistence.File.find(params.fileId).get
        Success(DatasetData.DatasetFile(
          id = result.id,
          name = result.name,
          description = result.description,
          size = result.fileSize,
          url = AppConf.fileDownloadRoot + params.datasetId + "/" + result.id,
          createdBy = params.userInfo,
          createdAt = timestamp.toString(),
          updatedBy = params.userInfo,
          updatedAt = timestamp.toString()
        ))
      }
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def deleteDatasetFile(params: DatasetData.DeleteDatasetFileParams): Try[String] = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    try {
      DB localTx { implicit s =>
        getDataset(params.datasetId) match {
          case Some(x) =>
            if (!isOwner(params.userInfo.id, params.datasetId)) throw new NotAuthorizedException
          case None => throw new NotFoundException
        }
        if (!isValidFile(params.datasetId, params.fileId)) throw new NotFoundException

        val myself = persistence.User.find(params.userInfo.id).get
        val timestamp = DateTime.now()

        withSQL {
          val f = persistence.File.column
          update(persistence.File)
            .set(f.deletedBy -> sqls.uuid(myself.id), f.deletedAt -> timestamp,
              f.updatedBy -> sqls.uuid(myself.id), f.updatedAt -> timestamp)
            .where
            .eq(f.id, sqls.uuid(params.fileId))
            .and
            .eq(f.datasetId, sqls.uuid(params.datasetId))
            .and
            .isNull(f.deletedAt)
        }.update().apply

        // datasetsのfiles_size, files_countの更新
        updateDatasetFileStatus(params.datasetId, myself.id, timestamp)

        Success(params.fileId)
      }
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def modifyDatasetMeta(params: DatasetData.ModifyDatasetMetaParams): Try[String] = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    try {
      DB localTx { implicit s =>
        // input parameter check
        val errors = mutable.LinkedHashMap.empty[String, String]
        val name = StringUtil.trimAllSpaces(params.name.getOrElse(""))
        if (name.isEmpty) {
          errors.put("name", "name is empty")
        }
        val description = params.description.getOrElse("")
        val licenseId = params.licenseId.getOrElse("")
        if (licenseId.isEmpty) {
            errors.put("license", "license is empty")
        } else {
          // licenseの存在チェック
          if (StringUtil.isUUID(licenseId)) {
            if (persistence.License.find(licenseId).isEmpty) {
              errors.put("license", "license is invalid")
            }
          } else {
            errors.put("license", "license is invalid")
          }
        }
        if (errors.size != 0) {
          throw new InputValidationException(errors)
        }

        getDataset(params.datasetId) match {
          case Some(x) =>
            if (!isOwner(params.userInfo.id, params.datasetId)) throw new NotAuthorizedException
          case None => throw new NotFoundException
        }

        val myself = persistence.User.find(params.userInfo.id).get
        val timestamp = DateTime.now()

        withSQL {
          val d = persistence.Dataset.column
          update(persistence.Dataset)
            .set(d.name -> name, d.description -> description, d.licenseId -> sqls.uuid(licenseId),
              d.updatedBy -> sqls.uuid(myself.id), d.updatedAt -> timestamp)
            .where
            .eq(d.id, sqls.uuid(params.datasetId))
        }.update().apply

        val da = persistence.DatasetAnnotation.syntax("da")
        val a = persistence.Annotation.syntax("a")
        // 先に指定datasetに関連するannotation(name)を取得(あとで差分チェックするため)
        val oldAnnotations = withSQL {
          select(a.result.*)
            .from(persistence.Annotation as a)
            .innerJoin(persistence.DatasetAnnotation as da).on(sqls.eq(da.annotationId, a.id).and.isNull(da.deletedAt))
            .where
            .eq(da.datasetId, sqls.uuid(params.datasetId))
            .and
            .isNull(a.deletedAt)
        }.map(rs => (rs.string(a.resultName.name).toLowerCase, rs.string(a.resultName.id))).list().apply

        // 既存DatasetAnnotation全削除
        withSQL {
          delete.from(persistence.DatasetAnnotation as da)
            .where
            .eq(da.datasetId, sqls.uuid(params.datasetId))
        }.update().apply

        // annotation(name)が既存のものかチェック なければ作る
        val annotationMap = withSQL {
          select(a.result.*)
            .from(persistence.Annotation as a)
            .where
            .isNull(a.deletedAt)
        }.map(rs => (rs.string(a.resultName.name).toLowerCase, rs.string(a.resultName.id))).list().apply.toMap

        val attributes = params.attributes.map(x => x._1 -> StringUtil.trimAllSpaces(x._2))
        attributes.foreach { x =>
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
              datasetId = params.datasetId,
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
          if (!attributes.map(_._1.toLowerCase).contains(x._1)) {
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
      Success(params.datasetId)
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def addImages(params: DatasetData.AddImagesToDatasetParams): Try[DatasetData.DatasetAddImages] = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException
    val inputImages = params.images match {
      case Some(x) => x.filter(_.name.length != 0)
      case None => Seq.empty
    }
    if (inputImages.size == 0) throw new InputValidationException(Map("image" -> "image is empty"))

    DB localTx { implicit s =>
      if (!isOwner(params.userInfo.id, params.datasetId)) throw new NotAuthorizedException

      val myself = persistence.User.find(params.userInfo.id).get
      val timestamp = DateTime.now()
      val primaryImage = getPrimaryImageId(params.datasetId)
      var isFirst = true

      val images = inputImages.map(i => {
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
          datasetId = params.datasetId,
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
      primaryImage = getPrimaryImageId(params.datasetId).getOrElse("")
      ))
    }
  }

  def changePrimaryImage(params: DatasetData.ChangePrimaryImageParams) = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    val imageId = params.id match {
      case Some(x) => x
      case None => throw new InputValidationException(Map("id" -> "ID is empty"))
    }

    DB localTx { implicit s =>
      getDataset(params.datasetId) match {
        case Some(x) =>
          if (!isOwner(params.userInfo.id, params.datasetId)) throw new NotAuthorizedException
        case None => throw new NotFoundException
      }
      if (!isValidImage(params.datasetId, imageId)) throw new NotFoundException

      val myself = persistence.User.find(params.userInfo.id).get
      val timestamp = DateTime.now()
      withSQL {
        val di = persistence.DatasetImage.column
        update(persistence.DatasetImage)
          .set(di.isPrimary -> true, di.updatedBy -> sqls.uuid(myself.id), di.updatedAt -> timestamp)
          .where
          .eq(di.imageId, sqls.uuid(imageId))
          .and
          .eq(di.datasetId, sqls.uuid(params.datasetId))
          .and
          .isNull(di.deletedAt)
      }.update().apply

      withSQL{
        val di = persistence.DatasetImage.column
        update(persistence.DatasetImage)
          .set(di.isPrimary -> false, di.updatedBy -> sqls.uuid(myself.id), di.updatedAt -> timestamp)
          .where
          .ne(di.imageId, sqls.uuid(imageId))
          .and
          .eq(di.datasetId, sqls.uuid(params.datasetId))
          .and
          .isNull(di.deletedAt)
      }.update().apply

      Success(params.id)
    }
  }

  def deleteImage(params: DatasetData.DeleteImageParams) = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    val primaryImage = DB localTx { implicit s =>
      getDataset(params.datasetId) match {
        case Some(x) =>
          if (!isOwner(params.userInfo.id, params.datasetId)) throw new NotAuthorizedException
        case None => throw new NotFoundException
      }
      if (!isValidImage(params.datasetId, params.imageId)) throw new NotFoundException

      val myself = persistence.User.find(params.userInfo.id).get
      val timestamp = DateTime.now()
      withSQL {
        val di = persistence.DatasetImage.column
        update(persistence.DatasetImage)
          .set(di.deletedBy -> sqls.uuid(myself.id), di.deletedAt -> timestamp, di.isPrimary -> false,
            di.updatedBy -> sqls.uuid(myself.id), di.updatedAt -> timestamp)
          .where
          .eq(di.datasetId, sqls.uuid(params.datasetId))
          .and
          .eq(di.imageId, sqls.uuid(params.imageId))
          .and
          .isNull(di.deletedAt)
      }.update().apply

      getPrimaryImageId(params.datasetId) match {
        case Some(x) => x
        case None =>
          // primaryImageの差し替え
          val di = persistence.DatasetImage.syntax("di")
          val i = persistence.Image.syntax("i")

          // primaryImageとなるImageを取得
          val primaryImage = withSQL {
            select(di.result.id, i.result.id)
              .from(persistence.Image as i)
              .innerJoin(persistence.DatasetImage as di).on(i.id, di.imageId)
              .where
              .eq(di.datasetId, sqls.uuid(params.datasetId))
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
      }
    }

    Success(DatasetData.DatasetDeleteImage(
      primaryImage = primaryImage
    ))
  }

  def deleteDataset(user: User, datasetId: String) = {
    if (user.isGuest) throw new NotAuthorizedException

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
    datasetId
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
          DatasetService.getGuestAccessLevel(datasetId)
        } else {
          val groups = DatasetService.getJoinedGroups(user)
          DatasetService.getPermission(datasetId, groups).getOrElse(UserAndGroupAccessDeny)
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

  private def countDatasets(groups : Seq[String], subQuery: TableAsAliasSQLSyntax)(implicit s: DBSession) = {
    val ds = persistence.Dataset.syntax("ds")
    val o = persistence.Ownership.syntax("o")
    val x = SubQuery.syntax("o", o.resultName)
    withSQL {
      select(sqls.count(sqls.distinct(ds.id)).append(sqls"count"))
        .from(persistence.Dataset as ds)
        .innerJoin(subQuery).on(x(o).datasetId, ds.id)
        .where
        .inByUuid(x(o).groupId, Seq.concat(groups, Seq(AppConf.guestGroupId)))
        .and
        .gt(x(o).accessLevel, 0)
        .and
        .isNull(ds.deletedAt)
        .and
        .isNull(x(o).deletedAt)
    }.map(implicit rs => rs.int("count")).single().apply().get
  }

  private def findDatasets(groups: Seq[String], subQuery: TableAsAliasSQLSyntax, limit: Int, offset: Int)(implicit s: DBSession) = {
    val ds = persistence.Dataset.syntax("ds")
    val o = persistence.Ownership.syntax("o")
    val x = SubQuery.syntax("o", o.resultName)
    withSQL {
      select(ds.result.*, sqls.max(x(o).accessLevel).append(sqls"access_level"))
        .from(persistence.Dataset as ds)
        .innerJoin(subQuery).on(ds.id, x(o).datasetId)
        .where
        .inByUuid(x(o).groupId, Seq.concat(groups, Seq(AppConf.guestGroupId)))
        .and
        .gt(x(o).accessLevel, UserAndGroupAccessDeny)
        .and
        .isNull(ds.deletedAt)
        .and
        .isNull(x(o).deletedAt)
        .groupBy(ds.*)
        .orderBy(ds.updatedAt).desc
        .offset(offset)
        .limit(limit)
    }.map(rs => (persistence.Dataset(ds.resultName)(rs), rs.int("access_level"))).list().apply()
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

  private def getImageId(datasetIds: Seq[String])(implicit s: DBSession): Map[String, String] = {
    if (datasetIds.nonEmpty) {
      val di = persistence.DatasetImage.syntax("di")
      withSQL {
        select(di.result.datasetId, di.result.imageId)
          .from(persistence.DatasetImage as di)
          .where
          .inByUuid(di.datasetId, datasetIds)
          .and
          .eq(di.isPrimary, true)
          .and
          .isNull(di.deletedAt)
      }.map(x => (x.string(di.resultName.datasetId), x.string(di.resultName.imageId))).list().apply().toMap
    } else {
      Map.empty
    }
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
            .inByUuid(o.datasetId, datasetIds)
            .and
            // FIXME 暫定措置
            // 以前の設計でgroupにも編集権限を付与するとしていた設計都合上の実装
            // 現在はgroupには編集権限は付与せず、user/groupによって権限値の意味が異なるため、
            // userかgroupかによって権限判定処理を修正する必要がある
            // (現状は同じ値を使用しているため動きはする)
            .eq(o.accessLevel, UserAndGroupAccessAllowAll)
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
          // FIXME 暫定措置
          // 以前の設計でgroupにも編集権限を付与するとしていた設計都合上の実装
          // 現在はgroupには編集権限は付与せず、user/groupによって権限値の意味が異なるため、
          // userかgroupかによって権限判定処理を修正する必要がある
          // (現状は同じ値を使用しているため動きはする)
          .gt(o.accessLevel, UserAndGroupAccessDeny)
          .and
          .isNull(o.deletedAt)
    }.map(rs =>
      DatasetData.DatasetOwnership(
        id = rs.stringOpt(u.resultName.id).getOrElse(rs.string(g.resultName.id)),
        name = rs.stringOpt(u.resultName.name).getOrElse(rs.string(g.resultName.name)),
        fullname = rs.stringOpt(u.resultName.fullname).getOrElse(""),
        organization = rs.stringOpt(u.resultName.organization).getOrElse(""),
        title = rs.stringOpt(u.resultName.title).getOrElse(""),
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

  private def isValidImage(datasetId: String, imageId: String)(implicit s: DBSession) = {
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

  private def isValidFile(datasetId: String, fileId: String)(implicit s: DBSession) = {
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
    }.map(rs => rs.string(f.resultName.id)).single().apply match {
      case Some(x) => true
      case None => false
    }
  }

  private def saveOrCreateOwnerships(userInfo: User,datasetId: String, groupId: String, accessLevel: Int)(implicit s: DBSession) {
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

  // データセット一覧ownershipサブクエリ 絞り込み条件なし
  private def allOwnerships() = {
    val o = persistence.Ownership.syntax("o")
    val x = SubQuery.syntax("o", o.resultName)
    select(o.result.*).from(persistence.Ownership as o).where.isNull(o.deletedAt)as(x)
  }

  // データセット一覧ownershipサブクエリ onwer(user_id)で絞り込み
  private def filterOwner(owner: String) = {
    val o = persistence.Ownership.syntax("o")
    val o1 = persistence.Ownership.syntax("o1")
    val g = persistence.Group.syntax("g")
    val m = persistence.Member.syntax("m")
    val x = SubQuery.syntax("o", o.resultName)
    val y = SubQuery.syntax("o1", o1.resultName)
    select(o.result.*)
      .from(persistence.Ownership as o)
      .innerJoin(
        select(o1.result.*)
        .from(persistence.Ownership as o1)
        .innerJoin(persistence.Group as g).on(sqls.eq(o1.groupId, g.id).and.isNull(g.deletedAt))
        .innerJoin(persistence.Member as m).on(sqls.eq(g.id, m.groupId).and.isNull(m.deletedAt))
        .where
          .eq(m.userId, sqls.uuid(owner))
          .and
          .eq(o1.accessLevel, UserAccessLevel.Owner)
          .and
          .eq(g.groupType, GroupType.Personal)
          .as(y)
      ).on(o.datasetId, y(o1).datasetId)
      .as(x)
  }

  // データセット一覧ownershipサブクエリ groupで絞り込み(ユーザー非ログイン時)
  private def filterGroup(group: String) = {
    val o = persistence.Ownership.syntax("o")
    val o1 = persistence.Ownership.syntax("o2")
    val g = persistence.Group.syntax("g")
    val x = SubQuery.syntax("o", o.resultName)
    select(sqls.distinct(o.result.*))
      .from(persistence.Ownership as o)
      .innerJoin(persistence.Ownership as o1).on(sqls.eq(o.datasetId, o1.datasetId).and.isNull(o1.deletedAt))
      .innerJoin(persistence.Group as g).on(sqls.eq(o1.groupId, g.id).and.isNull(g.deletedAt))
      .where
        .eq(g.id, sqls.uuid(group))
        .and
        .eq(g.groupType, GroupType.Public)
        .and
        .eq(o1.accessLevel, GroupAccessLevel.Provider)
        .and
        .isNull(o.deletedAt)
      .as(x)
  }

  // データセット一覧ownershipサブクエリ groupで絞り込み(ユーザーログイン時)
  private def filterGroupForUser(group: String) = {
    val o = persistence.Ownership.syntax("o")
    val o1 = persistence.Ownership.syntax("o2")
    val g = persistence.Group.syntax("g")
    val x = SubQuery.syntax("o", o.resultName)
    select(sqls.distinct(o.result.*))
      .from(persistence.Ownership as o)
      .innerJoin(persistence.Ownership as o1).on(sqls.eq(o.datasetId, o1.datasetId).and.isNull(o1.deletedAt))
      .innerJoin(persistence.Group as g).on(sqls.eq(o1.groupId, g.id).and.isNull(g.deletedAt))
      .where
        .eq(g.id, sqls.uuid(group))
        .and
        .eq(g.groupType, GroupType.Public)
        .and
        .gt(o1.accessLevel, GroupAccessLevel.Deny)
        .and
        .isNull(o.deletedAt)
      .as(x)
  }

  // データセット一覧ownershipサブクエリ onwer(user_id), groupで絞り込み(ユーザー非ログイン時)
  private def filterOwnerAndGroup(owner: String, group: String) = {
    val o = persistence.Ownership.syntax("o")
    val o1 = persistence.Ownership.syntax("o1")
    val o2 = persistence.Ownership.syntax("o2")
    val g1 = persistence.Group.syntax("g1")
    val g2 = persistence.Group.syntax("g2")
    val m = persistence.Member.syntax("m")
    val x = SubQuery.syntax("o", o.resultName)
    val y = SubQuery.syntax("o1", o1.resultName)
    select(sqls.distinct(o.result.*))
      .from(persistence.Ownership as o)
      .innerJoin(
        select(sqls.distinct(o1.result.*))
          .from(persistence.Ownership as o1)
          .innerJoin(persistence.Group as g1).on(sqls.eq(o1.groupId, g1.id).and.isNull(g1.deletedAt))
          .innerJoin(persistence.Member as m).on(sqls.eq(g1.id, m.groupId).and.isNull(m.deletedAt))
          .where
          .eq(m.userId, sqls.uuid(owner))
          .and
          .eq(o1.accessLevel, UserAccessLevel.Owner)
          .and
          .eq(g1.groupType, GroupType.Personal)
          .as(y)
      ).on(o.datasetId, y(o1).datasetId)
      .innerJoin(persistence.Ownership as o2).on(sqls.eq(y(o1).datasetId, o2.datasetId).and.isNull(o2.deletedAt))
      .innerJoin(persistence.Group as g2).on(sqls.eq(o2.groupId, g2.id).and.isNull(g2.deletedAt))
      .where
      .eq(g2.id, sqls.uuid(group))
      .and
      .eq(g2.groupType, GroupType.Public)
      .and
      .eq(o2.accessLevel, GroupAccessLevel.Provider)
      .and
      .isNull(o.deletedAt)
      .as(x)
  }

  // データセット一覧ownershipサブクエリ onwer(user_id), groupで絞り込み, ユーザーログイン状態
  private def filterOwnerAndGroupForUser(owner: String, group: String) = {
    val o = persistence.Ownership.syntax("o")
    val o1 = persistence.Ownership.syntax("o1")
    val o2 = persistence.Ownership.syntax("o2")
    val g1 = persistence.Group.syntax("g1")
    val g2 = persistence.Group.syntax("g2")
    val m = persistence.Member.syntax("m")
    val x = SubQuery.syntax("o", o.resultName)
    val y = SubQuery.syntax("o1", o1.resultName)
    select(sqls.distinct(o.result.*))
      .from(persistence.Ownership as o)
      .innerJoin(
        select(sqls.distinct(o1.result.*))
          .from(persistence.Ownership as o1)
          .innerJoin(persistence.Group as g1).on(sqls.eq(o1.groupId, g1.id).and.isNull(g1.deletedAt))
          .innerJoin(persistence.Member as m).on(sqls.eq(g1.id, m.groupId).and.isNull(m.deletedAt))
          .where
            .eq(m.userId, sqls.uuid(owner))
            .and
            .eq(o1.accessLevel, UserAccessLevel.Owner)
            .and
            .eq(g1.groupType, GroupType.Personal)
            .as(y)
      ).on(o.datasetId, y(o1).datasetId)
      .innerJoin(persistence.Ownership as o2).on(sqls.eq(y(o1).datasetId, o2.datasetId).and.isNull(o2.deletedAt))
      .innerJoin(persistence.Group as g2).on(sqls.eq(o2.groupId, g2.id).and.isNull(g2.deletedAt))
      .where
        .eq(g2.id, sqls.uuid(group))
        .and
        .eq(g2.groupType, GroupType.Public)
        .and
        .gt(o2.accessLevel, GroupAccessLevel.Deny)
        .and
        .isNull(o.deletedAt)
      .as(x)
  }

  private def isGroupMember(user: User, groupId: String)(implicit s: DBSession) = {
    val g = persistence.Group.syntax("g")
    val m = persistence.Member.syntax("m")
    withSQL {
      select(g.result.id)
      .from(persistence.Group as g)
      .innerJoin(persistence.Member as m).on(sqls.eq(g.id, m.groupId).and.isNull(m.deletedAt))
      .where
      .eq(g.id, sqls.uuid(groupId))
      .and
      .eq(m.userId, sqls.uuid(user.id))
      .and
      .isNull(g.deletedAt)
  }.map(_.string(g.resultName.id)).single().apply() match {
      case Some(x) => true
      case None => false
    }
  }
}