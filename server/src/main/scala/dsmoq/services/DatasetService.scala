package dsmoq.services

import scala.util.{Failure, Try, Success}
import scalikejdbc._, SQLInterpolation._
import java.util.UUID
import java.nio.file.Paths
import dsmoq.AppConf
import dsmoq.services.data._
import dsmoq.persistence
import dsmoq.persistence.PostgresqlHelper._
import dsmoq.exceptions.{InputValidationException, NotFoundException, ValidationException, NotAuthorizedException}
import org.joda.time.DateTime
import org.scalatra.servlet.FileItem
import dsmoq.forms.{AccessCrontolItem, AccessControl}
import dsmoq.persistence.{AccessLevel, GroupMemberRole}
import scala.collection.mutable.ArrayBuffer
import dsmoq.logic.ImageSaveLogic

object DatasetService {
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
          val historyId = UUID.randomUUID.toString
          val histroy = persistence.FileHistory.create(
            id = UUID.randomUUID.toString,
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
          name = files.head._1.name,
          description = "",
          filesCount = files.length,
          filesSize = files.map(x => x._2.fileSize).sum,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp)
        // FIXME licenseIdを指定してcreateできなかったため、データ作成後にupdateで対応
        withSQL {
          val d = persistence.Dataset.column
          update(persistence.Dataset)
            .set(d.licenseId -> sqls.uuid(AppConf.defaultLicenseId))
            .where
            .eq(d.id, sqls.uuid(dataset.id))
        }.update().apply
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
      // FIXME input parameter check
      val offset = try {
        params.offset.getOrElse("0").toInt
      } catch {
        case e: Exception => throw new InputValidationException("offset", "wrong parameter")
      }
      val limit = try {
        params.limit.getOrElse("20").toInt
      } catch {
        case e: Exception => throw new InputValidationException("limit", "wrong parameter")
      }

