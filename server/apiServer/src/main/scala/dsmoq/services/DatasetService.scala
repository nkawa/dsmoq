package dsmoq.services

import java.io.{ ByteArrayInputStream, Closeable, File, FileOutputStream, InputStream, SequenceInputStream }
import java.nio.charset.{ Charset, StandardCharsets }
import java.nio.file.{ Files, Path, Paths, StandardCopyOption }
import java.util.{ ResourceBundle, UUID }
import java.util.zip.ZipInputStream

import com.github.tototoshi.csv.CSVReader
import com.typesafe.scalalogging.LazyLogging
import dsmoq.exceptions.{ AccessDeniedException, BadRequestException, NotFoundException }
import dsmoq.logic.{ AppManager, FileManager, ImageSaveLogic, StringUtil, ZipUtil }
import dsmoq.{ AppConf, ResourceNames, persistence }
import dsmoq.persistence.PostgresqlHelper.{ PgConditionSQLBuilder, PgSQLSyntaxType }
import dsmoq.persistence.{ Annotation, Dataset, DatasetAnnotation, DatasetImage, DefaultAccessLevel, GroupAccessLevel, GroupType, OwnerType, Ownership, PresetType, UserAccessLevel, ZipedFiles }
import dsmoq.services.json.DatasetData.{ CopiedDataset, DatasetOwnership, DatasetTask, DatasetZipedFile }
import dsmoq.services.json.{ DatasetData, Image, RangeSlice, RangeSliceSummary, SearchDatasetCondition }
import org.apache.commons.io.input.BoundedInputStream
import org.joda.time.DateTime
import org.json4s.{ JBool, JInt }
import org.json4s.JsonDSL.{ jobject2assoc, pair2Assoc, string2jvalue }
import org.json4s.jackson.JsonMethods.{ compact, render }
import org.scalatra.servlet.FileItem
import org.slf4j.MarkerFactory
import scalikejdbc.interpolation.Implicits.{ scalikejdbcSQLInterpolationImplicitDef, scalikejdbcSQLSyntaxToStringImplicitDef }
import scalikejdbc.{ ConditionSQLBuilder, DB, DBSession, SQLSyntax, SelectSQLBuilder, SubQuery, delete, select, sqls, update, withSQL }