      DB readOnly { implicit s =>
        val groups = getJoinedGroups(params.userInfo)
        val count = countDatasets(groups)

        val summary = RangeSliceSummary(count, limit, offset)
        val results = if (count > offset) {
          getDatasetSummary(groups, limit, offset)
        } else {
          List.empty
        }
        Success(RangeSlice(summary, results))
      }
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def getDatasetSummary(groups: Seq[String], limit: Int, offset: Int)(implicit s: DBSession) = {
    val datasets = findDatasets(groups, limit, offset)
    val datasetIds = datasets.map(_._1.id)

    val owners = getOwnerGroups(datasetIds)
    val guestAccessLevels = getGuestAccessLevel(datasetIds)
    val imageIds = getImageId(datasetIds)

    datasets.map(x => {
      val ds = x._1
      val permission = x._2
      val imageUrl = imageIds.get(ds.id) match {
        case Some(x) => AppConf.imageDownloadRoot + x + "/48"
        case None => ""
      }
      DatasetData.DatasetsSummary(
        id = ds.id,
        name = ds.name,
        description = ds.description,
        image = imageUrl,
        attributes = Seq.empty, // TODO
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
        val dataset = try {
          getDataset(params.id) match {
            case Some(x) => x
            case None => throw new NotFoundException
          }
        } catch {
          case e: Exception =>
            throw new NotFoundException
        }
        (for {
          groups <- Some(getJoinedGroups(params.userInfo))
          permission <- getPermission(params.id, groups)
          guestAccessLevel <- Some(getGuestAccessLevel(params.id))
          owners <- Some(getAllOwnerships(params.id))
          files <- Some(getFiles(params.id))
          attributes <- Some(getAttributes(params.id))
          images <- Some(getImages(params.id))
          primaryImage <- getPrimaryImageId(params.id)
        } yield {
          println(dataset)
          // 権限チェック
          if ((params.userInfo.isGuest && guestAccessLevel == AccessLevel.Deny) ||
              (!params.userInfo.isGuest && permission == AccessLevel.Deny)) {
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
  def setAccessControl(user: User, item: AccessControl): Try[AccessCrontolItem] = {
    try {
      if (user.isGuest) throw new NotAuthorizedException

      DB localTx { implicit s =>
        if (!hasAllowAllPermission(user.id, item.datasetId))
            throw new NotAuthorizedException

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
                accessLevel = item.accessLevel,
                createdBy = x.createdBy,
                createdAt = x.createdAt,
                updatedBy = user.id,
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
                createdBy = user.id,
                createdAt = ts,
                updatedBy = user.id,
                updatedAt = ts
              )
            }
        }

        Success(AccessCrontolItem(
          id = item.groupId,
          name = "", //TODO
          image = "", //TODO
          accessLevel = item.accessLevel
        ))
      }
    } catch {
      case e: RuntimeException => Failure(e)
    }
  }

  def addFiles(params: DatasetData.AddFilesToDatasetParams): Try[DatasetData.DatasetAddFiles] = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException
    if (params.files.getOrElse(Seq.empty).isEmpty) throw new ValidationException

    DB localTx { implicit s =>
      if (!hasAllowAllPermission(params.userInfo.id, params.datasetId)) throw new NotAuthorizedException

      val myself = persistence.User.find(params.userInfo.id).get
      val timestamp = DateTime.now()
      val files = params.files.getOrElse(Seq.empty).map(f => {
        // FIXME ファイルサイズ=0のデータ時の措置(現状何も回避していない)
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
        files = files.map(x => DatasetData.DatasetFile(
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
      case None => throw new ValidationException
    }

    DB localTx { implicit s =>
      if (!hasAllowAllPermission(params.userInfo.id, params.datasetId)) throw new NotAuthorizedException

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
        id = UUID.randomUUID.toString,
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
      Success(DatasetData.DatasetAddFiles(
        files = ArrayBuffer(DatasetData.DatasetFile(
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
      ))
    }
  }

  def modifyFilename(params: DatasetData.ModifyDatasetFilenameParams): Try[String] = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    try {
      DB localTx { implicit s =>
        if (!hasAllowAllPermission(params.userInfo.id, params.datasetId)) throw new NotAuthorizedException

        val myself = persistence.User.find(params.userInfo.id).get
        val timestamp = DateTime.now()
        withSQL {
          val f = persistence.File.column
          update(persistence.File)
            .set(f.name -> params.filename,
              f.updatedBy -> sqls.uuid(myself.id), f.updatedAt -> timestamp)
            .where
            .eq(f.id, sqls.uuid(params.fileId))
            .and
            .eq(f.datasetId, sqls.uuid(params.datasetId))
        }.update().apply
      }
      Success(params.filename)
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def deleteDatasetFile(params: DatasetData.DeleteDatasetFileParams): Try[String] = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    try {
      DB localTx { implicit s =>
        if (!hasAllowAllPermission(params.userInfo.id, params.datasetId)) throw new NotAuthorizedException

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
        if (!hasAllowAllPermission(params.userInfo.id, params.datasetId)) throw new NotAuthorizedException

        val myself = persistence.User.find(params.userInfo.id).get
        val timestamp = DateTime.now()

        withSQL {
          val d = persistence.Dataset.column
          update(persistence.Dataset)
            .set(d.name -> params.name, d.description -> params.description, d.licenseId -> sqls.uuid(params.licenseId),
              d.updatedBy -> sqls.uuid(myself.id), d.updatedAt -> timestamp)
            .where
            .eq(d.id, sqls.uuid(params.datasetId))
        }.update().apply

        // attributesデータ削除(物理削除)
        val da = persistence.DatasetAnnotation.syntax("da")
        val a = persistence.Annotation.syntax("a")
        val annotationIdList = withSQL {
          select(da.result.annotationId)
            .from(persistence.DatasetAnnotation as da)
            .where
            .eq(da.datasetId, sqls.uuid(params.datasetId))
            .and
            .isNull(da.deletedAt)
        }.map(rs => rs.string(da.resultName.annotationId)).list().apply
        withSQL {
          delete.from(persistence.DatasetAnnotation as da)
            .where
            .eq(da.datasetId, sqls.uuid(params.datasetId))
        }.update().apply
        if (annotationIdList.nonEmpty) {
          withSQL {
            delete.from(persistence.Annotation as a)
              .where
              .inByUuid(a.id, annotationIdList)
          }.update().apply
        }

        // attributesデータ作成
        params.attributes.foreach { x =>
          val annotationId = UUID.randomUUID().toString
          persistence.Annotation.create(
            id = annotationId,
            name = x._1,
            createdBy =  myself.id,
            createdAt =  timestamp,
            updatedBy =  myself.id,
            updatedAt =  timestamp
          )
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
      Success(params.datasetId)
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def addImages(params: DatasetData.AddImagesToDatasetParams): Try[DatasetData.DatasetAddImages] = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException
    if (params.images.getOrElse(Seq.empty).isEmpty) throw new ValidationException

    DB localTx { implicit s =>
      if (!hasAllowAllPermission(params.userInfo.id, params.datasetId)) throw new NotAuthorizedException

      val myself = persistence.User.find(params.userInfo.id).get
      val timestamp = DateTime.now()
      val primaryImage = getPrimaryImageId(params.datasetId)
      var isFirst = true
      val inputImages = params.images match {
        case Some(x) => x.filter(_.size > 0)
        case None => Seq.empty
      }

      val images = inputImages.map(i => {
        val imageId = UUID.randomUUID().toString
        val bufferedImage = javax.imageio.ImageIO.read(i.getInputStream)

        val image = persistence.Image.create(
          id = imageId,
          name = i.getName,
          width = bufferedImage.getWidth,
          height = bufferedImage.getWidth,
          filePath = "/" + imageId,
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
          displayOrder = 999, // 廃止予定値
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

    DB localTx { implicit s =>
      if (!hasAllowAllPermission(params.userInfo.id, params.datasetId)) throw new NotAuthorizedException

      val myself = persistence.User.find(params.userInfo.id).get
      val timestamp = DateTime.now()
      withSQL {
        val di = persistence.DatasetImage.column
        update(persistence.DatasetImage)
          .set(di.isPrimary -> true, di.updatedBy -> sqls.uuid(myself.id), di.updatedAt -> timestamp)
          .where
          .eq(di.imageId, sqls.uuid(params.id))
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
          .ne(di.imageId, sqls.uuid(params.id))
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
      if (!hasAllowAllPermission(params.userInfo.id, params.datasetId)) throw new NotAuthorizedException

      val myself = persistence.User.find(params.userInfo.id).get
      val timestamp = DateTime.now()
      withSQL {
        // FIXME dataset_imageだけじゃなくimagesも削除する？
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
      try {
        getDataset(datasetId) match {
          case Some(x) => // do nothing
          case None => throw new NotFoundException
        }
      } catch {
        case e: Exception => throw new NotFoundException
      }

      if (!hasAllowAllPermission(user.id, datasetId)) throw new NotAuthorizedException
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

  private def hasAllowAllPermission(userId: String, datasetId: String)(implicit s: DBSession) = {
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
          .eq(o.accessLevel, persistence.AccessLevel.AllowAll)
          .and
          .isNull(o.deletedAt)
        .limit(1)
    }.map(x => true).single.apply().getOrElse(false)
  }

  private def getJoinedGroups(user: User)(implicit s: DBSession): Seq[String] = {
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

  private def getGroup(groupId: String)(implicit s: DBSession) = {
    if (groupId == AppConf.guestGroupId) {
      persistence.Group(
        id = groupId,
        name = "guest",
        description = "",
        groupType = persistence.GroupType.Public,
        createdBy = AppConf.systemUserId,
        createdAt = new DateTime(0),
        updatedBy = AppConf.systemUserId,
        updatedAt = new DateTime(0))
    } else {
      persistence.Group.find(groupId).get
    }
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
    val d = persistence.Dataset.syntax("d")
    withSQL {
      select(d.result.*)
        .from(persistence.Dataset as d)
        .where
        .eq(d.id, sqls.uuid(id))
        .and
        .isNull(d.deletedAt)
    }.map(persistence.Dataset(d.resultName)).single.apply()
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

  private def getOwnerGroups(datasetIds: Seq[String])(implicit s: DBSession):Map[String, Seq[DatasetData.DatasetOwnership]] = {
    if (datasetIds.nonEmpty) {
      val o = persistence.Ownership.o
      val g = persistence.Group.g
      val m = persistence.Member.m
      val u = persistence.User.u
      withSQL {
        select(o.result.*, g.result.*, u.result.*)
          .from(persistence.Ownership as o)
          .innerJoin(persistence.Group as g)
            .on(sqls.eq(g.id, o.groupId).and.isNull(g.deletedAt))
          .leftJoin(persistence.Member as m)
            .on(sqls.eq(g.id, m.groupId)
                .and.eq(g.groupType, persistence.GroupType.Personal)
                .and.eq(m.role, persistence.GroupMemberRole.Administrator)
                .and.isNull(m.deletedAt))
          .innerJoin(persistence.User as u)
            .on(sqls.eq(m.userId, u.id).and.isNull(u.deletedAt))
          .where
            .inByUuid(o.datasetId, datasetIds)
            .and
            .eq(o.accessLevel, AccessLevel.AllowAll)
            .and
            .isNull(o.deletedAt)
      }.map(rs =>
        (
          rs.string(o.resultName.datasetId),
          DatasetData.DatasetOwnership(
            id = rs.string(g.resultName.id),
            name = rs.stringOpt(u.resultName.name).getOrElse(rs.string(g.resultName.name)),
            fullname = rs.stringOpt(u.resultName.fullname).getOrElse(""),
            organization = rs.stringOpt(u.resultName.organization).getOrElse(""),
            title = rs.stringOpt(u.resultName.title).getOrElse(""),
            image = "", //TODO
            accessLevel = rs.int(o.resultName.accessLevel)
          )
        )
      ).list().apply()
      .groupBy(_._1)
      .map(x => (x._1, x._2.map(_._2)))
    } else {
      Map.empty
    }
  }

  private def getAllOwnerships(datasetId: String)(implicit s: DBSession) = {
    // ゲストアカウント情報はownersテーブルに存在しないので、このメソッドからは取得されない
    val o = persistence.Ownership.o
    val g = persistence.Group.g
    val m = persistence.Member.m
    val u = persistence.User.u
    withSQL {
      select(o.result.*, g.result.*, u.result.*)
        .from(persistence.Ownership as o)
        .innerJoin(persistence.Group as g)
          .on(sqls.eq(g.id, o.groupId).and.isNull(g.deletedAt))
        .leftJoin(persistence.Member as m)
          .on(sqls.eq(g.id, m.groupId)
              .and.eq(g.groupType, persistence.GroupType.Personal)
              .and.eq(m.role, persistence.GroupMemberRole.Administrator)
              .and.isNull(m.deletedAt))
        .innerJoin(persistence.User as u)
          .on(sqls.eq(m.userId, u.id).and.isNull(u.deletedAt))
        .where
          .eq(o.datasetId, sqls.uuid(datasetId))
          .and
          .gt(o.accessLevel, AccessLevel.Deny)
          .and
          .isNull(o.deletedAt)
    }.map(rs =>
      DatasetData.DatasetOwnership(
        id = rs.string(g.resultName.id),
        name = rs.stringOpt(u.resultName.name).getOrElse(rs.string(g.resultName.name)),
        fullname = rs.stringOpt(u.resultName.fullname).getOrElse(""),
        organization = rs.stringOpt(u.resultName.organization).getOrElse(""),
        title = rs.stringOpt(u.resultName.title).getOrElse(""),
        image = "", //TODO
        accessLevel = rs.int(o.resultName.accessLevel)
      )
    ).list().apply()
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
        .orderBy(di.displayOrder)
    }.map(rs =>
      (
        persistence.DatasetImage(di.resultName)(rs),
        persistence.Image(i.resultName)(rs)
        )
      ).list.apply.map(x =>
        Image(
          id = x._2.id,
          url = AppConf.imageDownloadRoot + x._2.id + "/48"
        )
      )
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
        url = AppConf.fileDownloadRoot + datasetId + "/" + x._1.id,
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
}