import scala.collection.mutable.{ ArrayBuffer, HashSet }
import scala.math.BigInt.int2bigInt
import scala.util.{ Failure, Success, Try }

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

  /** デフォルトの検索上限 */
  val DEFALUT_LIMIT = 20

  /**
   * データセットを新規作成します。
   *
   * @param files データセットに追加するファイルのリスト
   * @param saveLocal データセットをLocalに保存するか否か
   * @param saveS3 データセットをS3に保存するか否か
   * @param name データセット名
   * @param user ユーザ情報
   * @return
   *     Success(DatasetData.Dataset) 作成に成功した場合、作成したデータセットオブジェクト
   *     Failure(NullPointerException) 引数がnullの場合
   */
  def create(
    files: Seq[FileItem],
    saveLocal: Boolean,
    saveS3: Boolean,
    name: String,
    user: User
  ): Try[DatasetData.Dataset] = {
    Try {
      CheckUtil.checkNull(files, "files")
      CheckUtil.checkNull(saveLocal, "saveLocal")
      CheckUtil.checkNull(saveS3, "saveS3")
      CheckUtil.checkNull(name, "name")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        val myself = persistence.User.find(user.id).get
        val myGroup = getPersonalGroup(myself.id).get
        val datasetId = UUID.randomUUID().toString
        val timestamp = DateTime.now()
        val f = files.map { f =>
          // 拡張子を含み、大文字小文字を区別しない
          val isZip = f.getName.toLowerCase.endsWith(".zip")
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
          val realSize = if (isZip) {
            createZipedFiles(path, historyId, timestamp, myself).getOrElse {
              // 新規採番されたファイルヒストリIDに紐づくエラーIDを、新規登録する
              persistence.FileHistoryError.create(
                id = UUID.randomUUID().toString,
                historyId = historyId,
                createdBy = myself.id,
                createdAt = timestamp,
                updatedBy = myself.id,
                updatedAt = timestamp
              )

              // 展開できないZIPファイルのため、サイズはそのままとする
              f.size
            }
          } else {
            f.size
          }
          val histroy = persistence.FileHistory.create(
            id = historyId,
            fileId = file.id,
            fileType = 0,
            fileMime = "application/octet-stream",
            filePath = "/" + datasetId + "/" + file.id + "/" + historyId,
            fileSize = f.size,
            isZip = isZip,
            realSize = realSize,
            createdBy = myself.id,
            createdAt = timestamp,
            updatedBy = myself.id,
            updatedAt = timestamp
          )
          (file, histroy)
        }
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
        if (saveS3 && !f.isEmpty) {
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
          updatedAt = timestamp
        )
        val datasetImage = persistence.DatasetImage.create(
          id = UUID.randomUUID.toString,
          datasetId = dataset.id,
          imageId = AppConf.defaultDatasetImageId,
          isPrimary = true,
          isFeatured = true,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp
        )

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
            updatedAt = timestamp
          )
        }
        DatasetData.Dataset(
          id = dataset.id,
          meta = DatasetData.DatasetMetaData(
            name = dataset.name,
            description = dataset.description,
            license = dataset.licenseId,
            attributes = Seq.empty
          ),
          filesCount = dataset.filesCount,
          filesSize = dataset.filesSize,
          files = f.map { x =>
            DatasetData.DatasetFile(
              id = x._1.id,
              name = x._1.name,
              description = x._1.description,
              size = Some(x._2.fileSize),
              url = Some(AppConf.fileDownloadRoot + datasetId + "/" + x._1.id),
              createdBy = Some(user),
              createdAt = timestamp.toString(),
              updatedBy = Some(user),
              updatedAt = timestamp.toString(),
              isZip = x._2.isZip,
              zipedFiles = Seq.empty,
              zipCount = if (x._2.isZip) {
                getZippedFileAmounts(Seq(x._2.id)).headOption.map(x => x._2).getOrElse(0)
              } else {
                0
              }
            )
          },
          images = Seq(Image(
            id = AppConf.defaultDatasetImageId,
            url = datasetImageDownloadRoot + datasetId + "/" + AppConf.defaultDatasetImageId
          )),
          primaryImage = AppConf.defaultDatasetImageId,
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
        )
      }
    }
  }

  /**
   * ZipファイルからZip内ファイルを登録・作成する。
   *
   * @param path ファイルパス
   * @param historyId ファイル履歴ID
   * @param timestamp タイムスタンプ
   * @param myself ログインユーザオブジェクト
   * @return
   *     Success(Long) 作成に成功した場合、非圧縮サイズの合計値
   */
  private def createZipedFiles(
    path: Path,
    historyId: String,
    timestamp: DateTime,
    myself: persistence.User
  )(implicit s: DBSession): Try[Long] = {
    Try {
      val zipInfos = ZipUtil.read(path)
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
    }.recoverWith {
      case e: Exception =>
        logger.warn(LOG_MARKER, "error occurred in createZipedFiles.", e)
        Failure(e)
    }
  }

  /**
   * タスクを作成する。
   *
   * @param datasetId データセットID
   * @param commandType LocalあるいはS3の保存先変更を表すコマンド値(@see dsmoq.services.MoveToStatus)
   * @param userId ユーザID
   * @param timestamp タイムスタンプ
   * @param isSave 移動前のディレクトリを保存したままにしておくか否か
   * @param s DBセッション
   * @return タスクID
   */
  private def createTask(
    datasetId: String,
    commandType: Int,
    userId: String,
    timestamp: DateTime,
    isSave: Boolean
  )(implicit s: DBSession): String = {
    val id = UUID.randomUUID.toString
    persistence.Task.create(
      id = id,
      taskType = 0,
      parameter = compact(
        render(
          ("commandType" -> JInt(commandType))
            ~ ("datasetId" -> datasetId)
            ~ ("withDelete" -> JBool(!isSave))
        )
      ),
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
   * データセットを検索し、該当するデータセットの一覧を取得する。
   *
   * @param query 検索条件
   * @param limit 検索上限
   * @param offset 検索オフセット
   * @param user ユーザ情報
   * @return
   *        Success(RangeSlice[DatasetData.DatasetsSummary]) 検索成功時、検索結果
   *        Failure(NullPointerException) 引数がnullの場合
   */
  def search(
    query: SearchDatasetCondition,
    limit: Option[Int],
    offset: Option[Int],
    user: User
  ): Try[RangeSlice[DatasetData.DatasetsSummary]] = {
    Try {
      CheckUtil.checkNull(query, "query")
      CheckUtil.checkNull(limit, "limit")
      CheckUtil.checkNull(offset, "offset")
      CheckUtil.checkNull(user, "user")
      val d = persistence.Dataset.d
      val o = persistence.Ownership.o
      val limit_ = limit.getOrElse(DEFALUT_LIMIT)
      val offset_ = offset.getOrElse(0)
      DB.readOnly { implicit s =>
        val joinedGroups = getJoinedGroups(user) :+ AppConf.guestGroupId
        val datasetIds = findMatchedDatasetIds(query, joinedGroups).distinct
        val count = datasetIds.size

        val records =
          if (count == 0) {
            Seq.empty
          } else {
            val ds = withSQL {
              select(d.result.*)
                .from(persistence.Dataset as d)
                .where.in(d.id, datasetIds.map(x => sqls.uuid(x)))
                .orderBy(d.updatedAt.desc)
                .offset(offset_)
                .limit(limit_)
            }.map(persistence.Dataset(d.resultName)).list.apply()

            toDataset(ds, joinedGroups)
          }
        RangeSlice(RangeSliceSummary(count, limit_, offset_), records)
      }
    }
  }

  def findMatchedDatasetIds(query: SearchDatasetCondition, joinedGroups: Seq[String])(implicit s: DBSession): Seq[String] = {
    query match {
      case SearchDatasetCondition.Query(value, contains) => {
        // ベーシック検索
        findBasicMatchedDatasetIds(value, contains, joinedGroups)
      }
      case SearchDatasetCondition.Container(operator, value) => {
        // アドバンスド検索
        findAdvancedMatchedDatasetIds(operator, value, joinedGroups)
      }
      case _ => {
        // 想定外のため、例外
        throw new RuntimeException
      }
    }
  }

  def createFindMatchedDatasetIdsFromQueryBlank(joinedGroups: Seq[String]): SQLSyntax = {
    // 全表示
    val d = persistence.Dataset.d
    val o = persistence.Ownership.o
    val q = select(d.id)
      .from(persistence.Dataset as d)
      .innerJoin(persistence.Ownership as o)
      .on(sqls.eq(o.datasetId, d.id)
        .and.isNull(o.deletedAt)
        .and.gt(o.accessLevel, GroupAccessLevel.Deny)
        .and.append(sqls"${o.groupId} in ( ${sqls.join(joinedGroups.map(x => sqls.uuid(x)), sqls",")} )"))
      .where.isNull(d.deletedAt)
      .toSQLSyntax
    q
  }

  def createFindMatchedDatasetIdsFromQueryLike(value: String, joinedGroups: Seq[String]): SQLSyntax = {
    // キーワード検索
    val d = persistence.Dataset.d
    val o = persistence.Ownership.o
    val g = persistence.Group.g
    val f = persistence.File.f
    val fh = persistence.FileHistory.fh
    val zf = persistence.ZipedFiles.zf

    val q: SQLSyntax =
      select(d.id)
        .from(persistence.Dataset as d)
        .innerJoin(persistence.Ownership as o)
        .on(sqls.eq(o.datasetId, d.id)
          .and.isNull(o.deletedAt)
          .and.gt(o.accessLevel, GroupAccessLevel.Deny)
          .and.append(sqls"${o.groupId} in ( ${sqls.join(joinedGroups.map(x => sqls.uuid(x)), sqls",")} )"))
        .where.isNull(d.deletedAt)
        .and.append(sqls"(UPPER(${d.name}) like CONCAT('%', UPPER(${value}), '%') or UPPER(${d.description}) like CONCAT('%', UPPER(${value}), '%'))")
        .union(
          select(d.id)
            .from(persistence.Dataset as d)
            .innerJoin(persistence.Ownership as o)
            .on(sqls.eq(o.datasetId, d.id)
              .and.isNull(o.deletedAt)
              .and.gt(o.accessLevel, GroupAccessLevel.Deny)
              .and.append(sqls"${o.groupId} in ( ${sqls.join(joinedGroups.map(x => sqls.uuid(x)), sqls",")} )"))
            .innerJoin(persistence.File as f)
            .on(sqls.eq(f.datasetId, d.id)
              .and.isNull(f.deletedAt)
              .and.append(sqls"UPPER(${f.name}) like CONCAT('%', UPPER(${value}), '%')"))
            .where.isNull(d.deletedAt)
        )
        .union(
          select(d.id)
            .from(persistence.Dataset as d)
            .innerJoin(persistence.Ownership as o)
            .on(sqls.eq(o.datasetId, d.id)
              .and.isNull(o.deletedAt)
              .and.gt(o.accessLevel, GroupAccessLevel.Deny)
              .and.append(sqls"${o.groupId} in ( ${sqls.join(joinedGroups.map(x => sqls.uuid(x)), sqls",")} )"))
            .innerJoin(persistence.File as f)
            .on(sqls.eq(f.datasetId, d.id)
              .and.isNull(f.deletedAt))
            .innerJoin(persistence.FileHistory as fh)
            .on(sqls.eq(fh.fileId, f.id)
              .and.isNull(fh.deletedAt))
            .innerJoin(persistence.ZipedFiles as zf)
            .on(sqls.eq(zf.historyId, fh.id)
              .and.isNull(zf.deletedAt)
              .and.append(sqls"UPPER(${zf.name}) like CONCAT('%', UPPER(${value}), '%')"))
            .where.isNull(d.deletedAt)
        )
        .toSQLSyntax

    q
  }

  def createFindMatchedDatasetIdsFromOwner(value: String, joinedGroups: Seq[String]): SQLSyntax = {
    // オーナー検索
    val d = persistence.Dataset.d
    val o_access = persistence.Ownership.syntax("o1") // 閲覧権限があるかどうかをチェックするため
    val o_group = persistence.Ownership.syntax("o2") // グループとの結合のため
    val g = persistence.Group.g
    val m = persistence.Member.m
    val u = persistence.User.u

    val q: SQLSyntax =
      select(d.id)
        .from(persistence.Dataset as d)
        .innerJoin(persistence.Ownership as o_access)
        .on(sqls.eq(o_access.datasetId, d.id)
          .and.isNull(o_access.deletedAt)
          .and.gt(o_access.accessLevel, GroupAccessLevel.Deny)
          .and.append(sqls"${o_access.groupId} in ( ${sqls.join(joinedGroups.map(x => sqls.uuid(x)), sqls",")} )"))
        .innerJoin(persistence.Ownership as o_group)
        .on(sqls.eq(o_group.datasetId, d.id)
          .and.isNull(o_group.deletedAt)
          .and.eq(o_group.accessLevel, UserAndGroupAccessLevel.OWNER_OR_PROVIDER))
        .innerJoin(persistence.Group as g)
        .on(sqls.eq(g.id, o_group.groupId)
          .and.isNull(g.deletedAt)
          .and.eq(g.groupType, GroupType.Public)
          .and.eq(g.name, value))
        .where.isNull(d.deletedAt)
        .union(
          select(d.id)
            .from(persistence.Dataset as d)
            .innerJoin(persistence.Ownership as o_access)
            .on(sqls.eq(o_access.datasetId, d.id)
              .and.isNull(o_access.deletedAt)
              .and.gt(o_access.accessLevel, GroupAccessLevel.Deny)
              .and.append(sqls"${o_access.groupId} in ( ${sqls.join(joinedGroups.map(x => sqls.uuid(x)), sqls",")} )"))
            .innerJoin(persistence.Ownership as o_group)
            .on(sqls.eq(o_group.datasetId, d.id)
              .and.isNull(o_group.deletedAt)
              .and.eq(o_group.accessLevel, UserAndGroupAccessLevel.OWNER_OR_PROVIDER))
            .innerJoin(persistence.Group as g)
            .on(sqls.eq(g.id, o_group.groupId)
              .and.isNull(g.deletedAt)
              .and.eq(g.groupType, GroupType.Personal))
            .innerJoin(persistence.Member as m)
            .on(sqls.eq(m.groupId, g.id)
              .and.isNull(m.deletedAt))
            .innerJoin(persistence.User as u)
            .on(sqls.eq(u.id, m.userId)
              .and.eq(u.disabled, false)
              .and.eq(u.name, value))
            .where.isNull(d.deletedAt)
        )
        .toSQLSyntax

    q
  }

  def createFindMatchedDatasetIdsFromTag(value: String, joinedGroups: Seq[String]): SQLSyntax = {
    // タグ検索
    val d = persistence.Dataset.d
    val o = persistence.Ownership.o
    val a = persistence.Annotation.a
    val da = persistence.DatasetAnnotation.da

    val q: SQLSyntax =
      select(d.id)
        .from(persistence.Dataset as d)
        .innerJoin(persistence.Ownership as o)
        .on(sqls.eq(o.datasetId, d.id)
          .and.isNull(o.deletedAt)
          .and.gt(o.accessLevel, GroupAccessLevel.Deny)
          .and.append(sqls"${o.groupId} in ( ${sqls.join(joinedGroups.map(x => sqls.uuid(x)), sqls",")} )"))
        .innerJoin(persistence.DatasetAnnotation as da)
        .on(sqls.eq(da.datasetId, d.id)
          .and.isNull(da.deletedAt)
          .and.eq(da.data, "$tag"))
        .innerJoin(persistence.Annotation as a)
        .on(sqls.eq(a.id, da.annotationId)
          .and.isNull(a.deletedAt)
          .and.eq(a.name, value))
        .where.isNull(d.deletedAt)
        .toSQLSyntax

    q
  }

  def createFindMatchedDatasetIdsFromAttribute(key: String, value: String, joinedGroups: Seq[String]): SQLSyntax = {
    // 属性検索
    val d = persistence.Dataset.d
    val o = persistence.Ownership.o
    val a = persistence.Annotation.a
    val da = persistence.DatasetAnnotation.da

    val q: SQLSyntax =
      select(d.id)
        .from(persistence.Dataset as d)
        .innerJoin(persistence.Ownership as o)
        .on(sqls.eq(o.datasetId, d.id)
          .and.isNull(o.deletedAt)
          .and.gt(o.accessLevel, GroupAccessLevel.Deny)
          .and.append(sqls"${o.groupId} in ( ${sqls.join(joinedGroups.map(x => sqls.uuid(x)), sqls",")} )"))
        .innerJoin(persistence.DatasetAnnotation as da)
        .on(sqls.eq(da.datasetId, d.id)
          .and.isNull(da.deletedAt))
        .innerJoin(persistence.Annotation as a)
        .on(sqls.eq(a.id, da.annotationId)
          .and.isNull(a.deletedAt))
        .where(
          sqls.toAndConditionOpt(
            Some(sqls.eq(da.datasetId, d.id)),
            if (key.isEmpty) None else Some(sqls.eq(a.name, key)),
            if (value.isEmpty) None else Some(sqls.eq(da.data, value)),
            Some(sqls.isNull(d.deletedAt))
          )
        )
        .toSQLSyntax

    q
  }

  def createFindMatchedDatasetIdsFromTotalSize(compare: SearchDatasetCondition.Operators.Compare, value: Double, unit: SearchDatasetCondition.SizeUnit, joinedGroups: Seq[String]): SQLSyntax = {
    val d = persistence.Dataset.d
    val o = persistence.Ownership.o

    val size = (value * unit.magnification).toLong
    val q =
      select(d.id)
        .from(persistence.Dataset as d)
        .innerJoin(persistence.Ownership as o)
        .on(sqls.eq(o.datasetId, d.id)
          .and.isNull(o.deletedAt)
          .and.gt(o.accessLevel, GroupAccessLevel.Deny)
          .and.append(sqls"${o.groupId} in ( ${sqls.join(joinedGroups.map(x => sqls.uuid(x)), sqls",")} )"))

    compare match {
      case SearchDatasetCondition.Operators.Compare.GE => {
        q.where.ge(d.filesSize, size)
          .and.isNull(d.deletedAt)
          .toSQLSyntax
      }
      case SearchDatasetCondition.Operators.Compare.LE => {
        q.where.le(d.filesSize, size)
          .and.isNull(d.deletedAt)
          .toSQLSyntax

      }
    }
  }

  def createFindMatchedDatasetIdsFromNumOfFiles(compare: SearchDatasetCondition.Operators.Compare, value: Int, joinedGroups: Seq[String]): SQLSyntax = {
    val d = persistence.Dataset.d
    val o = persistence.Ownership.o

    val q =
      select(d.id)
        .from(persistence.Dataset as d)
        .innerJoin(persistence.Ownership as o)
        .on(sqls.eq(o.datasetId, d.id)
          .and.isNull(o.deletedAt)
          .and.gt(o.accessLevel, GroupAccessLevel.Deny)
          .and.append(sqls"${o.groupId} in ( ${sqls.join(joinedGroups.map(x => sqls.uuid(x)), sqls",")} )"))

    compare match {
      case SearchDatasetCondition.Operators.Compare.GE => {
        q.where.ge(d.filesCount, value)
          .and.isNull(d.deletedAt)
          .toSQLSyntax
      }
      case SearchDatasetCondition.Operators.Compare.LE => {
        q.where.le(d.filesCount, value)
          .and.isNull(d.deletedAt)
          .toSQLSyntax
      }
    }
  }

  def createFindMatchedDatasetIdsFromPublic(value: Boolean, joinedGroups: Seq[String]): SQLSyntax = {
    val d = persistence.Dataset.d
    val o_access = persistence.Ownership.syntax("o1") // 閲覧権限があるかどうかをチェックするため
    val o_public = persistence.Ownership.syntax("o2") // 公開/非公開のチェックのため

    val q =
      select(d.id)
        .from(persistence.Dataset as d)
        .innerJoin(persistence.Ownership as o_access)
        .on(sqls.eq(o_access.datasetId, d.id)
          .and.isNull(o_access.deletedAt)
          .and.gt(o_access.accessLevel, GroupAccessLevel.Deny)
          .and.append(sqls"${o_access.groupId} in ( ${sqls.join(joinedGroups.map(x => sqls.uuid(x)), sqls",")} )"))

    if (value) {
      q.innerJoin(persistence.Ownership as o_public)
        .on(sqls.eq(o_public.datasetId, d.id)
          .and.isNull(o_public.deletedAt)
          .and.gt(o_public.accessLevel, GroupAccessLevel.Deny)
          .and.eq(o_public.groupId, sqls.uuid(AppConf.guestGroupId)))
        .where.isNull(d.deletedAt)
        .toSQLSyntax
    } else {
      q.where(
        sqls.notExists(
          select
          .from(persistence.Ownership as o_public)
          .where.eq(o_public.datasetId, d.id)
          .and.isNull(o_public.deletedAt)
          .and.gt(o_public.accessLevel, GroupAccessLevel.Deny)
          .and.eqUuid(o_public.groupId, AppConf.guestGroupId)
          .toSQLSyntax
        )
      ).and.isNull(d.deletedAt).toSQLSyntax
    }
  }

  def findBasicMatchedDatasetIds(keyword: String, contains: Boolean, joinedGroups: Seq[String])(implicit s: DBSession): Seq[String] = {
    // ベーシック検索
    // ここでは以下の構造のみを想定する
    // Query([キーワード],true)

    val q: SQLSyntax = (keyword, contains) match {
      case ("", _) => {
        // 全表示
        createFindMatchedDatasetIdsFromQueryBlank(joinedGroups)
      }
      case (value, true) => {
        // キーワード検索
        createFindMatchedDatasetIdsFromQueryLike(value, joinedGroups)
      }
      case _ => {
        throw new RuntimeException
      }
    }

    withSQL {
      new SelectSQLBuilder(q)
    }.map(_.string(1)).list().apply()
  }

  def findInternalMatchedDatasetIds(subCondition: SearchDatasetCondition, joinedGroups: Seq[String])(implicit s: DBSession): Seq[String] = {
    subCondition match {
      case SearchDatasetCondition.Query("", _) => {
        // キーワード検索だが、なにも指定されていないため、全表示

        // 全表示
        val findIds = withSQL {
          new SelectSQLBuilder(createFindMatchedDatasetIdsFromQueryBlank(joinedGroups))
        }.map(_.string(1)).list().apply()

        findIds
      }
      case SearchDatasetCondition.Query(value, true) => {
        // キーワード検索（LIKE）
        val findIds = withSQL {
          new SelectSQLBuilder(createFindMatchedDatasetIdsFromQueryLike(value, joinedGroups))
        }.map(_.string(1)).list().apply()

        findIds
      }
      case SearchDatasetCondition.Query(value, false) => {
        // 全て
        val allIds = withSQL {
          new SelectSQLBuilder(createFindMatchedDatasetIdsFromQueryBlank(joinedGroups))
        }.map(_.string(1)).list().apply()

        // キーワードを含む
        val ignoreIds = withSQL {
          new SelectSQLBuilder(createFindMatchedDatasetIdsFromQueryLike(value, joinedGroups))
        }.map(_.string(1)).list().apply()

        // ユーザーが閲覧できるデータセットから、検索条件を含むものを除外する
        val findIds = allIds.filterNot(v => ignoreIds.contains(v))

        findIds
      }
      case SearchDatasetCondition.Owner(value, true) => {
        // オーナー検索
        val findIds = withSQL {
          new SelectSQLBuilder(createFindMatchedDatasetIdsFromOwner(value, joinedGroups))
        }.map(_.string(1)).list().apply()

        findIds
      }
      case SearchDatasetCondition.Owner(value, false) => {
        // 全て
        val allIds = withSQL {
          new SelectSQLBuilder(createFindMatchedDatasetIdsFromQueryBlank(joinedGroups))
        }.map(_.string(1)).list().apply()

        // オーナー検索
        val ignoreIds = withSQL {
          new SelectSQLBuilder(createFindMatchedDatasetIdsFromOwner(value, joinedGroups))
        }.map(_.string(1)).list().apply()

        // ユーザーが閲覧できるデータセットから、検索条件を含むものを除外する
        val findIds = allIds.filterNot(v => ignoreIds.contains(v))

        findIds
      }
      case SearchDatasetCondition.Tag(value) => {
        // タグ検索
        val findIds = withSQL {
          new SelectSQLBuilder(createFindMatchedDatasetIdsFromTag(value, joinedGroups))
        }.map(_.string(1)).list().apply()

        findIds
      }
      case SearchDatasetCondition.Attribute("", "") => {
        // 属性検索だが、なにも指定されていないため、全表示

        // 全表示
        val findIds = withSQL {
          new SelectSQLBuilder(createFindMatchedDatasetIdsFromQueryBlank(joinedGroups))
        }.map(_.string(1)).list().apply()

        findIds
      }
      case SearchDatasetCondition.Attribute(key, value) => {
        // 属性検索

        val findIds = withSQL {
          new SelectSQLBuilder(createFindMatchedDatasetIdsFromAttribute(key, value, joinedGroups))
        }.map(_.string(1)).list().apply()

        findIds
      }
      case SearchDatasetCondition.TotalSize(compare, value, unit) => {
        // データセットについている総ファイルサイズ検索

        val findIds = withSQL {
          new SelectSQLBuilder(createFindMatchedDatasetIdsFromTotalSize(compare, value, unit, joinedGroups))
        }.map(_.string(1)).list().apply()

        findIds
      }
      case SearchDatasetCondition.NumOfFiles(compare, value) => {
        // データセットについている総ファイル数検索

        val findIds = withSQL {
          new SelectSQLBuilder(createFindMatchedDatasetIdsFromNumOfFiles(compare, value, joinedGroups))
        }.map(_.string(1)).list().apply()

        findIds
      }
      case SearchDatasetCondition.Public(value) => {
        // 公開・非公開
        val findIds = withSQL {
          new SelectSQLBuilder(createFindMatchedDatasetIdsFromPublic(value, joinedGroups))
        }.map(_.string(1)).list().apply()

        findIds
      }
      case _ => {
        throw new IllegalArgumentException
      }
    }
  }

  def findAdvancedMatchedDatasetIds(operator: SearchDatasetCondition.Operators.Container, conditions: Seq[SearchDatasetCondition], joinedGroups: Seq[String])(implicit s: DBSession): Seq[String] = {
    // アドバンスト検索
    // ここでは以下の構造のみを想定する（親ORコンテナ１つに対し、子ANDコンテナ複数）
    // 想定外の構造の場合、例外をスロー
    //   Container(OR,List(
    //     Container(AND,List( // List内にはContainerを含まない
    //       Query(json,true),
    //       Query(json,false), ...
    //     )), Container(AND,List(
    //       Query(java,true),
    //       Query(java,false), ...
    //     )), Container(AND,List(
    //       Query(expo,true),
    //       Query(expo,false), ...
    //     )), ...
    //   ))

    if (SearchDatasetCondition.Operators.Container.AND.equals(operator)) {
      // 対象外の構造のため、例外
      throw new IllegalArgumentException
    }

    // 親構造がORのため、子構造のANDでヒットしたIDを保持し、返却するためのSet
    val ids: HashSet[String] = HashSet.empty

    conditions foreach {

      condition =>
        condition match {
          case SearchDatasetCondition.Container(SearchDatasetCondition.Operators.Container.AND, subConditions) => {

            var subIds: Set[String] = null
            var alreadyAddedSubIds = false

            // 以下のすべての条件にマッチするIDのみを返却する
            subConditions foreach {
              subCondition =>
                (alreadyAddedSubIds, (subIds == null || subIds.size == 0)) match {
                  case (false, _) => {
                    // 未検索のため、検索し、すべてを保持する
                    subIds = findInternalMatchedDatasetIds(subCondition, joinedGroups).toSet
                    alreadyAddedSubIds = true
                  }
                  case (true, true) => {
                    // すでに検索済み、かつ、検索済みIDが空のため、検索しない
                  }
                  case (true, false) => {
                    // すでに検索済み、かつ、検索済みIDがあるため、検索し、含まれるもののみを保持する
                    subIds = findInternalMatchedDatasetIds(subCondition, joinedGroups)
                      .toStream.filter(v => subIds.contains(v)).toSet
                  }
                }
            }

            // AND条件でヒットしたデータセットIDを追加
            subIds.foreach(v => ids += v)
          }
          case _ => {
            throw new IllegalArgumentException
          }
        }
    }

    ids.toList
  }

  /**
   * DBの検索結果からデータセット検索結果を作成する。
   *
   * @param ds DBの検索結果
   * @param joinedGroups ユーザが所属しているグループ
   * @return データセット検索結果
   */
  def toDataset(
    ds: Seq[persistence.Dataset],
    joinedGroups: Seq[String]
  )(implicit s: DBSession): Seq[DatasetData.DatasetsSummary] = {
    CheckUtil.checkNull(ds, "ds")
    CheckUtil.checkNull(joinedGroups, "joinedGroups")
    val ids = ds.map(_.id)
    val isGuest = joinedGroups.filter(_ != AppConf.guestGroupId).isEmpty
    val ownerMap = if (isGuest) Map.empty[String, Seq[DatasetData.DatasetOwnership]] else getOwnerMap(ids)
    val accessLevelMap = getAccessLevelMap(ids, joinedGroups)
    val guestAccessLevelMap = getGuestAccessLevelMap(ids)
    val imageIdMap = getImageIdMap(ids)
    val featuredImageIdMap = getFeaturedImageIdMap(ids)
    val attributeMap = getAttributeMap(ids)
    ds.map { d =>
      val imageUrl = imageIdMap.get(d.id).map { x =>
        datasetImageDownloadRoot + d.id + "/" + x
      }.getOrElse("")
      val featuredImageUrl = featuredImageIdMap.get(d.id).map { x =>
        datasetImageDownloadRoot + d.id + "/" + x
      }.getOrElse("")
      DatasetData.DatasetsSummary(
        id = d.id,
        name = d.name,
        description = d.description,
        image = imageUrl,
        featuredImage = featuredImageUrl,
        attributes = attributeMap.getOrElse(d.id, Seq.empty),
        ownerships = ownerMap.getOrElse(d.id, Seq.empty),
        files = d.filesCount,
        dataSize = d.filesSize,
        defaultAccessLevel = guestAccessLevelMap.getOrElse(d.id, DefaultAccessLevel.Deny),
        permission = accessLevelMap.getOrElse(d.id, DefaultAccessLevel.Deny),
        localState = d.localState,
        s3State = d.s3State
      )
    }
  }

  /**
   * データセットを検索し、該当するデータセットの一覧を取得する。
   *
   * @param query 検索文字列
   * @param owners 所有者
   * @param groups 検索するグループ
   * @param attributes 検索する属性
   * @param limit 検索上限
   * @param offset 検索オフセット
   * @param orderby ソート条件を規定する文字列
   * @param user ユーザ情報
   * @return
   *        Success(RangeSlice[DatasetData.DatasetsSummary]) 検索成功時、検索結果
   *        Failure(NullPointerException) 引数がnullの場合
   */
  def search(
    query: Option[String],
    owners: Seq[String],
    groups: Seq[String],
    attributes: Seq[DataSetAttribute],
    limit: Option[Int],
    offset: Option[Int],
    orderby: Option[String],
    user: User
  ): Try[RangeSlice[DatasetData.DatasetsSummary]] = {
    Try {
      CheckUtil.checkNull(query, "query")
      CheckUtil.checkNull(owners, "owners")
      CheckUtil.checkNull(groups, "groups")
      CheckUtil.checkNull(attributes, "attributes")
      CheckUtil.checkNull(limit, "limit")
      CheckUtil.checkNull(offset, "offset")
      CheckUtil.checkNull(orderby, "orderby")
      CheckUtil.checkNull(user, "user")
      val limit_ = limit.getOrElse(DEFALUT_LIMIT)
      val offset_ = offset.getOrElse(0)

      DB.readOnly { implicit s =>
        val joinedGroups = getJoinedGroups(user)

        val ids = for {
          userGroupIds <- getGroupIdsByUserName(owners)
          groupIds <- getGroupIdsByGroupName(groups)
        } yield {
          (userGroupIds, groupIds)
        }
        ids match {
          case None => {
            RangeSlice(RangeSliceSummary(0, limit_, offset_), Seq.empty)
          }
          case Some((userGroupIds, groupIds)) => {
            val count = countDataSets(joinedGroups, query, userGroupIds, groupIds, attributes)
            val records = if (count > 0) {
              findDataSets(joinedGroups, query, userGroupIds, groupIds, attributes, limit_, offset_, orderby, user)
            } else {
              Seq.empty
            }
            RangeSlice(RangeSliceSummary(count, limit_, offset_), records)
          }
        }
      }
    }
  }

  /**
   * ユーザアカウント名から対応するPersonalグループIDを取得する。
   *
   * @param names ユーザアカウント名のリスト
   * @param s DBセッション
   * @return 取得結果
   */
  private def getGroupIdsByUserName(names: Seq[String])(implicit s: DBSession): Option[Seq[String]] = {
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
          .eq(u.disabled, false)
      }.map(rs => rs.string(1)).list.apply()
      if (groups.nonEmpty) {
        Some(groups)
      } else {
        None
      }
    } else {
      Some(Seq.empty)
    }
  }

  /**
   * グループ名からグループIDを取得する。
   *
   * @param names グループ名のリスト
   * @param s DBセッション
   * @return 取得結果
   */
  private def getGroupIdsByGroupName(names: Seq[String])(implicit s: DBSession): Option[Seq[String]] = {
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
      }.map(rs => rs.string(1)).list.apply()
      if (groups.nonEmpty) {
        Some(groups)
      } else {
        None
      }
    } else {
      Some(Seq.empty)
    }
  }

  /**
   * 検索結果のデータセット件数を取得する。
   *
   * @param joindGroups ログインユーザが所属しているグループIDのリスト
   * @param query 検索文字列
   * @param ownerUsers オーナーのユーザIDのリスト
   * @param ownerGroups ProviderのグループIDのリスト
   * @param attributes 属性のリスト
   * @param s DBセッション
   * @return データセット件数
   */
  private def countDataSets(
    joindGroups: Seq[String],
    query: Option[String],
    ownerUsers: Seq[String],
    ownerGroups: Seq[String],
    attributes: Seq[DataSetAttribute]
  )(implicit s: DBSession): Int = {
    withSQL {
      createDatasetSql(
        select.apply[Int](sqls.countDistinct(persistence.Dataset.d.id)),
        joindGroups, query, ownerUsers, ownerGroups, attributes
      )
    }.map(implicit rs => rs.int(1)).single.apply().get
  }

  /**
   * データセットを検索する。
   *
   * @param joindGroups ログインユーザが所属しているグループIDのリスト
   * @param query 検索文字列
   * @param ownerUsers オーナーのユーザIDのリスト
   * @param ownerGroups ProviderのグループIDのリスト
   * @param attributes 属性のリスト
   * @param limit 検索上限
   * @param offset 検索オフセット
   * @param orderby ソート条件を規定する文字列
   * @param user ユーザ情報
   * @param s DBセッション
   * @return 検索結果
   */
  private def findDataSets(
    joindGroups: Seq[String],
    query: Option[String],
    ownerUsers: Seq[String],
    ownerGroups: Seq[String],
    attributes: Seq[DataSetAttribute],
    limit: Int,
    offset: Int,
    orderby: Option[String],
    user: User
  )(implicit s: DBSession): Seq[DatasetData.DatasetsSummary] = {
    val ds = persistence.Dataset.d
    val o = persistence.Ownership.o
    val a = persistence.Annotation.a
    val da = persistence.DatasetAnnotation.syntax("da")
    val xda2 = SubQuery.syntax("xda2", da.resultName, a.resultName)

    val selects = orderby match {
      case Some(ord) if ord == "attribute" => {
        select.apply[Any](
          ds.resultAll,
          sqls.max(o.accessLevel).append(sqls"access_level"),
          xda2(da).data
        )
      }
      case _ => {
        select.apply[Any](ds.resultAll, sqls.max(o.accessLevel).append(sqls"access_level"))
      }
    }

    val datasets = orderby match {
      case Some(ord) if ord == "attribute" => {
        withSQL {
          createDatasetSql(selects, joindGroups, query, ownerUsers, ownerGroups, attributes)
            .groupBy(ds.id, xda2(da).data)
            .orderBy(xda2(da).data)
            .offset(offset)
            .limit(limit)
        }.map(rs => (persistence.Dataset(ds.resultName)(rs), rs.int("access_level"))).list.apply()
      }
      case _ => {
        withSQL {
          createDatasetSql(selects, joindGroups, query, ownerUsers, ownerGroups, attributes)
            .groupBy(ds.id)
            .orderBy(ds.updatedAt).desc
            .offset(offset)
            .limit(limit)
        }.map(rs => (persistence.Dataset(ds.resultName)(rs), rs.int("access_level"))).list.apply()
      }
    }

    val datasetIds = datasets.map(_._1.id)

    val ownerMap = getOwnerMap(datasetIds)
    val guestAccessLevelMap = getGuestAccessLevelMap(datasetIds)
    val imageIdMap = getImageIdMap(datasetIds)
    val featuredImageIdMap = getFeaturedImageIdMap(datasetIds)

    datasets.map { x =>
      val ds = x._1
      val permission = x._2
      val imageUrl = imageIdMap.get(ds.id).map { x =>
        datasetImageDownloadRoot + ds.id + "/" + x
      }.getOrElse("")
      val featuredImageUrl = featuredImageIdMap.get(ds.id).map { x =>
        datasetImageDownloadRoot + ds.id + "/" + x
      }.getOrElse("")
      val accessLevel = guestAccessLevelMap.get(ds.id).getOrElse(DefaultAccessLevel.Deny)
      DatasetData.DatasetsSummary(
        id = ds.id,
        name = ds.name,
        description = ds.description,
        image = imageUrl,
        featuredImage = featuredImageUrl,
        attributes = getAttributes(ds.id), //TODO 非効率
        ownerships = if (user.isGuest) { Seq.empty } else { ownerMap.get(ds.id).getOrElse(Seq.empty) },
        files = ds.filesCount,
        dataSize = ds.filesSize,
        defaultAccessLevel = accessLevel,
        permission = permission,
        localState = ds.localState,
        s3State = ds.s3State
      )
    }
  }

  /**
   * データセットを検索するSQLを作成する。
   *
   * @param selectSql select部のSQL
   * @param joindGroups ログインユーザが所属しているグループIDのリスト
   * @param query 検索文字列
   * @param ownerUsers オーナーのユーザIDのリスト
   * @param ownerGroups ProviderのグループIDのリスト
   * @param attributes 属性のリスト
   * @return 検索SQL
   */
  private def createDatasetSql[A](
    selectSql: SelectSQLBuilder[A],
    joinedGroups: Seq[String],
    query: Option[String],
    ownerUsers: Seq[String],
    ownerGroups: Seq[String],
    attributes: Seq[DataSetAttribute]
  ): ConditionSQLBuilder[A] = {
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
          val ownerAccesses = Seq(
            ownerUsers.map {
              sqls.eqUuid(o.groupId, _).and.eq(o.accessLevel, UserAccessLevel.Owner)
            },
            ownerGroups.map {
              sqls.eqUuid(o.groupId, _).and.eq(o.accessLevel, GroupAccessLevel.Provider)
            }
          ).flatten
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
                    sqls.join(ownerAccesses, sqls"or")
                  )
                }
              )
              .groupBy(o.datasetId)
              .having(sqls.eq(sqls.count(o.datasetId), ownerUsers.length + ownerGroups.length))
              .as(xo)
          ).on(ds.id, xo(o).datasetId)
        } else {
          sql
        }
      }
      .map { sql =>
        if (attributes.nonEmpty) {
          sql.innerJoin(
            select.apply(da.result.datasetId)
              .from(persistence.Annotation as a)
              .innerJoin(persistence.DatasetAnnotation as da).on(da.annotationId, a.id)
              .where
              .isNull(a.deletedAt).and.isNull(da.deletedAt)
              .and
              .withRoundBracket(
                _.append(sqls.join(attributes.map { x =>
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
      }
      .map { sql =>
        if (attributes.nonEmpty) {
          sql.leftJoin(
            select.apply(da.result.datasetId, da.result.data)
              .from(persistence.Annotation as a)
              .innerJoin(persistence.DatasetAnnotation as da).on(da.annotationId, a.id)
              .where
              .isNull(a.deletedAt).and.isNull(da.deletedAt)
              .and
              .withRoundBracket(
                _.append(sqls.join(attributes.map { x =>
                  sqls.eq(a.name, x.name)
                }, sqls"or"))
              )
              .as(xda2)
          ).on(ds.id, xda2(da).datasetId)
        } else {
          sql
        }
      }
      .where
      .inUuid(o.groupId, Seq.concat(joinedGroups, Seq(AppConf.guestGroupId)))
      .and
      .gt(o.accessLevel, GroupAccessLevel.Deny)
      .and
      .isNull(ds.deletedAt)
  }

  /**
   * 指定されたデータセットに対して、指定されたグループに所属しているユーザが持つアクセス権を取得する。
   *
   * @param datasetIds データセットID
   * @param joinedGroups 所属しているグループ
   * @return データセットに対するアクセス権
   */
  private def getAccessLevelMap(
    datasetIds: Seq[String],
    joinedGroups: Seq[String]
  )(implicit s: DBSession): Map[String, Int] = {
    if (datasetIds.isEmpty) {
      return Map.empty
    }
    val o = persistence.Ownership.syntax("o")
    withSQL {
      select(o.result.datasetId, sqls.max(o.accessLevel))
        .from(persistence.Ownership as o)
        .where
        .inUuid(o.datasetId, datasetIds)
        .and
        .inUuid(o.groupId, joinedGroups)
        .and
        .isNull(o.deletedAt)
        .groupBy(o.datasetId)
    }.map { rs =>
      (rs.string(o.resultName.datasetId), rs.int(2))
    }.list.apply().toMap
  }

  /**
   * 指定されたデータセットのゲストアクセス権を取得する。
   *
   * @param datasetIds データセットID
   * @return データセットに対するアクセス権
   */
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
      }.map { rs =>
        (
          rs.string(o.resultName.datasetId),
          rs.int(o.resultName.accessLevel)
        )
      }.list.apply().toMap
    } else {
      Map.empty
    }
  }

  /**
   * データセットのアイコン画像のMapを作成する。
   *
   * @param datasetIds データセットIDのリスト
   * @param s DBセッション
   * @return データセットアイコン画像のMap
   */
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
      }.map { rs =>
        (
          rs.string(di.resultName.datasetId),
          rs.string(di.resultName.imageId)
        )
      }.list.apply().toMap
    } else {
      Map.empty
    }
  }

  /**
   * データセットのFeatured画像のMapを作成する。
   *
   * @param datasetIds データセットIDのリスト
   * @param s DBセッション
   * @return データセットFeatured画像のMap
   */
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
      }.map { rs =>
        (
          rs.string(di.resultName.datasetId),
          rs.string(di.resultName.imageId)
        )
      }.list.apply().toMap
    } else {
      Map.empty
    }
  }

  /**
   * データセットのOwner/Providerのアクセス権のMapを作成する。
   *
   * @param datasetIds データセットIDのリスト
   * @param s DBセッション
   * @return データセットのOwner/Providerのアクセス権のMap
   */
  private def getOwnerMap(
    datasetIds: Seq[String]
  )(implicit s: DBSession): Map[String, Seq[DatasetData.DatasetOwnership]] = {
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
            sqls.eq(u.disabled, false)
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
      }).list.apply().groupBy(_._1).mapValues(_.map(_._2))
    } else {
      Map.empty
    }
  }

  /**
   * 指定されたデータセットの属性を取得する。
   *
   * @param datasetIds データセットID
   * @return データセットが持つ属性
   */
  private def getAttributeMap(
    datasetIds: Seq[String]
  )(implicit s: DBSession): Map[String, Seq[DatasetData.DatasetAttribute]] = {
    if (datasetIds.isEmpty) {
      return Map.empty
    }
    val da = persistence.DatasetAnnotation.da
    val a = persistence.Annotation.a
    withSQL {
      select(da.result.*, a.result.*)
        .from(persistence.DatasetAnnotation as da)
        .innerJoin(persistence.Annotation as a)
        .on(sqls.eq(a.id, da.annotationId).and.isNull(a.deletedAt))
        .where
        .inUuid(da.datasetId, datasetIds)
        .and
        .isNull(da.deletedAt)
    }.map { rs =>
      val datasetAnnotaion = persistence.DatasetAnnotation(da.resultName)(rs)
      val annotation = persistence.Annotation(a.resultName)(rs)
      val attribute = DatasetData.DatasetAttribute(
        name = annotation.name,
        value = datasetAnnotaion.data
      )
      (datasetAnnotaion.datasetId, attribute)
    }.list.apply().groupBy(_._1).mapValues(_.map(_._2))
  }

  /**
   * 指定したデータセットの存在をチェックします。
   * @param datasetId データセットID
   * @param session DBセッション
   * @return 取得したデータセット情報
   * @throws NotFoundException データセットが存在しなかった場合
   */
  private def checkDatasetExisitence(datasetId: String)(implicit session: DBSession): persistence.Dataset = {
    getDataset(datasetId).getOrElse {
      // データセットが存在しない場合例外
      throw new NotFoundException
    }
  }

  /**
   * ファイルの存在を確認した後、指定されたデータセットを取得します。
   *
   * @param datasetId データセットID
   * @param fileId ファイルID
   * @param user ユーザ情報
   * @return データセット
   * @throws NotFoundException データセットまたはファイルが存在しない場合
   */
  private def checkDatasetWithFile(datasetId: String, fileId: String)(implicit s: DBSession): persistence.Dataset = {
    val dataset = checkDatasetExisitence(datasetId)
    if (!existsFile(datasetId, fileId)) {
      throw new NotFoundException
    }
    dataset
  }

  /**
   * 指定されたデータセットに対する管理権限があるかをチェックします。
   *
   * @param datasetId データセットID
   * @param user ユーザ情報
   * @throws AccessDeniedException ユーザに管理権限がない場合
   */
  private def checkOwnerAccess(datasetId: String, user: User)(implicit s: DBSession): Unit = {
    if (!isOwner(user.id, datasetId)) {
      throw new AccessDeniedException(resource.getString(ResourceNames.ONLY_ALLOW_DATASET_OWNER), Some(user))
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
      throw new AccessDeniedException(resource.getString(ResourceNames.NO_ACCESS_PERMISSION), Some(user))
    }
    permission
  }

  /**
   * 指定したデータセットの詳細情報を取得します。
   *
   * @param id データセットID
   * @param user ユーザ情報
   * @return
   *        Success(DatasetData.Dataset) 取得成功時、データセット情報
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセットが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに参照権限がない場合
   */
  def get(id: String, user: User): Try[DatasetData.Dataset] = {
    Try {
      CheckUtil.checkNull(id, "id")
      CheckUtil.checkNull(user, "user")
      DB.readOnly { implicit s =>
        val dataset = checkDatasetExisitence(id)
        val permission = checkReadPermission(id, user)
        val guestAccessLevel = getGuestAccessLevel(id)
        val owners = getAllOwnerships(id, user)
        val attributes = getAttributes(id)
        val images = getImages(id)
        val primaryImage = getPrimaryImageId(id).getOrElse(AppConf.defaultDatasetImageId)
        val featuredImage = getFeaturedImageId(id).getOrElse(AppConf.defaultFeaturedImageIds(0))
        val count = getAccessCount(id)
        val dsApp = getApp(id)
        val daAppUrl = getAppUrl(id, user)
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
          ownerships = if (user.isGuest) { Seq.empty } else { owners },
          defaultAccessLevel = guestAccessLevel,
          permission = permission,
          accessCount = count,
          localState = dataset.localState,
          s3State = dataset.s3State,
          fileLimit = AppConf.fileLimit,
          app = dsApp,
          appUrl = daAppUrl.getOrElse("")
        )
      }
    }
  }

  /**
   * 指定したデータセットにファイルを追加します。
   *
   * @param id データセットID
   * @param files ファイルリスト
   * @param user ユーザ情報
   * @return
   *        Success(DatasetData.DatasetAddFiles) 追加成功時、追加したファイルデータオブジェクト
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセットが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに編集権権がない場合
   */
  def addFiles(id: String, files: Seq[FileItem], user: User): Try[DatasetData.DatasetAddFiles] = {
    Try {
      CheckUtil.checkNull(id, "id")
      CheckUtil.checkNull(files, "files")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        val dataset = checkDatasetExisitence(id)
        checkOwnerAccess(id, user)
        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        val f = files.map { f =>
          // 拡張子を含み、大文字小文字を区別しない
          val isZip = f.getName.toLowerCase.endsWith(".zip")
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
            localState = dataset.localState match {
              case SaveStatus.SAVED => SaveStatus.SAVED
              case SaveStatus.SYNCHRONIZING => SaveStatus.SAVED
              case _ => SaveStatus.DELETING
            },
            s3State = dataset.s3State match {
              case SaveStatus.NOT_SAVED => SaveStatus.NOT_SAVED
              case SaveStatus.DELETING => SaveStatus.DELETING
              case _ => SaveStatus.SYNCHRONIZING
            }
          )
          val realSize = if (isZip) {
            createZipedFiles(path, historyId, timestamp, myself).getOrElse {
              // 新規採番されたファイルヒストリIDに紐づくエラーIDを、新規登録する
              persistence.FileHistoryError.create(
                id = UUID.randomUUID().toString,
                historyId = historyId,
                createdBy = myself.id,
                createdAt = timestamp,
                updatedBy = myself.id,
                updatedAt = timestamp
              )

              // 展開できないZIPファイルのため、サイズはそのままとする
              f.size
            }
          } else {
            f.size
          }
          val history = persistence.FileHistory.create(
            id = historyId,
            fileId = file.id,
            fileType = 0,
            fileMime = "application/octet-stream",
            filePath = "/" + id + "/" + file.id + "/" + historyId,
            fileSize = f.size,
            isZip = isZip,
            realSize = realSize,
            createdBy = myself.id,
            createdAt = timestamp,
            updatedBy = myself.id,
            updatedAt = timestamp
          )

          (file, history)
        }

        if (dataset.s3State == SaveStatus.SAVED || dataset.s3State == SaveStatus.SYNCHRONIZING) {
          createTask(id, MoveToStatus.S3, myself.id, timestamp, dataset.localState == SaveStatus.SAVED)
        }
        // datasetsのfiles_size, files_countの更新
        updateDatasetFileStatus(id, myself.id, timestamp)

        DatasetData.DatasetAddFiles(
          files = f.map { x =>
            DatasetData.DatasetFile(
              id = x._1.id,
              name = x._1.name,
              description = x._1.description,
              size = Some(x._2.fileSize),
              url = Some(AppConf.fileDownloadRoot + id + "/" + x._1.id),
              createdBy = Some(user),
              createdAt = timestamp.toString(),
              updatedBy = Some(user),
              updatedAt = timestamp.toString(),
              isZip = x._2.isZip,
              zipedFiles = Seq.empty,
              zipCount = if (x._2.isZip) {
                getZippedFileAmounts(Seq(x._2.id)).headOption.map(x => x._2).getOrElse(0)
              } else {
                0
              }
            )
          }
        )
      }
    }
  }

  /**
   * 指定したファイルを更新します。
   *
   * @param datasetId データセットID
   * @param fileId ファイルID
   * @param file 更新するファイル
   * @param user ユーザ情報
   * @return
   *        Success(DatasetData.DatasetFile) 更新成功時、更新したファイルデータオブジェクト
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセット、ファイルが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに編集権限がない場合
   */
  def updateFile(datasetId: String, fileId: String, file: FileItem, user: User): Try[DatasetData.DatasetFile] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(fileId, "fileId")
      CheckUtil.checkNull(file, "file")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        val dataset = checkDatasetWithFile(datasetId, fileId)
        checkOwnerAccess(datasetId, user)

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        val historyId = UUID.randomUUID.toString

        updateFileNameAndSize(
          fileId = fileId,
          historyId = historyId,
          file = file,
          userId = myself.id,
          timestamp = timestamp,
          s3State = dataset.s3State match {
            case SaveStatus.NOT_SAVED => SaveStatus.NOT_SAVED
            case SaveStatus.DELETING => SaveStatus.DELETING
            case _ => SaveStatus.SYNCHRONIZING
          },
          localState = dataset.localState match {
            case SaveStatus.SAVED => SaveStatus.SAVED
            case SaveStatus.SYNCHRONIZING => SaveStatus.SAVED
            case _ => SaveStatus.DELETING

          }
        )

        // 拡張子を含み、大文字小文字を区別しない
        val isZip = file.getName.toLowerCase.endsWith(".zip")
        FileManager.uploadToLocal(datasetId, fileId, historyId, file)
        val path = Paths.get(AppConf.fileDir, datasetId, fileId, historyId)

        val realSize = if (isZip) {
          createZipedFiles(path, historyId, timestamp, myself).getOrElse {

            // 直前のファイルヒストリIDに紐づくエラーIDを取得する
            val prevErrorId = withSQL {
              val fh = persistence.FileHistory.fh
              val fhe = persistence.FileHistoryError.fhe

              select(fhe.id)
                .from(persistence.FileHistory as fh)
                .innerJoin(persistence.FileHistoryError as fhe)
                .on(sqls.eq(fhe.historyId, fh.id)
                  .and.isNull(fhe.deletedAt))
                .where.eqUuid(fh.fileId, fileId)
                .and.isNull(fh.deletedAt)
                .orderBy(fh.updatedAt.desc, fhe.updatedAt.desc)
                .limit(1)
            }.map(_.string(1)).single().apply()

            if (prevErrorId.isDefined) {
              // 直前のファイルヒストリIDに紐づくエラーIDがあるため、論理削除する
              withSQL {
                val fhe = persistence.FileHistoryError.column

                update(persistence.FileHistoryError)
                  .set(
                    fhe.deletedBy -> sqls.uuid(myself.id),
                    fhe.deletedAt -> timestamp
                  )
                  .where
                  .eq(fhe.id, sqls.uuid(prevErrorId.get))
              }.update.apply()
            }

            // 新規採番されたファイルヒストリIDに紐づくエラーIDを、新規登録する
            persistence.FileHistoryError.create(
              id = UUID.randomUUID().toString,
              historyId = historyId,
              createdBy = myself.id,
              createdAt = timestamp,
              updatedBy = myself.id,
              updatedAt = timestamp
            )

            // 展開できないZIPファイルのため、サイズはそのままとする
            file.size
          }
        } else {
          file.size
        }
        val history = persistence.FileHistory.create(
          id = historyId,
          fileId = fileId,
          fileType = 0,
          fileMime = "application/octet-stream",
          filePath = "/" + datasetId + "/" + fileId + "/" + historyId,
          fileSize = file.size,
          isZip = isZip,
          realSize = realSize,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp
        )
        FileManager.uploadToLocal(datasetId, fileId, history.id, file)
        if (dataset.s3State == SaveStatus.SAVED || dataset.s3State == SaveStatus.SYNCHRONIZING) {
          createTask(
            datasetId,
            MoveToStatus.S3,
            myself.id,
            timestamp,
            dataset.localState == SaveStatus.SAVED
          )

          // S3に上がっている場合は、アップロードが完了するまで、ローカルからダウンロードしなければならない
          withSQL {
            val d = persistence.Dataset.column
            update(persistence.Dataset)
              .set(
                d.localState -> SaveStatus.DELETING,
                d.s3State -> SaveStatus.SYNCHRONIZING
              )
              .where
              .eq(d.id, sqls.uuid(datasetId))
          }.update.apply()
        }

        // datasetsのfiles_size, files_countの更新
        updateDatasetFileStatus(datasetId, myself.id, timestamp)

        getFiles(datasetId, Seq(fileId), UserAndGroupAccessLevel.ALLOW_DOWNLOAD).head
      }
    }
  }

  /**
   * ファイルの名前、サイズ、保存先を変更する。
   *
   * @param fileId ファイルID
   * @param historyId ファイル履歴ID
   * @param file 更新に使用するファイル
   * @param userId 更新者のユーザID
   * @param timestamp タイムスタンプ
   * @param s3State S3保存状態
   * @param localState ローカル保存状態
   * @param s DBセッション
   * @return 更新件数
   */
  private def updateFileNameAndSize(
    fileId: String,
    historyId: String,
    file: FileItem,
    userId: String,
    timestamp: DateTime,
    s3State: Int,
    localState: Int
  )(implicit s: DBSession): Int = {
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
    }.update.apply()
  }

  /**
   * 指定したファイルのメタデータを更新します。
   *
   * @param datasetId データセットID
   * @param fileId ファイルID
   * @param filename ファイル名
   * @param description 説明
   * @param user ログインユーザ情報
   * @retur
   *        Success(DatasetData.DatasetFile) 更新成功時、更新したファイルデータオブジェクト
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセット、ファイルが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに編集権限がない場合
   */
  def updateFileMetadata(
    datasetId: String,
    fileId: String,
    filename: String,
    description: String,
    user: User
  ): Try[DatasetData.DatasetFile] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(fileId, "fileId")
      CheckUtil.checkNull(filename, "filename")
      CheckUtil.checkNull(description, "description")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        checkDatasetWithFile(datasetId, fileId)
        checkOwnerAccess(datasetId, user)

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()

        updateFileNameAndDescription(fileId, datasetId, filename, description, myself.id, timestamp)

        getFiles(datasetId, Seq(fileId), UserAndGroupAccessLevel.ALLOW_DOWNLOAD).head
      }
    }
  }

  /**
   * ファイルの名前、説明を変更する。
   *
   * @param fileId ファイルID
   * @param datasetId データセットID
   * @param fileName ファイル名
   * @param description 説明
   * @param userId 更新者のユーザID
   * @param timestamp タイムスタンプ
   * @param s DBセッション
   * @return 更新件数
   */
  private def updateFileNameAndDescription(
    fileId: String,
    datasetId: String,
    fileName: String,
    description: String,
    userId: String,
    timestamp: DateTime
  )(implicit s: DBSession): Int = {
    withSQL {
      val f = persistence.File.column
      update(persistence.File)
        .set(
          f.name -> fileName,
          f.description -> description,
          f.updatedBy -> sqls.uuid(userId),
          f.updatedAt -> timestamp
        )
        .where
        .eq(f.id, sqls.uuid(fileId))
        .and
        .eq(f.datasetId, sqls.uuid(datasetId))
    }.update.apply()
  }

  /**
   * 指定したファイルを削除します。
   *
   * @param datasetId データセットID
   * @param fileId ファイルID
   * @param user ログインユーザ情報
   * @return
   *        Success(Unit) 削除成功時
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセット、ファイルが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに編集権限がない場合
   */
  def deleteDatasetFile(datasetId: String, fileId: String, user: User): Try[Unit] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(fileId, "fileId")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        checkDatasetWithFile(datasetId, fileId)
        checkOwnerAccess(datasetId, user)

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()

        deleteFile(datasetId, fileId, myself.id, timestamp)

        // datasetsのfiles_size, files_countの更新
        updateDatasetFileStatus(datasetId, myself.id, timestamp)
      }
    }
  }

  /**
   * ファイルを論理削除する。
   *
   * @param datasetId データセットID
   * @param fileId ファイルID
   * @param userId 更新者のユーザID
   * @param timestamp タイムスタンプ
   * @param s DBセッション
   */
  private def deleteFile(
    datasetId: String,
    fileId: String,
    userId: String,
    timestamp: DateTime
  )(implicit s: DBSession): Unit = {
    withSQL {
      val f = persistence.File.column
      update(persistence.File)
        .set(
          f.deletedBy -> sqls.uuid(userId),
          f.deletedAt -> timestamp,
          f.updatedBy -> sqls.uuid(userId),
          f.updatedAt -> timestamp
        )
        .where
        .eq(f.id, sqls.uuid(fileId))
        .and
        .eq(f.datasetId, sqls.uuid(datasetId))
        .and
        .isNull(f.deletedAt)
    }.update.apply()
  }

  /**
   * データセットの保存先を変更する
   *
   * @param id データセットID
   * @param saveLocal ローカルに保存するか否か
   * @param saveS3 S3に保存するか否か
   * @param user ユーザオブジェクト
   * @return
   *        Success(DatasetTask) 変更成功時、保存先変更タスク情報
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセット、ファイルが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに編集権限がない場合
   */
  def modifyDatasetStorage(id: String, saveLocal: Boolean, saveS3: Boolean, user: User): Try[DatasetTask] = {
    Try {
      CheckUtil.checkNull(id, "id")
      CheckUtil.checkNull(saveLocal, "saveLocal")
      CheckUtil.checkNull(saveS3, "saveS3")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        val dataset = checkDatasetExisitence(id)
        checkOwnerAccess(id, user)

        val taskId = (saveLocal, saveS3, dataset.localState, dataset.s3State) match {
          case (true, _, SaveStatus.NOT_SAVED, SaveStatus.SAVED)
            | (true, _, SaveStatus.NOT_SAVED, SaveStatus.SYNCHRONIZING) => {
            // S3 to local
            updateDatasetStorage(
              dataset,
              myself.id,
              timestamp,
              SaveStatus.SYNCHRONIZING,
              if (saveS3) { SaveStatus.SAVED } else { SaveStatus.DELETING }
            )
            createTask(id, MoveToStatus.LOCAL, myself.id, timestamp, saveS3)
          }
          case (_, true, SaveStatus.SAVED, SaveStatus.NOT_SAVED)
            | (_, true, SaveStatus.SYNCHRONIZING, SaveStatus.NOT_SAVED) => {
            // local to S3
            updateDatasetStorage(
              dataset,
              myself.id,
              timestamp,
              if (saveLocal) { SaveStatus.SAVED } else { SaveStatus.DELETING },
              SaveStatus.SYNCHRONIZING
            )
            createTask(id, MoveToStatus.S3, myself.id, timestamp, saveLocal)
          }
          case _ => {
            def savedOrSynchronizing(state: Int): Boolean = {
              state match {
                case SaveStatus.SAVED => true
                case SaveStatus.SYNCHRONIZING => true
                case _ => false
              }
            }
            if (saveLocal != saveS3
              && savedOrSynchronizing(dataset.localState)
              && savedOrSynchronizing(dataset.s3State)) {
              // local, S3のいずれか削除
              updateDatasetStorage(
                dataset,
                myself.id,
                timestamp,
                if (saveLocal) { SaveStatus.SAVED } else { SaveStatus.DELETING },
                if (saveS3) { SaveStatus.SAVED } else { SaveStatus.DELETING }
              )
              val moveToStatus = if (saveS3) { MoveToStatus.S3 } else { MoveToStatus.LOCAL }
              createTask(id, moveToStatus, myself.id, timestamp, false)
            } else {
              // no taskId
              "0"
            }
          }
        }
        DatasetTask(taskId)
      }
    }
  }

  /**
   * データセットの保存状態を更新する。
   *
   * @param ds データセットオブジェクト
   * @param userId 更新者のユーザID
   * @param timestamp タイムスタンプ
   * @param localState ローカル保存状態
   * @param s3State S3保存状態
   * @return 更新後のデータセットオブジェクト
   */
  private def updateDatasetStorage(
    ds: Dataset,
    userId: String,
    timestamp: DateTime,
    localState: Int,
    s3State: Int
  )(implicit s: DBSession): persistence.Dataset = {
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
   * @return
   *        Success(DatasetData.DatasetMetaData) 更新成功時、更新後のデータセットのメタデータ
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセット、ファイルが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに編集権限がない場合
   *        Failure(BadRequestException) ライセンスIDが不正な場合
   */
  def modifyDatasetMeta(
    id: String,
    name: String,
    description: Option[String],
    license: String,
    attributes: Seq[DataSetAttribute],
    user: User
  ): Try[DatasetData.DatasetMetaData] = {
    Try {
      CheckUtil.checkNull(id, "id")
      CheckUtil.checkNull(name, "name")
      CheckUtil.checkNull(description, "description")
      CheckUtil.checkNull(license, "license")
      CheckUtil.checkNull(attributes, "attributes")
      CheckUtil.checkNull(user, "user")
      val checkedDescription = description.getOrElse("")
      val trimmedAttributes = attributes.map(x => x.name -> StringUtil.trimAllSpaces(x.value))

      DB.localTx { implicit s =>
        checkDatasetExisitence(id)
        checkOwnerAccess(id, user)
        if (persistence.License.find(license).isEmpty) {
          val message = resource.getString(ResourceNames.INVALID_LICENSEID).format(license)
          throw new BadRequestException(message)
        }
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
        oldAnnotations.foreach { x =>
          if (!trimmedAttributes.map(_._1.toLowerCase).contains(x._1)) {
            val datasetAnnotations = getDatasetAnnotations(x._2)
            if (datasetAnnotations.size == 0) {
              deleteAnnotation(x._2)
            }
          }
        }
      }
      DatasetData.DatasetMetaData(
        name = name,
        description = checkedDescription,
        license = license,
        attributes = trimmedAttributes.map {
          case (name, value) => DatasetData.DatasetAttribute(name, value)
        }
      )
    }
  }

  /**
   * 未削除のDatasetAnnotationのID一覧を取得する。
   *
   * @param id アノテーションID
   * @param s DBセッション
   * @return 取得結果
   */
  private def getDatasetAnnotations(id: String)(implicit s: DBSession): Seq[String] = {
    val da = persistence.DatasetAnnotation.da
    withSQL {
      select(da.result.id)
        .from(persistence.DatasetAnnotation as da)
        .where
        .eq(da.annotationId, sqls.uuid(id))
        .and
        .isNull(da.deletedAt)
    }.map(rs => rs.string(da.resultName.id)).list.apply()
  }

  /**
   * 未削除のAnnotationのID・名前一覧を取得する。
   *
   * @param s DBセッション
   * @return 取得結果
   */
  private def getAvailableAnnotations(implicit s: DBSession): Seq[(String, String)] = {
    val a = persistence.Annotation.a
    withSQL {
      select(a.result.*)
        .from(persistence.Annotation as a)
        .where
        .isNull(a.deletedAt)
    }.map { rs =>
      (
        rs.string(a.resultName.name).toLowerCase,
        rs.string(a.resultName.id)
      )
    }.list.apply()
  }

  /**
   * データセットに関連づいた未削除のAnnotationのID・名前一覧を取得する。
   *
   * @param id データセットID
   * @param s DBセッション
   * @return 取得結果
   */
  private def getAnnotationsRelatedByDataset(id: String)(implicit s: DBSession): Seq[(String, String)] = {
    val a = persistence.Annotation.a
    val da = persistence.DatasetAnnotation.da
    withSQL {
      select(a.result.*)
        .from(persistence.Annotation as a)
        .innerJoin(persistence.DatasetAnnotation as da)
        .on(sqls.eq(da.annotationId, a.id).and.isNull(da.deletedAt))
        .where
        .eq(da.datasetId, sqls.uuid(id))
        .and
        .isNull(a.deletedAt)
    }.map(rs => (rs.string(a.resultName.name).toLowerCase, rs.string(a.resultName.id))).list.apply()
  }

  /**
   * Annotationを物理削除する。
   *
   * @param id アノテーションID
   * @param s DBセッション
   * @return 削除件数
   */
  private def deleteAnnotation(id: String)(implicit s: DBSession): Int = {
    withSQL {
      val a = persistence.Annotation.a
      delete.from(persistence.Annotation as a)
        .where
        .eq(a.id, sqls.uuid(id))
    }.update.apply()
  }

  /**
   * DatasetAnnotationを物理削除する。
   *
   * @param id データセットID
   * @param s DBセッション
   * @return 削除件数
   */
  private def deleteDatasetAnnotation(id: String)(implicit s: DBSession): Int = {
    val da = persistence.DatasetAnnotation.da
    withSQL {
      delete.from(persistence.DatasetAnnotation as da)
        .where
        .eq(da.datasetId, sqls.uuid(id))
    }.update.apply()
  }

  /**
   * データセットの詳細を更新する。
   *
   * @param id データセットID
   * @param name データセット名
   * @param description 説明
   * @param licenseId ライセンスID
   * @param userId 更新者のユーザID
   * @param timestamp タイムスタンプ
   * @param s DBセッション
   * @return 更新件数
   */
  private def updateDatasetDetail(
    id: String,
    name: String,
    description: String,
    licenseId: String,
    userId: String,
    timestamp: DateTime
  )(implicit s: DBSession): Int = {
    withSQL {
      val d = persistence.Dataset.column
      update(persistence.Dataset)
        .set(
          d.name -> name,
          d.description -> description,
          d.licenseId -> sqls.uuid(licenseId),
          d.updatedBy -> sqls.uuid(userId),
          d.updatedAt -> timestamp
        )
        .where
        .eq(d.id, sqls.uuid(id))
    }.update.apply()
  }

  /**
   * 指定したデータセットに画像を追加します。
   *
   * @param datasetId データセットID
   * @param images 追加する画像の一覧
   * @param user ユーザ情報
   * @return
   *        Success(DatasetData.DatasetAddImages) 追加成功時、追加した画像オブジェクト
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセットが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに編集権限がない場合
   */
  def addImages(datasetId: String, images: Seq[FileItem], user: User): Try[DatasetData.DatasetAddImages] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(images, "images")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        checkDatasetExisitence(datasetId)
        checkOwnerAccess(datasetId, user)
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
            isPrimary = isFirst && primaryImage.isEmpty,
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

        DatasetData.DatasetAddImages(
          images = addedImages.map {
            case (image, isPrimary) =>
              DatasetData.DatasetGetImage(
                id = image.id,
                name = image.name,
                url = datasetImageDownloadRoot + datasetId + "/" + image.id,
                isPrimary = isPrimary
              )
          },
          primaryImage = getPrimaryImageId(datasetId).getOrElse("")
        )
      }
    }
  }

  /**
   * 指定したデータセットのプライマリ画像を変更します。
   *
   * @param datasetId データセットID
   * @param imageId 画像ID
   * @param user ログインユーザ情報
   * @return
   *        Success(DatasetData.ChangeDatasetImage) 変更成功時、変更後の画像ID
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセット、または画像が見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに編集権限がない場合
   */
  def changePrimaryImage(datasetId: String, imageId: String, user: User): Try[DatasetData.ChangeDatasetImage] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(imageId, "imageId")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        checkDatasetExisitence(datasetId)
        if (!existsImage(datasetId, imageId)) {
          throw new NotFoundException
        }
        checkOwnerAccess(datasetId, user)

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        // 対象のイメージをPrimaryに変更
        turnImageToPrimary(datasetId, imageId, myself, timestamp)
        // 対象以外のイメージをPrimary以外に変更
        turnOffPrimaryOtherImage(datasetId, imageId, myself, timestamp)

        DatasetData.ChangeDatasetImage(imageId)
      }
    }
  }

  /**
   * データセットのアイコン画像を解除する。
   *
   * @param datasetId データセットID
   * @param imageId 画像ID
   * @param myself ログインユーザ情報
   * @param timestamp タイムスタンプ
   * @param s DBセッション
   * @return 更新件数
   */
  private def turnOffPrimaryOtherImage(
    datasetId: String,
    imageId: String,
    myself: persistence.User,
    timestamp: DateTime
  )(implicit s: DBSession): Int = {
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
    }.update.apply()
  }

  /**
   * データセットのアイコン画像を指定する。
   *
   * @param datasetId データセットID
   * @param imageId 画像ID
   * @param myself ログインユーザ情報
   * @param timestamp タイムスタンプ
   * @param s DBセッション
   * @return 更新件数
   */
  private def turnImageToPrimary(
    datasetId: String,
    imageId: String,
    myself: persistence.User,
    timestamp: DateTime
  )(implicit s: DBSession): Int = {
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
    }.update.apply()
  }

  /**
   * 指定したデータセットの画像を削除します。
   *
   * @param datasetId データセットID
   * @param imageId 画像ID
   * @param user ログインユーザ情報
   * @return
   *        Success(DatasetData.DatasetDeleteImage) 削除成功時、削除した画像情報
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセット、画像が見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに編集権限がない場合
   *        Failure(BadRequestException) デフォルト画像を削除する場合
   */
  def deleteImage(datasetId: String, imageId: String, user: User): Try[DatasetData.DatasetDeleteImage] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(imageId, "imageId")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        checkDatasetExisitence(datasetId)
        if (!existsImage(datasetId, imageId)) {
          throw new NotFoundException
        }
        checkOwnerAccess(datasetId, user)
        val cantDeleteImages = Seq(AppConf.defaultDatasetImageId) ++ AppConf.defaultFeaturedImageIds
        if (cantDeleteImages.contains(imageId)) {
          throw new BadRequestException(resource.getString(ResourceNames.CANT_DELETE_DEFAULTIMAGE))
        }
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
        val featuredImageId = getFeaturedImageId(datasetId).getOrElse {
          val featuredImage = findNextImage(datasetId)
          featuredImage match {
            case Some(x) =>
              turnImageToFeaturedById(x._1, myself, timestamp)
              x._2
            case None => ""
          }
        }
        DatasetData.DatasetDeleteImage(
          primaryImage = primaryImageId,
          featuredImage = featuredImageId
        )
      }
    }
  }

  /**
   * データセットのアイコン画像を指定する。
   *
   * @param id DatasetImageID
   * @param myself ログインユーザ情報
   * @param timestamp タイムスタンプ
   * @param s DBセッション
   * @return 更新件数
   */
  private def turnImageToPrimaryById(
    id: String,
    myself: persistence.User,
    timestamp: DateTime
  )(implicit s: DBSession): Int = {
    val di = persistence.DatasetImage.column
    withSQL {
      update(persistence.DatasetImage)
        .set(di.isPrimary -> true, di.updatedBy -> sqls.uuid(myself.id), di.updatedAt -> timestamp)
        .where
        .eq(di.id, sqls.uuid(id))
    }.update.apply()
  }

  /**
   * データセットのFeatured画像を指定する。
   *
   * @param id DatasetImageID
   * @param myself ログインユーザ情報
   * @param timestamp タイムスタンプ
   * @param s DBセッション
   * @return 更新件数
   */
  private def turnImageToFeaturedById(
    id: String,
    myself: persistence.User,
    timestamp: DateTime
  )(implicit s: DBSession): Int = {
    val di = persistence.DatasetImage.column
    withSQL {
      update(persistence.DatasetImage)
        .set(di.isFeatured -> true, di.updatedBy -> sqls.uuid(myself.id), di.updatedAt -> timestamp)
        .where
        .eq(di.id, sqls.uuid(id))
    }.update.apply()
  }

  /**
   * データセットに関連づいているDatasetImage、ImageのIDを一件取得する。
   *
   * @param datasetId データセットID
   * @param s DBセッション
   * @return 取得結果
   */
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
    }.map(rs => (rs.string(di.resultName.id), rs.string(i.resultName.id))).single.apply()
  }

  /**
   * DatasetImageを論理削除する。
   *
   * @param datasetId データセットID
   * @param imageId 画像ID
   * @param myself ログインユーザ情報
   * @param timestamp タイムスタンプ
   * @param s DBセッション
   * @return 更新件数
   */
  private def deleteDatasetImage(
    datasetId: String,
    imageId: String,
    myself: persistence.User,
    timestamp: DateTime
  )(implicit s: DBSession): Int = {
    withSQL {
      val di = persistence.DatasetImage.column
      update(persistence.DatasetImage)
        .set(
          di.deletedBy -> sqls.uuid(myself.id),
          di.deletedAt -> timestamp,
          di.isPrimary -> false,
          di.updatedBy -> sqls.uuid(myself.id),
          di.updatedAt -> timestamp
        )
        .where
        .eq(di.datasetId, sqls.uuid(datasetId))
        .and
        .eq(di.imageId, sqls.uuid(imageId))
        .and
        .isNull(di.deletedAt)
    }.update.apply()
  }

  /**
   * 指定したデータセットのアクセスコントロールを設定します。
   *
   * @param datasetId データセットID
   * @param acl アクセスコントロール変更オブジェクトのリスト
   * @param user ユーザオブジェクト
   * @return
   *        Success(Seq[DatasetData.DatasetOwnership]) 設定成功時、変更されたアクセスコントロールのリスト
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセットが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに編集権限がない場合
   *        Failure(BadRequestException) 更新結果でオーナーがいなくなる場合
   *        Failure(BadRequestException) 無効化されたユーザが指定された場合
   *        Failure(BadRequestException) 存在しないグループが指定された場合
   */
  def setAccessControl(
    datasetId: String,
    acl: Seq[DataSetAccessControlItem],
    user: User
  ): Try[Seq[DatasetData.DatasetOwnership]] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(acl, "acl")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        checkDatasetExisitence(datasetId)
        checkOwnerAccess(datasetId, user)

        val ownerChanges = acl.filter { x =>
          x.ownerType == OwnerType.User && x.accessLevel == UserAndGroupAccessLevel.OWNER_OR_PROVIDER
        }.map(_.id).toSet
        val notOwnerChanges = acl.filter { x =>
          x.ownerType == OwnerType.User && x.accessLevel != UserAndGroupAccessLevel.OWNER_OR_PROVIDER
        }.map(_.id).toSet
        // 元々設定されているオーナーのうち、今回オーナー以外に変更されない件数
        val ownerCountRemains = getOwners(datasetId).count(x => !notOwnerChanges.contains(x.id))
        // 更新後のオーナーの数は元々設定されているオーナーのうち、今回オーナー以外に変更されない件数と、今回オーナーに変更された件数を足したもの
        val ownerCountAfterUpdated = ownerCountRemains + ownerChanges.size
        if (ownerCountAfterUpdated == 0) {
          throw new BadRequestException(resource.getString(ResourceNames.NO_OWNER))
        }

        acl.map { x =>
          x.ownerType match {
            case OwnerType.User =>
              val groupId = findGroupIdByUserId(x.id).getOrElse {
                throw new BadRequestException(resource.getString(ResourceNames.DISABLED_USER))
              }
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
              val group = persistence.Group.find(x.id).getOrElse {
                throw new BadRequestException(resource.getString(ResourceNames.INVALID_GROUP))
              }
              saveOrCreateOwnerships(user, datasetId, x.id, x.accessLevel)
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
      }
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
    // Ownershipsテーブルのエイリアス
    val o = persistence.Ownership.o
    withSQL {
      select(u.result.*)
        .from(persistence.Ownership as o)
        .innerJoin(persistence.Group as g).on(sqls.eq(g.id, o.groupId).and.eq(g.groupType, GroupType.Personal))
        .innerJoin(persistence.Member as m).on(sqls.eq(m.groupId, g.id))
        .innerJoin(persistence.User as u).on(sqls.eq(u.id, m.userId))
        .where.eq(o.datasetId, sqls.uuid(datasetId))
        .and.eq(o.accessLevel, UserAndGroupAccessLevel.OWNER_OR_PROVIDER)
    }.map(persistence.User(u.resultName)).list.apply()
  }

  /**
   * ユーザIDからPersonalグループIDを取得する。
   *
   * @param userId ユーザID
   * @param s DBセッション
   * @return 取得結果
   */
  def findGroupIdByUserId(userId: String)(implicit s: DBSession): Option[String] = {
    val u = persistence.User.u
    val m = persistence.Member.m
    val g = persistence.Group.g
    withSQL {
      select(g.result.id)
        .from(persistence.Group as g)
        .innerJoin(persistence.Member as m).on(sqls.eq(g.id, m.groupId).and.isNull(m.deletedAt))
        .innerJoin(persistence.User as u).on(sqls.eq(u.id, m.userId).and.eq(u.disabled, false))
        .where
        .eq(u.id, sqls.uuid(userId))
        .and
        .eq(g.groupType, GroupType.Personal)
        .and
        .isNull(g.deletedAt)
        .and
        .isNull(m.deletedAt)
        .limit(1)
    }.map(rs => rs.string(g.resultName.id)).single.apply()
  }

  /**
   * 指定したデータセットのゲストアクセスレベルを設定します。
   *
   * @param datasetId データセットID
   * @param accessLevel 設定するゲストアクセスレベル
   * @param user ユーザ情報
   * @return
   *        Success(DatasetData.DatasetGuestAccessLevel) 設定成功時、設定したゲストアクセスレベル
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセットが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに編集権限がない場合
   */
  def setGuestAccessLevel(
    datasetId: String,
    accessLevel: Int,
    user: User
  ): Try[DatasetData.DatasetGuestAccessLevel] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(accessLevel, "accessLevel")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        checkDatasetExisitence(datasetId)
        checkOwnerAccess(datasetId, user)

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
        DatasetData.DatasetGuestAccessLevel(accessLevel)
      }
    }
  }

  /**
   * ゲストユーザに対するOwnershipを取得する。
   *
   * @param datasetId データセットID
   * @param s DBセッション
   * @return 取得結果
   */
  private def findGuestOwnership(datasetId: String)(implicit s: DBSession): Option[persistence.Ownership] = {
    val o = persistence.Ownership.o
    withSQL(
      select(o.result.*)
        .from(persistence.Ownership as o)
        .where
        .eq(o.datasetId, sqls.uuid(datasetId))
        .and
        .eq(o.groupId, sqls.uuid(AppConf.guestGroupId))
    ).map(persistence.Ownership(o.resultName)).single.apply()
  }

  /**
   * 指定したデータセットを削除します。
   *
   * @param datasetId データセットID
   * @param user ログインユーザ情報
   * @return
   *        Success(Unit) 削除成功時
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセットが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに編集権限がない場合
   */
  def deleteDataset(datasetId: String, user: User): Try[Unit] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        checkDatasetExisitence(datasetId)
        checkOwnerAccess(datasetId, user)
        deleteDatasetById(datasetId, user)
        deleteApp(datasetId, user)
      }
    }
  }

  /**
   * データセットを論理削除する。
   *
   * @param datasetId データセットID
   * @param user ログインユーザ情報
   * @param s DBセッション
   * @return 更新件数
   */
  private def deleteDatasetById(datasetId: String, user: User)(implicit s: DBSession): Int = {
    val timestamp = DateTime.now()
    val d = persistence.Dataset.column
    withSQL {
      update(persistence.Dataset)
        .set(d.deletedAt -> timestamp, d.deletedBy -> sqls.uuid(user.id))
        .where
        .eq(d.id, sqls.uuid(datasetId))
    }.update.apply()
  }

  /**
   * 指定したユーザがデータセットのオーナーか否かを判定する。
   *
   * @param userId ユーザID
   * @param datasetId データセットID
   * @param s DBセッション
   * @return オーナーであればtrue、それ以外の場合はfalse
   */
  private def isOwner(userId: String, datasetId: String)(implicit s: DBSession): Boolean = {
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
        .innerJoin(persistence.User as u).on(sqls.eq(u.id, m.userId).and.eq(u.disabled, false))
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

  /**
   * ユーザが所属するグループ(Personal/Public問わず)を取得する。
   *
   * @param user ログインユーザ情報
   * @param s DBセッション
   * @return 所属するグループIDのリスト
   */
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
      }.map(_.string("id")).list.apply()
    }
  }

  /**
   * ファイル取得結果を表すtrait
   *
   * @param file ファイルオブジェクト
   * @param path ファイルパス
   */
  sealed trait FileResult {
    val file: persistence.File
    val path: String
  }

  /**
   * ファイル取得結果：通常ファイルを表すケースクラス
   *
   * @param file ファイルオブジェクト
   * @param path ファイルパス
   */
  case class FileResultNormal(
    file: persistence.File,
    path: String
  ) extends FileResult

  /**
   * ファイル取得結果：Zipファイルを表すケースクラス
   *
   * @param file ファイルオブジェクト
   * @param path ファイルパス
   * @param zipFile Zipファイルオブジェクト
   */
  case class FileResultZip(
    file: persistence.File,
    path: String,
    zipFile: persistence.ZipedFiles
  ) extends FileResult

  /**
   * ファイルを取得する。
   *
   * @param fileId ファイルID
   * @param session DBセッション
   * @return 取得結果
   */
  def findFile(fileId: String)(implicit session: DBSession): Option[FileResult] = {
    val file = for {
      file <- persistence.File.find(fileId)
      if file.deletedAt.isEmpty
      history <- persistence.FileHistory.find(file.historyId)
      if history.deletedAt.isEmpty
    } yield {
      FileResultNormal(file, history.filePath)
    }
    lazy val zipedFile = for {
      zipFile <- persistence.ZipedFiles.find(fileId)
      if zipFile.deletedAt.isEmpty
      history <- persistence.FileHistory.find(zipFile.historyId)
      if history.deletedAt.isEmpty
      file <- persistence.File.find(history.fileId)
      if file.deletedAt.isEmpty
    } yield {
      FileResultZip(file, history.filePath, zipFile)
    }
    file orElse zipedFile
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
    if (permission < UserAndGroupAccessLevel.ALLOW_DOWNLOAD) {
      return Failure(new AccessDeniedException(resource.getString(ResourceNames.NO_DOWNLOAD_PERMISSION), Some(user)))
    }
    Success(())
  }

  /**
   * OptionをTryに変換する。
   *
   * @tparam T オプショナルな値の型
   * @param opt オプショナルな値
   * @return
   *        Success(T) オプショナルな値が存在した場合
   *        Failure(NotFoundException) オプショナルな値が存在しなかった場合
   */
  def found[T](opt: Option[T]): Try[T] = {
    opt match {
      case Some(x) => Success(x)
      case None => Failure(new NotFoundException)
    }
  }

  /**
   * Zipファイルにパスワードが掛かっているかを判定する。
   *
   * @param zipedFile Zipファイルオブジェクト
   * @return パスワードが掛かっている場合はtrue、それ以外の場合はfalse
   */
  def hasPassword(zipedFile: persistence.ZipedFiles): Boolean = {
    (zipedFile.cenHeader(8) & 0x01) == 1
  }

  /**
   * ファイルにパスワードが掛かっていないかを確認する。
   *
   * @param file ファイル取得結果オブジェクト
   * @return
   *        Success(Unit) Zipファイルでない場合
   *        Success(Unit) パスワードのかかっていないZipファイルの場合
   *        Failure(NotFoundException) パスワードのかかったZipファイルの場合
   */
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

  /**
   * ファイルからRange指定したInputStreamを作成する。
   *
   * @param path ファイルパス
   * @param offset Rangeの開始位置
   * @param limit Rangeの取得幅
   * @return 作成したInputStream
   */
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

  /**
   * InputStreamから解凍済みのInputStreamを作成する。
   *
   * @param data InputStream
   * @param centralHeader Zipセントラルヘッダ
   * @param dataSize データサイズ
   * @param encoding 解凍するエンコーディング
   * @return 解凍済みのInputStream
   */
  def createUnzipInputStream(
    data: InputStream,
    centralHeader: Array[Byte],
    dataSize: Long,
    encoding: Charset
  ): InputStream = {
    val footer = createFooter(centralHeader, dataSize)
    val sis = new SequenceInputStream(data, new ByteArrayInputStream(footer))
    val zis = new ZipInputStream(sis, encoding)
    zis.getNextEntry
    zis
  }

  /**
   * Zipフォーマットのfooterを作成する。
   *
   * @param centralHeader Zipセントラルヘッダ
   * @param dataSize データサイズ
   * @return 構築したfooterのByte列
   */
  def createFooter(centralHeader: Array[Byte], dataSize: Long): Array[Byte] = {
    val centralHeaderSize = centralHeader.length
    val zip64EndOfCentralDirectoryRecord = if (dataSize < 0x00000000FFFFFFFFL) {
      Array.empty[Byte]
    } else {
      Array.concat(
        Array[Byte](
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
        Array[Byte](
          0x50, 0x4b, 0x06, 0x07, // sig
          0, 0, 0, 0 // disk
        ),
        longToByte8(dataSize + centralHeaderSize),
        Array[Byte](
          1, 0, 0, 0 // total disk num
        )
      )
    }
    val endOfCentralDirectoryRecord = Array.concat(
      Array[Byte](
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

  /**
   * 文字列がSJISエンコーディングかを判定する。
   *
   * @param str 判定する文字列
   * @return SJISの場合はtrue、それ以外の場合はfalse
   */
  private def isSJIS(str: String): Boolean = {
    try {
      val encoded = new String(str.getBytes("SHIFT_JIS"), "SHIFT_JIS")
      encoded.equals(str)
    } catch {
      case e: Exception => false
    }
  }

  /**
   * Long値を要素4のByte列に変換する。
   *
   * @param num Long値
   * @return 変換結果
   */
  def longToByte4(num: Long): Array[Byte] = {
    Array[Long](
      (num & 0x00000000000000FFL),
      (num & 0x000000000000FF00L) >> 8,
      (num & 0x0000000000FF0000L) >> 16,
      (num & 0x00000000FF000000L) >> 24
    ).map(_.toByte)
  }

  /**
   * Long値を要素8のByte列に変換する。
   *
   * @param num Long値
   * @return 変換結果
   */
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

  /**
   * 未削除のFileHistoryを取得する。
   *
   * @param fileId ファイルID
   * @param s DBセッション
   * @return FileHistoryのファイルパス
   */
  private def getFileHistory(fileId: String)(implicit s: DBSession): Option[String] = {
    val fh = persistence.FileHistory.syntax("fh")
    val filePath = withSQL {
      select(fh.result.filePath)
        .from(persistence.FileHistory as fh)
        .where
        .eq(fh.fileId, sqls.uuid(fileId))
        .and
        .isNull(fh.deletedAt)
    }.map(rs => rs.string(fh.resultName.filePath)).single.apply()
    filePath
  }

  /**
   * ユーザIDからPersonalグループを取得する。
   *
   * @param userId ユーザID
   * @param s DBセッション
   * @return 取得結果
   */
  private def getPersonalGroup(userId: String)(implicit s: DBSession): Option[persistence.Group] = {
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
    }.map(rs => persistence.Group(g.resultName)(rs)).single.apply()
  }

  /**
   * 未削除のデータセットを取得する。
   *
   * @param id データセットID
   * @param s DBセッション
   * @return 取得結果
   */
  private def getDataset(id: String)(implicit s: DBSession): Option[Dataset] = {
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
    }.map(rs => (rs.int(o.resultName.accessLevel), rs.int(g.resultName.groupType))).list.apply()
    // 上記のSQLではゲストユーザーは取得できないため、別途取得する必要がある
    val guestPermission = (getGuestAccessLevel(id), GroupType.Personal)
    (guestPermission :: permissions).map {
      case (accessLevel, groupType) =>
        // Provider権限のGroupはWriteできない
        if (accessLevel == GroupAccessLevel.Provider && groupType == GroupType.Public) {
          UserAndGroupAccessLevel.ALLOW_DOWNLOAD
        } else {
          accessLevel
        }
    }.max
  }

  /**
   * データセットのゲストアクセスレベルを取得する。
   *
   * @param datasetId データセットID
   * @param s DBセッション
   * @return ゲストアクセスレベル
   */
  private def getGuestAccessLevel(datasetId: String)(implicit s: DBSession): Int = {
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
    }.map(_.int(o.resultName.accessLevel)).single.apply().getOrElse(0)
  }

  /**
   * Ownerのユーザ、Providerのグループの一覧を取得する。
   *
   * @param datasetIds データセットIDのリスト
   * @param userInfo ログインユーザ情報
   * @param s DBセッション
   * @return 取得結果
   */
  private def getOwnerGroups(
    datasetIds: Seq[String],
    userInfo: User
  )(implicit s: DBSession): Map[String, Seq[DatasetData.DatasetOwnership]] = {
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
          .on(sqls.eq(m.userId, u.id).and.eq(u.disabled, false))
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
      }.map { rs =>
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
      }.list.apply()
        .groupBy(_._1)
        .map(x => (x._1, x._2.map(_._2)))

      // グループ、ログインユーザー(あれば)、他のユーザーの順にソート
      // mutable map使用
      val sortedOwners = scala.collection.mutable.Map.empty[String, Seq[DatasetData.DatasetOwnership]]
      owners.foreach { x =>
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

  /**
   * データセットのすべてのアクセス権を取得する。
   *
   * @param datasetId データセットID
   * @param userInfo ログインユーザ情報
   * @param s DBセッション
   * @return 取得結果
   */
  private def getAllOwnerships(
    datasetId: String,
    userInfo: User
  )(implicit s: DBSession): Seq[DatasetData.DatasetOwnership] = {
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
        .on(
          sqls.eq(g.id, m.groupId)
            .and.eq(g.groupType, persistence.GroupType.Personal)
            .and.eq(m.role, persistence.GroupMemberRole.Manager)
            .and.isNull(m.deletedAt)
        )
        .leftJoin(persistence.User as u)
        .on(sqls.eq(m.userId, u.id))
        .leftJoin(persistence.GroupImage as gi)
        .on(sqls.eq(g.id, gi.groupId).and.eq(gi.isPrimary, true).and.isNull(gi.deletedAt))
        .where
        .eq(o.datasetId, sqls.uuid(datasetId))
        .and
        .withRoundBracket { sql =>
          sql.withRoundBracket { sql =>
            sql
              .eq(g.groupType, GroupType.Personal)
              .and
              .gt(o.accessLevel, UserAccessLevel.Deny)
          }
            .or
            .withRoundBracket { sql =>
              sql
                .eq(g.groupType, GroupType.Public)
                .and
                .gt(o.accessLevel, GroupAccessLevel.Deny)
            }
        }
        .and
        .isNull(o.deletedAt)
        .and
        .withRoundBracket { sql =>
          sql
            .eq(u.disabled, false)
            .or
            .isNull(u.disabled)
        }
    }.map { rs =>
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
    }.list.apply()
    // ソート(ログインユーザーがownerであればそれが一番最初に、それ以外はアクセスレベル→ownerTypeの順に降順に並ぶ)
    // ログインユーザーとそれ以外のownershipsとで分ける
    val owner = owners.filter(x => x.id == userInfo.id && x.accessLevel == UserAccessLevel.Owner)

    // accessLevel, ownerTypeから順序付け用重みを計算してソート
    val sortedPartial = owners.diff(owner)
      .map(x => (x, x.accessLevel * 10 - x.ownerType))
      .sortBy(s => (s._2, s._1.fullname)).reverse.map(_._1)
    owner ++ sortedPartial
  }

  /**
   * データセットのすべての属性を取得する。
   *
   * @param datasetId データセットID
   * @param s DBセッション
   * @return 取得結果
   */
  private def getAttributes(datasetId: String)(implicit s: DBSession): Seq[DatasetData.DatasetAttribute] = {
    val da = persistence.DatasetAnnotation.syntax("da")
    val a = persistence.Annotation.syntax("d")
    withSQL {
      select(da.result.*, a.result.*)
        .from(persistence.DatasetAnnotation as da)
        .innerJoin(persistence.Annotation as a)
        .on(sqls.eq(da.annotationId, a.id).and.isNull(a.deletedAt))
        .where
        .eq(da.datasetId, sqls.uuid(datasetId))
        .and
        .isNull(da.deletedAt)
    }.map { rs =>
      (
        persistence.DatasetAnnotation(da.resultName)(rs),
        persistence.Annotation(a.resultName)(rs)
      )
    }.list.apply().map { x =>
      DatasetData.DatasetAttribute(
        name = x._2.name,
        value = x._1.data
      )
    }
  }

  /**
   * データセットのすべての画像を取得する。
   *
   * @param datasetId データセットID
   * @param s DBセッション
   * @return 取得結果
   */
  private def getImages(datasetId: String)(implicit s: DBSession): Seq[Image] = {
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
    }.map { rs =>
      (
        persistence.DatasetImage(di.resultName)(rs),
        persistence.Image(i.resultName)(rs)
      )
    }.list.apply().map { x =>
      Image(
        id = x._2.id,
        url = datasetImageDownloadRoot + datasetId + "/" + x._2.id
      )
    }
  }

  /**
   * ファイル一覧を取得する。
   *
   * @param datasetId データセットID
   * @param fileIds ファイルIDの配列。未指定の場合は、データセットの全ファイルを対象とする
   * @param permission ログインユーザのアクセスレベル
   * @param limit 取得件数
   * @param offset 取得開始位置
   * @param s DBセッション
   * @return 取得結果
   */
  private def getFiles(
    datasetId: String,
    fileIds: Seq[String] = Seq.empty,
    permission: Int,
    limit: Int = AppConf.fileLimit,
    offset: Int = 0
  )(implicit s: DBSession): Seq[DatasetData.DatasetFile] = {
    val f = persistence.File.f
    val u1 = persistence.User.syntax("u1")
    val u2 = persistence.User.syntax("u2")
    val ma1 = persistence.MailAddress.syntax("ma1")
    val ma2 = persistence.MailAddress.syntax("ma2")
    val fh = persistence.FileHistory.fh

    val results: ArrayBuffer[(Boolean, persistence.File, Option[User], Option[User])] = ArrayBuffer.empty

    withSQL {
      select(
        fh.result.isZip,
        f.result.*,
        u1.result.*,
        u2.result.*,
        ma1.result.address,
        ma2.result.address
      )
        .from(persistence.File as f)
        .leftJoin(persistence.User as u1).on(sqls.eq(f.createdBy, u1.id).and.eq(u1.disabled, false))
        .leftJoin(persistence.User as u2).on(sqls.eq(f.updatedBy, u2.id).and.eq(u2.disabled, false))
        .leftJoin(persistence.MailAddress as ma1).on(u1.id, ma1.userId)
        .leftJoin(persistence.MailAddress as ma2).on(u2.id, ma2.userId)
        .innerJoin(persistence.FileHistory as fh).on(
          sqls.eq(fh.id, f.historyId)
            .and.isNull(fh.deletedAt)
        )
        .where(sqls.toAndConditionOpt(
          Some(sqls.eqUuid(f.datasetId, datasetId)),
          Some(sqls.isNull(f.deletedAt)),
          if (fileIds == null || fileIds.size == 0) {
            None
          } else {
            Some(sqls.inUuid(f.id, fileIds))
          }
        ))
        .orderBy(f.name, f.createdAt)
        .offset(offset)
        .limit(limit)
    }.map { rs =>
      (
        rs.boolean(1),
        persistence.File(f.resultName)(rs),
        rs.stringOpt(u1.resultName.id).map { _ =>
          persistence.User(u1.resultName)(rs)
        },
        rs.stringOpt(u2.resultName.id).map { _ =>
          persistence.User(u2.resultName)(rs)
        },
        rs.stringOpt(ma1.resultName.address),
        rs.stringOpt(ma2.resultName.address)
      )
    }.list.apply().foreach {
      case (fileIsZip, file, createdUser, updatedUser, createdUserMail, updatedUserMail) => {
        results += ((fileIsZip, file, createdUser.map(u => User(u, createdUserMail.getOrElse(""))), updatedUser.map(u => User(u, updatedUserMail.getOrElse("")))))
      }
    }

    val zippedFileAmounts = getZippedFileAmounts(
      results.filter(x => x._1).map(x => x._2.historyId).toSeq
    )

    results.map {
      case (fileIsZip, file, createdUser, updatedUser) => {
        val canDownload = permission >= UserAndGroupAccessLevel.ALLOW_DOWNLOAD

        DatasetData.DatasetFile(
          id = file.id,
          name = file.name,
          description = file.description,
          url = if (canDownload) Some(AppConf.fileDownloadRoot + datasetId + "/" + file.id) else None,
          size = if (canDownload) Some(file.fileSize) else None,
          createdBy = createdUser,
          createdAt = file.createdAt.toString(),
          updatedBy = updatedUser,
          updatedAt = file.updatedAt.toString(),
          isZip = fileIsZip,
          zipedFiles = Seq.empty,
          zipCount = if (fileIsZip) {
            zippedFileAmounts.filter(x => x._1 == file.historyId).headOption.map(x => x._2).getOrElse(0)
          } else {
            0
          }
        )
      }
    }.toSeq
  }

  /**
   * データセットの持つファイル数を取得する。
   *
   * @param datasetId データセットID
   * @param s DBセッション
   * @return ファイル数
   */
  def getFileAmount(datasetId: String)(implicit s: DBSession): Int = {
    val f = persistence.File.f
    val u1 = persistence.User.syntax("u1")
    val u2 = persistence.User.syntax("u2")
    val ma1 = persistence.MailAddress.syntax("ma1")
    val ma2 = persistence.MailAddress.syntax("ma2")
    withSQL {
      select(sqls.count)
        .from(persistence.File as f)
        .leftJoin(persistence.User as u1).on(sqls.eq(f.createdBy, u1.id).and.eq(u1.disabled, false))
        .leftJoin(persistence.User as u2).on(sqls.eq(f.updatedBy, u2.id).and.eq(u2.disabled, false))
        .leftJoin(persistence.MailAddress as ma1).on(u1.id, ma1.userId)
        .leftJoin(persistence.MailAddress as ma2).on(u2.id, ma2.userId)
        .where
        .eq(f.datasetId, sqls.uuid(datasetId))
        .and
        .isNull(f.deletedAt)
    }.map(_.int(1)).single.apply().getOrElse(0)
  }

  /**
   * データセットのファイル情報(ファイル数、データサイズ)を更新する。
   *
   * @param datasetId データセットID
   * @param userId 更新者のユーザID
   * @param timestamp タイムスタンプ
   * @param s DBセッション
   * @return 更新件数
   */
  private def updateDatasetFileStatus(
    datasetId: String,
    userId: String,
    timestamp: DateTime
  )(implicit s: DBSession): Int = {
    val f = persistence.File.f
    val allFiles = withSQL {
      select(f.result.*)
        .from(persistence.File as f)
        .where
        .eq(f.datasetId, sqls.uuid(datasetId))
        .and
        .isNull(f.deletedAt)
    }.map(persistence.File(f.resultName)).list.apply()
    val totalFileSize = allFiles.foldLeft(0L)((a: Long, b: persistence.File) => a + b.fileSize)

    withSQL {
      val d = persistence.Dataset.column
      update(persistence.Dataset)
        .set(d.filesCount -> allFiles.size, d.filesSize -> totalFileSize,
          d.updatedBy -> sqls.uuid(userId), d.updatedAt -> timestamp)
        .where
        .eq(d.id, sqls.uuid(datasetId))
    }.update.apply()
  }

  /**
   * データセットのアイコン画像IDを取得する。
   *
   * @param datasetId データセットID
   * @param s DBセッション
   * @return 取得結果
   */
  private def getPrimaryImageId(datasetId: String)(implicit s: DBSession): Option[String] = {
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
    }.map(rs => rs.string(i.resultName.id)).single.apply()
  }

  /**
   * データセットのFeatured画像IDを取得する。
   *
   * @param datasetId データセットID
   * @param s DBセッション
   * @return 取得結果
   */
  private def getFeaturedImageId(datasetId: String)(implicit s: DBSession): Option[String] = {
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
    }.map(rs => rs.string(i.resultName.id)).single.apply()
  }

  /**
   * データセットのアクセスカウントを取得する。
   *
   * @param datasetId データセットID
   * @param s DBセッション
   * @return アクセスカウント
   */
  private def getAccessCount(datasetId: String)(implicit s: DBSession): Long = {
    val dal = persistence.DatasetAccessLog.dal
    persistence.DatasetAccessLog.countBy(sqls.eqUuid(dal.datasetId, datasetId))
  }

  /**
   * データセットに対象の画像が存在しているかを判定する。
   *
   * @param datasetId データセットID
   * @param imageId 画像ID
   * @param s DBセッション
   * @return データセットに関連づいており、画像が存在している場合はtrue、それ以外の場合はfalse
   */
  private def existsImage(datasetId: String, imageId: String)(implicit s: DBSession): Boolean = {
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
    }.map(rs => rs.string(i.resultName.id)).single.apply().isDefined
  }

  /**
   * データセットに対象のファイルが存在しているかを判定する。
   *
   * @param datasetId データセットID
   * @param fileId ファイルID
   * @param s DBセッション
   * @return ファイルが存在している場合はtrue、それ以外の場合はfalse
   */
  private def existsFile(datasetId: String, fileId: String)(implicit s: DBSession): Boolean = {
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
    }.map(rs => rs.string(f.resultName.id)).single.apply().isDefined
  }

  /**
   * Onwershipが存在していれば更新し、していなければ作成する。
   *
   * @param userInfo ログインユーザ情報
   * @param datasetId データセットID
   * @param groupId グループID
   * @param accessLevel アクセスレベル
   * @param s DBセッション
   */
  private def saveOrCreateOwnerships(
    userInfo: User,
    datasetId: String,
    groupId: String,
    accessLevel: Int
  )(implicit s: DBSession): Unit = {
    val myself = persistence.User.find(userInfo.id).get
    val timestamp = DateTime.now()

    val o = persistence.Ownership.o
    val ownership = withSQL {
      select(o.result.*)
        .from(persistence.Ownership as o)
        .where
        .eq(o.datasetId, sqls.uuid(datasetId))
        .and
        .eq(o.groupId, sqls.uuid(groupId))
    }.map(persistence.Ownership(o.resultName)).single.apply()
    ownership match {
      case Some(x) => {
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
      }
      case None => {
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

  /**
   * データセットのコピーを作成する。
   *
   * @param datasetId データセットID
   * @param user ログインユーザ情報
   * @return
   *        Success(CopiedDataset) コピー成功時、コピーデータセット情報
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセットが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに編集権限がない場合
   */
  def copyDataset(datasetId: String, user: User): Try[CopiedDataset] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        checkDatasetExisitence(datasetId)
        checkOwnerAccess(datasetId, user)

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
        }.map(persistence.DatasetAnnotation(da.resultName)).list.apply()

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
        }.map(persistence.DatasetImage(di.resultName)).list.apply()

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
        }.map(persistence.Ownership(o.resultName)).list.apply()

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
        CopiedDataset(newDatasetId)
      }
    }
  }

  /**
   * データセットに属性をインポートする。
   *
   * @param datasetId データセットID
   * @param file インポートファイル
   * @param user ログインユーザ情報
   * @return
   *        Success(Unit) インポート成功時
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセットが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに編集権限がない場合
   */
  def importAttribute(datasetId: String, file: FileItem, user: User): Try[Unit] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(file, "file")
      CheckUtil.checkNull(user, "user")
      val tmpFile = File.createTempFile("coi_", null)
      val csv = try {
        // 負荷分散のため、一旦テンポラリファイルに保存
        val tmpPath = Paths.get(tmpFile.getAbsolutePath)
        Files.copy(file.getInputStream, tmpPath, StandardCopyOption.REPLACE_EXISTING)

        val charset = parseCharset(tmpPath)
        use(Files.newBufferedReader(tmpPath, charset)) { in =>
          CSVReader.open(in).all().collect {
            case name :: value :: _ => (name, value)
          }
        }
      } finally {
        tmpFile.delete()
      }

      DB.localTx { implicit s =>
        checkDatasetExisitence(datasetId)
        checkOwnerAccess(datasetId, user)

        val a = persistence.Annotation.a
        val da = persistence.DatasetAnnotation.da
        val exists = withSQL {
          select
            .from(Annotation as a)
            .where
            .in(a.name, csv.map(_._1))
        }.map { rs =>
          val annotation = persistence.Annotation(a.resultName)(rs)
          (annotation.name, annotation.id)
        }.list.apply().toMap

        val timestamp = DateTime.now()

        val names = csv.map(_._1).toSet
        val annotations = names.map { name =>
          val id = exists.getOrElse(name, {
            val id = UUID.randomUUID().toString
            persistence.Annotation.create(
              id = id,
              name = name,
              createdBy = user.id,
              createdAt = timestamp,
              updatedBy = user.id,
              updatedAt = timestamp
            )
            id
          })
          (name, id)
        }.toMap

        csv.foreach {
          case (name, value) =>
            persistence.DatasetAnnotation.create(
              id = UUID.randomUUID().toString,
              datasetId = datasetId,
              annotationId = annotations(name),
              data = value,
              createdBy = user.id,
              createdAt = timestamp,
              updatedBy = user.id,
              updatedAt = timestamp
            )
        }
      }
    }
  }

  /**
   * Loan Patternでリソースを取り扱う。
   *
   * @tparam T1 取り扱うリソースの型
   * @tparam T2 リソースに対して行う処理の結果型
   * @param resource 取り扱うリソース
   * @param f リソースに対して行う処理
   * @return 処理結果
   */
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
   * 指定したファイルの文字コードを判定する。
   *
   *
   * @param path 判定対象ファイルへのパス
   * @return 文字コード
   */
  private def parseCharset(path: Path): Charset = {
    val charsets = Seq("ISO-2022-JP", "EUC-JP", "MS932", "UTF-8", "UTF-16")
      .map { c =>
        try {
          Files.readAllLines(path, Charset.forName(c))
          Some(c)
        } catch {
          case _: Throwable => None
        }
      }
      .filter(_.nonEmpty)
    if (charsets.nonEmpty) Charset.forName(charsets.head.get)
    else StandardCharsets.UTF_8
  }

  /**
   * 属性をcsv形式で取得する。
   *
   * @param datasetId データセットID
   * @param user ユーザ情報
   * @return
   *        Success(File) 取得成功時、CSVファイル
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセットが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに参照権限がない場合
   */
  def exportAttribute(datasetId: String, user: User): Try[java.io.File] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(user, "user")
      DB.readOnly { implicit s =>
        checkDatasetExisitence(datasetId)
        checkReadPermission(datasetId, user)
        val a = persistence.Annotation.a
        val da = persistence.DatasetAnnotation.da
        val attributes = withSQL {
          select
            .from(Annotation as a)
            .join(DatasetAnnotation as da).on(a.id, da.annotationId)
            .where
            .eq(da.datasetId, sqls.uuid(datasetId))
        }.map { rs =>
          val line = Seq(rs.string(a.resultName.name), rs.string(da.resultName.data)).mkString(",")
          line + System.getProperty("line.separator")
        }.list.apply()

        val file = Paths.get(AppConf.tempDir, "export.csv").toFile

        use(new FileOutputStream(file)) { out =>
          attributes.foreach { x => out.write(x.getBytes) }
        }
        file
      }
    }
  }

  /**
   * データセットのアクセスレベルの一覧を取得する。
   *
   * @param datasetId データセットID
   * @param limit 検索上限
   * @param offset 検索の開始位置
   * @param user ユーザ情報
   * @return
   *        Success(RangeSlice[DatasetOwnership]) 取得成功時、アクセスレベルの一覧(offset, limitつき)
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセットが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに参照権限がない場合
   */
  def searchOwnerships(
    datasetId: String,
    offset: Option[Int],
    limit: Option[Int],
    user: User
  ): Try[RangeSlice[DatasetOwnership]] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(offset, "offset")
      CheckUtil.checkNull(limit, "limit")
      CheckUtil.checkNull(user, "user")
      DB.readOnly { implicit s =>
        checkDatasetExisitence(datasetId)
        checkReadPermission(datasetId, user)
        val o = persistence.Ownership.o
        val u = persistence.User.u
        val g = persistence.Group.g
        val m = persistence.Member.m
        val gi = persistence.GroupImage.gi
        val count = withSQL {
          select(sqls.countDistinct(g.id))
            .from(persistence.Ownership as o)
            .innerJoin(persistence.Group as g)
            .on(sqls.eq(o.groupId, g.id).and.eq(g.groupType, GroupType.Public))
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
                .innerJoin(persistence.Group as g)
                .on(sqls.eq(o.groupId, g.id).and.eq(g.groupType, GroupType.Personal))
                .innerJoin(persistence.Member as m).on(sqls.eq(g.id, m.groupId).and.isNull(m.deletedAt))
                .innerJoin(persistence.User as u).on(sqls.eq(u.id, m.userId).and.eq(u.disabled, false))
                .where
                .eq(o.datasetId, sqls.uuid(datasetId))
                .and
                .isNull(o.deletedBy)
                .and
                .isNull(o.deletedAt)
                .and
                .gt(o.accessLevel, 0)
            )
        }.map(rs => rs.int(1)).list.apply().foldLeft(0)(_ + _)

        val list = withSQL {
          select(
            g.id, o.accessLevel, g.name, gi.imageId, g.description,
            sqls"null as fullname, '2' as type, null as organization, null as title, false as own"
          )
            .from(persistence.Ownership as o)
            .innerJoin(persistence.Group as g)
            .on(sqls.eq(o.groupId, g.id).and.eq(g.groupType, GroupType.Public))
            .innerJoin(persistence.GroupImage as gi)
            .on(sqls.eq(gi.groupId, g.id).and.eq(gi.isPrimary, true).and.isNull(gi.deletedBy))
            .where
            .eq(o.datasetId, sqls.uuid(datasetId))
            .and
            .isNull(o.deletedBy)
            .and
            .isNull(o.deletedAt)
            .and
            .gt(o.accessLevel, 0)
            .union(
              select(
                u.id, o.accessLevel, u.name, u.imageId, u.description, u.fullname,
                sqls"'1' as type",
                u.organization, u.title,
                sqls.eqUuid(u.id, user.id).and.eq(o.accessLevel, 3).append(sqls"own")
              )
                .from(persistence.Ownership as o)
                .innerJoin(persistence.Group as g)
                .on(sqls.eq(o.groupId, g.id).and.eq(g.groupType, GroupType.Personal))
                .innerJoin(persistence.Member as m).on(sqls.eq(g.id, m.groupId).and.isNull(m.deletedAt))
                .innerJoin(persistence.User as u).on(sqls.eq(u.id, m.userId).and.eq(u.disabled, false))
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
            .limit(limit.getOrElse(DEFALUT_LIMIT))
        }.map { rs =>
          (
            rs.string("id"),
            rs.int("access_level"),
            rs.string("name"),
            rs.string("image_id"),
            rs.string("description"),
            rs.string("fullname"),
            rs.int("type"),
            rs.string("organization"),
            rs.string("title")
          )
        }.list.apply().map { o =>
          val image = AppConf.imageDownloadRoot + (if (o._7 == 1) "user/" else "groups/") + o._1 + "/" + o._4
          DatasetOwnership(
            id = o._1,
            name = o._3,
            fullname = o._6,
            image = image,
            accessLevel = o._2,
            ownerType = o._7,
            description = o._5,
            organization = o._8,
            title = o._9
          )
        }.toSeq
        RangeSlice(
          summary = RangeSliceSummary(
            total = count,
            offset = offset.getOrElse(0),
            count = limit.getOrElse(DEFALUT_LIMIT)
          ),
          results = list
        )
      }
    }
  }

  /**
   * データセットの画像一覧を取得する。
   *
   * @param datasetId データセットID
   * @param limit 検索上限
   * @param offset 検索の開始位置
   * @param user ユーザー情報
   * @return
   *        Success(RangeSlice[DatasetGetImage]) 取得成功時、データセットが保持する画像の一覧(総件数、limit、offset付き)
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセットが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに参照権限がない場合
   */
  def getImages(
    datasetId: String,
    offset: Option[Int],
    limit: Option[Int],
    user: User
  ): Try[RangeSlice[DatasetData.DatasetGetImage]] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(offset, "offset")
      CheckUtil.checkNull(limit, "limit")
      CheckUtil.checkNull(user, "user")
      DB.readOnly { implicit s =>
        checkDatasetExisitence(datasetId)
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
        }.map(rs => rs.int(1)).single.apply()
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
            .limit(limit.getOrElse(DEFALUT_LIMIT))
        }.map { rs =>
          (
            rs.string(i.resultName.id),
            rs.string(i.resultName.name),
            rs.boolean(di.resultName.isPrimary)
          )
        }.list.apply().map { x =>
          DatasetData.DatasetGetImage(
            id = x._1,
            name = x._2,
            url = datasetImageDownloadRoot + datasetId + "/" + x._1,
            isPrimary = x._3
          )
        }
        RangeSlice(
          RangeSliceSummary(
            total = totalCount.getOrElse(0),
            count = limit.getOrElse(DEFALUT_LIMIT),
            offset = offset.getOrElse(0)
          ),
          result
        )
      }
    }
  }

  /**
   * 指定したデータセットのFeatured画像を変更します。
   *
   * @param datasetId データセットID
   * @param imageId 画像ID
   * @param user ログインユーザ情報
   * @return
   *        Success(DatasetData.ChangeDatasetImage) 変更後の画像ID
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセット、または画像が見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに編集権限がない場合
   */
  def changeFeaturedImage(
    datasetId: String,
    imageId: String,
    user: User
  ): Try[DatasetData.ChangeDatasetImage] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(imageId, "imageId")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        checkDatasetExisitence(datasetId)
        if (!existsImage(datasetId, imageId)) {
          throw new NotFoundException
        }
        checkOwnerAccess(datasetId, user)

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        // 対象のイメージをFeaturedに変更
        turnImageToFeatured(datasetId, imageId, myself, timestamp)
        // 対象以外のイメージをFeatured以外に変更
        turnOffFeaturedOtherImage(datasetId, imageId, myself, timestamp)

        DatasetData.ChangeDatasetImage(imageId)
      }
    }
  }

  /**
   * データセットのFeatured画像を解除する。
   *
   * @param datasetId データセットID
   * @param imageId 画像ID
   * @param myself ログインユーザ情報
   * @param timestamp タイムスタンプ
   * @param s DBセッション
   * @return 更新件数
   */
  private def turnOffFeaturedOtherImage(
    datasetId: String,
    imageId: String,
    myself: persistence.User,
    timestamp: DateTime
  )(implicit s: DBSession): Int = {
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
    }.update.apply()
  }

  /**
   * データセットのFeatured画像を設定する。
   *
   * @param datasetId データセットID
   * @param imageId 画像ID
   * @param myself ログインユーザ情報
   * @param timestamp タイムスタンプ
   * @param s DBセッション
   * @return 更新件数
   */
  private def turnImageToFeatured(
    datasetId: String,
    imageId: String,
    myself: persistence.User,
    timestamp: DateTime
  )(implicit s: DBSession): Int = {
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
    }.update.apply()
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

    val findResult = DB.readOnly { implicit s =>
      for {
        file <- found(findFile(fileId))
        _ <- found(getDataset(datasetId))
        _ <- requireAllowDownload(user, datasetId)
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
          logger.error(
            LOG_MARKER,
            "Unknown file info, fileInfo={}, isDownloadFromLocal={}",
            fileInfo,
            isDownloadFromLocal.toString
          )
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
  private def getDownloadFileByFileInfo(
    fileInfo: DatasetService.FileInfo,
    requireData: Boolean = true
  ): Try[DatasetService.DownloadFile] = {
    logger.trace(
      LOG_MARKER,
      "Called getDownloadFileByFileInfo, fileInfo={}, requireData={}",
      fileInfo,
      requireData.toString
    )
    Try {
      fileInfo match {
        case DatasetService.FileInfoLocalNormal(file, path) => {
          val downloadFile = FileManager.downloadFromLocal(path.substring(1))
          val is = if (requireData) { Files.newInputStream(downloadFile.toPath) } else { null }
          DatasetService.DownloadFileLocalNormal(is, file.name, file.fileSize)
        }
        case DatasetService.FileInfoS3Normal(file, path) => {
          val url = FileManager.generateS3PresignedURL(path.substring(1), file.name, !requireData)
          DatasetService.DownloadFileS3Normal(url)
        }
        case DatasetService.FileInfoLocalZipped(file, path, zippedFile) => {
          val is = if (requireData) {
            createRangeInputStream(
              path = Paths.get(AppConf.fileDir, path.substring(1)),
              offset = zippedFile.dataStart,
              limit = zippedFile.dataSize
            )
          } else { null }

          val encoding = if (isSJIS(zippedFile.name)) {
            Charset.forName("Shift-JIS")
          } else {
            Charset.forName("UTF-8")
          }
          try {
            val zis = if (requireData) {
              createUnzipInputStream(
                data = is,
                centralHeader = zippedFile.cenHeader,
                dataSize = zippedFile.dataSize,
                encoding = encoding
              )
            } else { null }

            DatasetService.DownloadFileLocalZipped(zis, zippedFile.name, zippedFile.fileSize)
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
              end = zippedFile.dataStart + zippedFile.dataSize - 1
            )
          } else { null }
          val encoding = if (isSJIS(zippedFile.name)) {
            Charset.forName("Shift-JIS")
          } else {
            Charset.forName("UTF-8")
          }
          try {
            val zis = if (requireData) {
              createUnzipInputStream(
                data = is,
                centralHeader = zippedFile.cenHeader,
                dataSize = zippedFile.dataSize,
                encoding = encoding
              )
            } else { null }
            DatasetService.DownloadFileS3Zipped(zis, zippedFile.name, zippedFile.fileSize)
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
  def getDownloadFileWithStream(
    datasetId: String,
    fileId: String,
    user: User
  ): Try[DatasetService.DownloadFile] = {
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
  def getDownloadFileWithoutStream(
    datasetId: String,
    fileId: String,
    user: User
  ): Try[DatasetService.DownloadFile] = {
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
   * @return
   *        Success(RangeSlice[DatasetData.DatasetFile])
   *          取得成功時、データセットが保持するファイル情報の一覧(総件数、limit、offset付き)
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセットが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに参照権限がない場合
   */
  def getDatasetFiles(
    datasetId: String,
    limit: Option[Int],
    offset: Option[Int],
    user: User
  ): Try[RangeSlice[DatasetData.DatasetFile]] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(limit, "limit")
      CheckUtil.checkNull(offset, "offset")
      CheckUtil.checkNull(user, "user")
      DB.readOnly { implicit s =>
        val dataset = checkDatasetExisitence(datasetId)
        val permission = checkReadPermission(datasetId, user)
        val validatedLimit = limit.map { x =>
          if (x < 0) { 0 } else { x }
        }.getOrElse(AppConf.fileLimit)
        val validatedOffset = offset.getOrElse(0)
        val count = getFileAmount(datasetId)
        // offsetが0未満は空リストを返却する
        if (validatedOffset < 0) {
          RangeSlice(RangeSliceSummary(count, 0, validatedOffset), Seq.empty[DatasetData.DatasetFile])
        } else {
          val files = getFiles(datasetId, Seq.empty, permission, validatedLimit, validatedOffset)
          RangeSlice(RangeSliceSummary(count, files.size, validatedOffset), files)
        }
      }
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
   * @return
   *        Success(RangeSlice[DatasetData.DatasetFile])
   *          取得成功時、Zipファイルが内部に保持するファイル情報の一覧(総件数、limit、offset付き)
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセットが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに参照権限がない場合
   *        Failure(BadRequestException) ファイルが見つからない場合
   *        Failure(BadRequestException) Zipファイル以外に対して行った場合
   */
  def getDatasetZippedFiles(
    datasetId: String,
    fileId: String,
    limit: Option[Int],
    offset: Option[Int],
    user: User
  ): Try[RangeSlice[DatasetData.DatasetZipedFile]] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(fileId, "fileId")
      CheckUtil.checkNull(limit, "limit")
      CheckUtil.checkNull(offset, "offset")
      CheckUtil.checkNull(user, "user")
      DB.readOnly { implicit s =>
        val dataset = checkDatasetExisitence(datasetId)
        val file = persistence.File.find(fileId)
        val fileHistory = file.flatMap(file => persistence.FileHistory.find(file.historyId))
        val history = fileHistory match {
          case None => {
            throw new BadRequestException(resource.getString(ResourceNames.FILE_NOT_FOUND))
          }
          case Some(x) if !x.isZip => {
            throw new BadRequestException(resource.getString(ResourceNames.CANT_TAKE_OUT_BECAUSE_NOT_ZIP))
          }
          case Some(x) if x.isZip => {
            x
          }
        }
        val permission = checkReadPermission(datasetId, user)
        val validatedLimit = limit.map { x =>
          if (x < 0) { 0 } else { x }
        }.getOrElse(AppConf.fileLimit)
        val validatedOffset = offset.getOrElse(0)
        val zipAmounts = {
          if (fileHistory.get.isZip) {
            getZippedFileAmounts(Seq(history.id)).headOption.map(x => x._2).getOrElse(0)
          } else {
            0
          }
        }

        // offsetが0未満は空リストを返却する
        if (validatedOffset < 0) {
          RangeSlice(
            RangeSliceSummary(zipAmounts, 0, validatedOffset),
            Seq.empty[DatasetData.DatasetZipedFile]
          )
        } else {
          val files = getZippedFiles(datasetId, history.id, permission, validatedLimit, validatedOffset)
          RangeSlice(RangeSliceSummary(zipAmounts, files.size, validatedOffset), files)
        }
      }
    }
  }

  /**
   * Zipファイル内ファイルを取得する。
   *
   * @param datasetId データセットID
   * @param historyId ファイル履歴ID
   * @param permission ログインユーザのアクセスレベル
   * @param limit 検索上限
   * @param offset 検索の開始位置
   * @param s DBセッション
   * @return 取得結果
   */
  private def getZippedFiles(
    datasetId: String,
    historyId: String,
    permission: Int,
    limit: Int,
    offset: Int
  )(implicit s: DBSession): Seq[DatasetZipedFile] = {
    val zf = persistence.ZipedFiles.zf
    val zipedFiles = withSQL {
      select
        .from(ZipedFiles as zf)
        .where
        .eq(zf.historyId, sqls.uuid(historyId))
        .orderBy(zf.name.asc)
        .offset(offset)
        .limit(limit)
    }.map(persistence.ZipedFiles(zf.resultName)).list.apply()
    if (zipedFiles.exists(hasPassword)) {
      return Seq.empty
    }
    val canDownload = permission >= UserAndGroupAccessLevel.ALLOW_DOWNLOAD
    zipedFiles.map { x =>
      DatasetZipedFile(
        id = x.id,
        name = x.name,
        size = if (canDownload) Some(x.fileSize) else None,
        url = if (canDownload) Some(AppConf.fileDownloadRoot + datasetId + "/" + x.id) else None
      )
    }.toSeq
  }

  /**
   * Zipファイル内ファイル件数を取得する。
   *
   * @param historyIds ファイル履歴IDの配列
   * @param s DBセッション
   * @return 取得結果
   */
  private def getZippedFileAmounts(historyIds: Seq[String])(implicit s: DBSession): Seq[(String, Int)] = {
    val zf = persistence.ZipedFiles.zf
    withSQL {
      select(zf.result.historyId, sqls.count(zf.id))
        .from(ZipedFiles as zf)
        .where.in(
          zf.historyId,
          historyIds.map(x => sqls.uuid(x))
        )
        .groupBy(zf.historyId)
    }.map { rs =>
      (
        rs.string(1),
        rs.int(2)
      )
    }.list().apply()
  }

  /**
   * 指定したデータセットにアプリを追加する。
   *
   * @param datasetId データセットID
   * @param description アプリの説明文
   * @param file アプリのJARファイル
   * @param user ユーザ情報
   * @return
   *   Success(App) 追加成功時、アプリ情報
   *   Failure(NullPointerException) datasetId、file、またはuserがnullの場合
   *   Failure(NotFoundException) データセットが存在しない場合
   *   Failure(AccessDeniedException) ユーザに権限がない場合
   *   Failure(IOException) ファイル保存時に入出力エラーが発生した場合
   */
  def addApp(datasetId: String, description: String, file: FileItem, user: User): Try[DatasetData.App] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(file, "file")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        checkDatasetExisitence(datasetId)
        checkOwnerAccess(datasetId, user)
        val timestamp = DateTime.now()
        val appId = UUID.randomUUID.toString
        val fileName = file.getName
        persistence.App.create(
          id = appId,
          name = appNameOf(fileName),
          description = Option(description),
          datasetId = Some(datasetId),
          createdBy = user.id,
          createdAt = timestamp,
          updatedBy = user.id,
          updatedAt = timestamp
        )
        AppManager.upload(appId, file)
        getApp(datasetId).getOrElse(throw new NotFoundException())
      }
    }
  }

  /**
   * 指定されたアプリ情報を取得する。
   *
   * @param datasetId データセットID
   * @param user ユーザ情報
   * @return
   *   Success(App) 取得成功時、アプリ情報
   *   Failure(NullPointerException) datasetId、appIdまたはuserがnullの場合
   *   Failure(AccessDeniedException) ユーザに管理権限がない場合
   */
  def getApp(datasetId: String, user: User): Try[RangeSlice[DatasetData.App]] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(user, "user")
      DB.readOnly { implicit s =>
        checkDatasetExisitence(datasetId)
        checkOwnerAccess(datasetId, user)
        getApp(datasetId).fold(RangeSlice(RangeSliceSummary(0, 0), Seq.empty[DatasetData.App])) { app =>
          RangeSlice(RangeSliceSummary(1, 1), Seq(app))
        }
      }
    }
  }

  /**
   * 指定されたアプリを更新する。
   *
   * @param datasetId データセットID
   * @param appId アプリID
   * @param description アプリの説明文
   * @param file アプリのJARファイル
   * @param user ユーザ情報
   * @return
   *   Success(App) 更新成功時、アプリ情報
   *   Failure(NullPointerException) datasetId、appId, fileまたはuserがnullの場合
   *   Failure(NotFoundException) データセットまたはアプリが存在しない場合
   *   Failure(AccessDeniedException) ユーザに管理権限がない場合
   *   Failure(IOException) ファイル保存時に入出力エラーが発生した場合
   */
  def updateApp(
    datasetId: String, appId: String, description: String, file: Option[FileItem], user: User
  ): Try[DatasetData.App] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(appId, "appId")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        checkDatasetExisitence(datasetId)
        getApp(datasetId).orElse(throw new NotFoundException())
        checkOwnerAccess(datasetId, user)
        val timestamp = DateTime.now()
        withSQL {
          val a = persistence.App.column
          if (file.isEmpty) {
            // ファイル指定なしの場合
            update(persistence.App)
              .set(
                a.description -> description,
                a.datasetId -> sqls.uuid(datasetId),
                a.updatedBy -> sqls.uuid(user.id),
                a.updatedAt -> timestamp
              )
              .where
              .eq(a.id, sqls.uuid(appId))
          } else {
            // ファイル指定ありの場合
            val fileName = file.get.getName
            update(persistence.App)
              .set(
                a.name -> appNameOf(fileName),
                a.description -> description,
                a.datasetId -> sqls.uuid(datasetId),
                a.updatedBy -> sqls.uuid(user.id),
                a.updatedAt -> timestamp
              )
              .where
              .eq(a.id, sqls.uuid(appId))
          }
        }.update.apply()
        if (file.nonEmpty) AppManager.upload(appId, file.get)
        getApp(datasetId).getOrElse(throw new NotFoundException())
      }
    }
  }

  /**
   * 指定されたアプリを物理削除する。
   *
   * @param datasetId データセットID
   * @param user ユーザ情報
   * @return
   *   Success(Unit) 削除成功時
   *   Failure(NullPointerException) datasetId、appIdまたはuserがnullの場合
   *   Failure(NotFoundException) データセットまたはアプリが存在しない場合
   *   Failure(AccessDeniedException) ユーザに管理権限がない場合
   */
  def deleteApp(datasetId: String, user: User): Try[Unit] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(user, "user")
      val appId = DB.localTx { implicit s =>
        checkDatasetExisitence(datasetId)
        val app = getApp(datasetId).getOrElse(throw new NotFoundException())
        checkOwnerAccess(datasetId, user)
        withSQL {
          val a = persistence.App.column
          delete
            .from(persistence.App)
            .where
            .eq(a.id, sqls.uuid(app.id))
            .and
            .isNull(a.deletedAt)
        }.update.apply()
        app.id
      }
      // Jarファイルの削除
      AppManager.delete(appId)
    }
  }

  /**
   * ファイル名からアプリ名を取得する。
   *
   * @param fileName ファイル名
   * @return アプリ名
   */
  def appNameOf(fileName: String): String = {
    fileName
  }

  /**
   * アプリ情報を取得する。
   *
   * @param datasetId データセットID
   * @param s DBセッション
   * @return アプリ情報
   */
  private def getApp(datasetId: String)(implicit s: DBSession): Option[DatasetData.App] = {
    val a = persistence.App.syntax("a")
    withSQL {
      select(a.result.*)
        .from(persistence.App as a)
        .where
        .eq(a.datasetId, sqls.uuid(datasetId))
        .and
        .isNull(a.deletedAt)
    }.map { rs =>
      val app = persistence.App(a.resultName)(rs)
      DatasetData.App(
        id = app.id,
        name = app.name,
        description = app.description.getOrElse(""),
        datasetId = datasetId,
        lastModified = app.updatedAt
      )
    }.single.apply()
  }

  /**
   * データセットに設定されているアプリのURLを取得する。
   *
   * @param datasetId データセットID
   * @param user ユーザ情報
   * @return
   *   Some(String) アプリのJNLPファイルへのURL
   *   None 設定されていない、ユーザにAPIキーがない、ユーザに権限がない、データセットが存在しない場合
   */
  private def getAppUrl(datasetId: String, user: User): Option[String] = {
    Try {
      DB.readOnly { implicit s =>
        checkDatasetExisitence(datasetId)
        for {
          _ <- requireAllowDownload(user, datasetId).toOption
          _ <- getApp(datasetId)
          _ <- getUserKey(user)
        } yield {
          AppManager.getJnlpUrl(datasetId, user.id)
        }
      }
    }.getOrElse(None)
  }

  /**
   * ユーザのAPIキー情報を取得する。
   *
   * @param user ユーザ
   * @param s DBセッション
   * @return ユーザのAPIキー情報、設定されていない場合 None
   */
  private def getUserKey(user: User)(implicit s: DBSession): Option[DatasetService.AppUser] = {
    val ak = persistence.ApiKey.syntax("ak")
    val u = persistence.User.syntax("u")
    withSQL {
      select(ak.result.*)
        .from(persistence.ApiKey as ak)
        .where(
          sqls.toAndConditionOpt(
            Some(sqls.eq(ak.userId, sqls.uuid(user.id))),
            if (user.isGuest) {
              None
            } else {
              Some(
                sqls.exists(
                  select
                  .from(persistence.User as u)
                  .where
                  .eq(u.id, ak.userId)
                  .and
                  .eq(u.disabled, false)
                  .toSQLSyntax
                )
              )
            },
            Some(sqls.isNull(ak.deletedAt)),
            Some(sqls.isNull(ak.deletedBy))
          )
        )
        .limit(1)
    }.map { rs =>
      DatasetService.AppUser(
        id = user.id,
        apiKey = rs.string(ak.resultName.apiKey),
        secretKey = rs.string(ak.resultName.secretKey)
      )
    }.single.apply()
  }

  /**
   * 指定したアプリのJNLPファイル情報を取得する。
   *
   * @param datasetId データセットID
   * @param userId ユーザID
   * @return
   *   Success(AppJnlp) 取得成功時、アプリのJNLPファイル情報
   *   Failure(NullPointerException) datasetIdまたはuserIdがnullの場合
   *   Failure(NotFoundException)
   *     ユーザが存在しないまたは無効な場合、
   *     データセットが存在しない場合、
   *     データセットに設定されたアプリが存在しない場合、
   *     ユーザにAPIキーが存在しない場合
   *   Failure(AccessDeniedException) ユーザにダウンロード権限がない場合
   */
  def getAppJnlp(datasetId: String, userId: String): Try[DatasetData.AppJnlp] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(userId, "userId")
      val db = DB.readOnly { implicit s =>
        for {
          user <- found(getUser(userId))
          dataset <- found(getDataset(datasetId))
          app <- found(getApp(datasetId))
          uk <- found(getUserKey(user))
          _ <- requireAllowDownload(user, datasetId)
        } yield {
          (dataset, app, uk)
        }
      }
      for {
        (dataset, app, uk) <- db
      } yield {
        val content = AppManager.getJnlp(
          datasetId = datasetId,
          userId = userId,
          apiKey = uk.apiKey,
          secretKey = uk.secretKey
        )
        DatasetData.AppJnlp(
          id = app.id,
          name = dataset.name,
          datasetId = datasetId,
          lastModified = app.lastModified,
          content = content
        )
      }
    }.flatMap(x => x)
  }

  /**
   * 指定したアプリのJARファイル情報を取得する。
   *
   * @param datasetId データセットID
   * @param userId ユーザID
   * @return
   *   Success(AppFile) 取得成功時、アプリのJARファイル情報
   *   Failure(NullPointerException) datasetIdまたはuserIdがnullの場合
   *   Failure(NotFoundException)
   *     ユーザが存在しないまたは無効な場合、
   *     データセットが存在しない場合、
   *     データセットに設定されたアプリが存在しない場合、
   *     ユーザにAPIキーが存在しない場合
   *   Failure(AccessDeniedException) ユーザにダウンロード権限がない場合
   */
  def getAppFile(datasetId: String, userId: String): Try[DatasetData.AppFile] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(userId, "userId")
      val db = DB.readOnly { implicit s =>
        for {
          user <- found(getUser(userId))
          dataset <- found(getDataset(datasetId))
          app <- found(getApp(datasetId))
          _ <- found(getUserKey(user))
          _ <- requireAllowDownload(user, datasetId)
        } yield {
          (dataset, app)
        }
      }
      for {
        (dataset, app) <- db
      } yield {
        val file = AppManager.download(app.id)
        val size = file.length
        val content = Files.newInputStream(file.toPath)
        DatasetData.AppFile(
          appId = app.id,
          lastModified = app.lastModified,
          size = size,
          content = content
        )
      }
    }.flatMap(x => x)
  }

  /**
   * 指定したIDのユーザを取得する。
   *
   * @param id ユーザID
   * @return 取得したユーザ、存在しないまたは無効な場合None
   */
  private def getUser(id: String)(implicit s: DBSession): Option[User] = {
    if (id == AppConf.guestUser.id) {
      return Some(AppConf.guestUser)
    }
    val u = persistence.User.u
    val ma = persistence.MailAddress.ma
    withSQL {
      select(u.result.*, ma.result.address)
        .from(persistence.User as u)
        .innerJoin(persistence.MailAddress as ma).on(u.id, ma.userId)
        .where
        .eq(u.id, sqls.uuid(id))
        .and
        .eq(u.disabled, false)
    }.map { rs =>
      val user = persistence.User(u.resultName)(rs)
      val address = rs.string(ma.resultName.address)
      User(user, address)
    }.single.apply()
  }

  /**
   * 指定したデータセットのエラー一覧を取得します。
   *
   * @param id データセットID
   * @param user ユーザ情報
   * @return
   *        Success(Seq[DatasetData.DatasetFile]) 取得成功時、エラー一覧
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) データセットが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザに参照権限がない場合
   */
  def getFileHistoryErrors(id: String, user: User): Try[Seq[DatasetData.DatasetFile]] = {

    val ret = Try {
      CheckUtil.checkNull(id, "id")
      CheckUtil.checkNull(user, "user")
      DB.readOnly { implicit s =>
        val dataset = checkDatasetExisitence(id)
        val permission = checkReadPermission(id, user)
        val histories = findFileHistoryErrors(id, user)

        histories.getOrElse(List.empty).map {
          x =>
            DatasetData.DatasetFile(
              id = x.id,
              name = x.name,
              description = x.description,
              size = Some(x.fileSize),
              url = Some(AppConf.fileDownloadRoot + id + "/" + x.id),
              createdBy = getUser(x.createdBy),
              createdAt = x.createdAt.toString,
              updatedBy = getUser(x.updatedBy),
              updatedAt = x.updatedAt.toString,
              isZip = false,
              zipedFiles = Seq.empty,
              zipCount = 0
            )
        }
      }
    }

    // 結果ログを出力する
    ret match {
      case Success(_) => {
        logger.debug("Successed getFileHistoryErrors, id = {}, user = {}", id, user)
      }
      case Failure(x) => {
        logger.info("Failed getFileHistoryErrors, id = {}, user = {}, errorClass = {}, errorMessage = {}", id, user, x.getClass, x.getMessage)
      }
    }

    ret
  }

  private def findFileHistoryErrors(id: String, user: User): Try[List[persistence.File]] = {
    Try {
      DB.localTx { implicit s =>
        val fhe = persistence.FileHistoryError.fhe
        val fh = persistence.FileHistory.fh
        val f = persistence.File.f

        withSQL {
          select
            .from(persistence.File as f)
            .innerJoin(persistence.FileHistory as fh)
            .on(
              sqls.eq(fh.fileId, f.id)
                .and.isNull(fh.deletedAt)
            )
            .innerJoin(persistence.FileHistoryError as fhe)
            .on(
              sqls.eq(fhe.historyId, fh.id)
                .and.isNull(fhe.deletedAt)
            )
            .where.eq(f.datasetId, sqls.uuid(id))
            .and.isNull(f.deletedAt)
        }.map(persistence.File(f.resultName)).list.apply()
      }
    }
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
  case class FileInfoLocalZipped(
    file: persistence.File,
    path: String,
    zippedFile: persistence.ZipedFiles
  ) extends FileInfo

  /**
   * ファイル情報：S3上に保持するZIPファイル内の個別ファイル
   *
   * @param file ファイル情報
   * @param path ファイルパス
   */
  case class FileInfoS3Zipped(
    file: persistence.File,
    path: String,
    zippedFile: persistence.ZipedFiles
  ) extends FileInfo

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
  case class DownloadFileLocalNormal(
    fileData: InputStream,
    fileName: String,
    fileSize: Long
  ) extends DownloadFile

  /**
   * ファイルダウンロード：ローカルに保持するZIPファイル内の個別ファイル
   *
   * @param fileData ファイル内容を返すストリーム
   * @param fileName ファイル名
   * @param fileSize ファイルサイズ
   */
  case class DownloadFileLocalZipped(
    fileData: InputStream,
    fileName: String,
    fileSize: Long
  ) extends DownloadFile

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
  case class DownloadFileS3Zipped(
    fileData: InputStream,
    fileName: String,
    fileSize: Long
  ) extends DownloadFile

  /** アプリ検索に用いる削除状態 */
  object GetAppDeletedTypes {
    /** 論理削除を含まない */
    val LOGICAL_DELETED_EXCLUDE = 0
    /** 論理削除を含む */
    val LOGICAL_DELETED_INCLUDE = 1
    /** 論理削除のみ */
    val LOGICAL_DELETED_ONLY = 2
  }

  /** アプリ検索に用いるデフォルトの削除状態 */
  val DEFAULT_GET_APP_DELETED_TYPE = GetAppDeletedTypes.LOGICAL_DELETED_EXCLUDE

  /**
   * ユーザのAPIキー情報
   *
   * @param id ユーザID
   * @param apiKey APIキー
   * @param secretKey シークレットキー
   */
  case class AppUser(
    id: String,
    apiKey: String,
    secretKey: String
  )
}
