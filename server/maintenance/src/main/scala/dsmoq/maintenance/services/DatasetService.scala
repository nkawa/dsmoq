package dsmoq.maintenance.services

import java.util.UUID
import java.nio.file.Paths

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.joda.time.DateTime
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.maintenance.AppConfig
import dsmoq.maintenance.data.SearchResult
import dsmoq.maintenance.data.dataset.SearchCondition
import dsmoq.maintenance.data.dataset.SearchCondition.DatasetType
import dsmoq.maintenance.data.dataset.AccessLevel
import dsmoq.maintenance.data.dataset.AclAddData
import dsmoq.maintenance.data.dataset.AclListData
import dsmoq.maintenance.data.dataset.AclUpdateData
import dsmoq.maintenance.data.dataset.OwnerType
import dsmoq.maintenance.data.dataset.SearchAclsParameter
import dsmoq.maintenance.data.dataset.SearchAclGroupParameter
import dsmoq.maintenance.data.dataset.SearchAclUserParameter
import dsmoq.maintenance.data.dataset.SearchResultDataset
import dsmoq.maintenance.data.dataset.SearchResultOwnership
import dsmoq.maintenance.data.dataset.UpdateParameter
import dsmoq.maintenance.data.dataset.AddAclGroupParameter
import dsmoq.maintenance.data.dataset.AddAclUserParameter
import dsmoq.maintenance.data.dataset.UpdateAclGroupParameter
import dsmoq.maintenance.data.dataset.UpdateAclUserParameter
import dsmoq.persistence
import dsmoq.persistence.GroupType
import dsmoq.persistence.UserAccessLevel
import dsmoq.persistence.PostgresqlHelper.PgConditionSQLBuilder
import dsmoq.persistence.PostgresqlHelper.PgSQLSyntaxType
import org.scalatra.util.MultiMap
import scalikejdbc.ConditionSQLBuilder
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SelectSQLBuilder
import scalikejdbc.SQLSyntax
import scalikejdbc.convertJavaSqlTimestampToConverter
import scalikejdbc.interpolation.Implicits.scalikejdbcSQLInterpolationImplicitDef
import scalikejdbc.interpolation.Implicits.scalikejdbcSQLSyntaxToStringImplicitDef
import scalikejdbc.select
import scalikejdbc.sqls
import scalikejdbc.update
import scalikejdbc.delete
import scalikejdbc.withSQL

/**
 * データセット処理サービス
 */
object DatasetService extends LazyLogging {
  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("MAINTENANCE_DATASET_LOG")

  /**
   * サービス名
   */
  val SERVICE_NAME = "DatasetService"

  /**
   * ユーザーのアクセス権として有効なアクセスレベル
   */
  val validUserAccessLevel = Set[AccessLevel](AccessLevel.LimitedRead, AccessLevel.FullRead, AccessLevel.Owner)

  /**
   * グループのアクセス権として有効なアクセスレベル
   */
  val validGroupAccessLevel = Set[AccessLevel](AccessLevel.LimitedRead, AccessLevel.FullRead, AccessLevel.Provider)

  /**
   * データセットを検索する。
   *
   * @param condition 検索条件
   * @return 検索結果
   */
  def search(condition: SearchCondition): SearchResult[SearchResultDataset] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "search", condition))
    val d = persistence.Dataset.d
    val o = persistence.Ownership.o
    val g = persistence.Group.g
    val m = persistence.Member.m
    val u = persistence.User.u
    def createSqlBase(topSelect: SelectSQLBuilder[Unit]): ConditionSQLBuilder[Unit] = {
      topSelect
        .from(persistence.Dataset as d)
        .where(
          sqls.toAndConditionOpt(
            condition.datasetType match {
              case DatasetType.NotDeleted => Some(sqls.isNull(d.deletedBy).and.isNull(d.deletedAt))
              case DatasetType.Deleted => Some(sqls.isNotNull(d.deletedBy).and.isNotNull(d.deletedAt))
              case _ => None
            },
            if (condition.ownerId.isEmpty) {
              None
            } else {
              Some(
                sqls.exists(
                  select
                  .from(persistence.Ownership as o)
                  .innerJoin(persistence.Group as g).on(g.id, o.groupId)
                  .innerJoin(persistence.Member as m).on(m.groupId, g.id)
                  .innerJoin(persistence.User as u).on(u.id, m.userId)
                  .where
                  .eq(o.datasetId, d.id)
                  .and
                  .eq(o.accessLevel, UserAccessLevel.Owner)
                  .and
                  .eq(g.groupType, GroupType.Personal)
                  .and
                  .eq(u.disabled, false)
                  .and
                  .upperLikeQuery(u.name, condition.ownerId)
                  .toSQLSyntax
                )
              )
            },
            if (condition.datasetName.isEmpty) {
              None
            } else {
              Some(upperLikeQuery(d.name, condition.datasetName))
            }
          )
        )
    }
    val limit = AppConfig.searchLimit
    val offset = (condition.page - 1) * limit
    DB.readOnly { implicit s =>
      val total = withSQL {
        createSqlBase(select(sqls.count))
      }.map(_.int(1)).single.apply().getOrElse(0)
      val records = withSQL {
        createSqlBase(select(d.result.*))
          .orderBy(d.createdAt, d.id)
          .offset(offset)
          .limit(limit)
      }.map { rs =>
        val dataset = persistence.Dataset(d.resultName)(rs)
        val ownerships = searchDatasetOwnerships(dataset.id)
        SearchResultDataset(
          id = dataset.id,
          name = dataset.name,
          description = dataset.description,
          owners = ownerships.collect { case SearchResultOwnership(_, _, name, AccessLevel.Owner) => name },
          numOfFiles = dataset.filesCount,
          createdAt = dataset.createdAt,
          updatedAt = dataset.updatedAt,
          deletedAt = dataset.deletedAt
        )
      }.list.apply()
      SearchResult(
        offset,
        limit,
        total,
        records
      )
    }
  }

  /**
   * データセットアクセス権追加画面を表示するための情報を取得する。
   *
   * @param param 入力パラメータ
   * @return 取得結果
   *        Failure(ServiceException) データセットIDが未指定の場合
   *        Failure(ServiceException) 存在しないIDの場合
   */
  def getAclAddData(param: SearchAclsParameter): Try[AclAddData] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "getAclAddData", param))
    val result = DB.readOnly { implicit s =>
      for {
        id <- Util.require(param.datasetId, "データセットID")
        dataset <- searchDatasetById(id)
      } yield {
        AclAddData(
          datasetId = dataset.id,
          datasetName = dataset.name
        )
      }
    }
    Util.withErrorLogging(logger, LOG_MARKER, result)
  }

  /**
   * データセットアクセス権一覧画面を表示するための情報を取得する。
   *
   * @param param 入力パラメータ
   * @return 取得結果
   *        Failure(ServiceException) データセットIDが未指定の場合
   *        Failure(ServiceException) 存在しないIDの場合
   */
  def getAclListData(param: SearchAclsParameter): Try[AclListData] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "getAclListData", param))
    val result = DB.readOnly { implicit s =>
      for {
        id <- Util.require(param.datasetId, "データセットID")
        dataset <- searchDatasetById(id)
      } yield {
        val ownerships = searchDatasetOwnerships(id)
        AclListData(
          datasetId = dataset.id,
          datasetName = dataset.name,
          ownerships = ownerships
        )
      }
    }
    Util.withErrorLogging(logger, LOG_MARKER, result)
  }

  /**
   * データセットアクセス権更新(ユーザー)画面を表示するための情報を取得する。
   *
   * @param param 入力パラメータ
   * @return 取得結果
   *        Failure(ServiceException) データセットID、ユーザーIDが未指定の場合
   *        Failure(ServiceException) データセット、ユーザーが存在しない場合
   *        Failure(ServiceException) 指定したユーザーが無効化されている場合
   */
  def getAclUpdateDataForUser(param: SearchAclUserParameter): Try[AclUpdateData] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "getAclUpdateDataForUser", param))
    val result = DB.readOnly { implicit s =>
      for {
        datasetId <- Util.require(param.datasetId, "データセットID")
        userId <- Util.require(param.userId, "ユーザーID")
        dataset <- searchDatasetById(datasetId)
        user <- searchUserById(userId)
        ownership <- searchOwnershipForUser(datasetId, userId)
      } yield {
        AclUpdateData(
          datasetId = dataset.id,
          datasetName = dataset.name,
          ownership = ownership
        )
      }
    }
    Util.withErrorLogging(logger, LOG_MARKER, result)
  }

  /**
   * IDからユーザーを取得する。
   *
   * @param userId ユーザーID
   * @param s DBセッション
   * @return 取得結果
   *        Failure(ServiceException) 存在しないIDの場合
   *        Failure(ServiceException) 指定したユーザーが無効化されている場合
   */
  def searchUserById(userId: String)(implicit s: DBSession): Try[persistence.User] = {
    val u = persistence.User.u
    val result = withSQL {
      select(u.result.*)
        .from(persistence.User as u)
        .where
        .eq(u.id, sqls.uuid(userId))
    }.map(persistence.User(u.resultName)).single.apply()
    result match {
      case Some(user) if user.disabled => Failure(new ServiceException("無効なユーザーが指定されました。"))
      case Some(user) => Success(user)
      case None => Failure(new ServiceException("存在しないユーザーが指定されました。"))
    }
  }

  /**
   * 対象のユーザーに対するアクセス権を取得する。
   *
   * @param datasetId データセットID
   * @param userId ユーザーID
   * @param s DBセッション
   * @return 取得結果
   *        Failure(ServiceException) アクセス権が取得できなかった場合
   */
  def searchOwnershipForUser(datasetId: String, userId: String)(implicit s: DBSession): Try[SearchResultOwnership] = {
    val o = persistence.Ownership.o
    val g = persistence.Group.g
    val m = persistence.Member.m
    val u = persistence.User.u
    val result = withSQL {
      select(o.result.accessLevel, u.result.*)
        .from(persistence.Ownership as o)
        .innerJoin(persistence.Group as g).on(g.id, o.groupId)
        .innerJoin(persistence.Member as m).on(m.groupId, g.id)
        .innerJoin(persistence.User as u).on(u.id, m.userId)
        .where(
          sqls.toAndConditionOpt(
            Some(sqls.eq(o.datasetId, sqls.uuid(datasetId))),
            Some(sqls.ne(o.accessLevel, persistence.UserAccessLevel.Deny)),
            Some(sqls.isNull(g.deletedBy)),
            Some(sqls.isNull(g.deletedAt)),
            Some(sqls.eq(g.groupType, persistence.GroupType.Personal)),
            Some(sqls.eq(u.id, sqls.uuid(userId))),
            Some(sqls.eq(u.disabled, false))
          )
        )
    }.map { rs =>
      val accessLevel = rs.int(o.resultName.accessLevel)
      val userId = rs.string(u.resultName.id)
      val userName = rs.string(u.resultName.name)
      val ownerType = OwnerType(persistence.OwnerType.User)
      SearchResultOwnership(
        id = userId,
        ownerType = ownerType,
        name = userName,
        accessLevel = AccessLevel(ownerType, accessLevel)
      )
    }.single.apply()
    result match {
      case Some(r) => Success(r)
      case None => Failure(new ServiceException("アクセス権が未設定です。"))
    }
  }

  /**
   * データセットアクセス権更新(グループ)画面を表示するための情報を取得する。
   *
   * @param param 入力パラメータ
   * @return 取得結果
   *        Failure(ServiceException) データセットID、グループIDが未指定の場合
   *        Failure(ServiceException) データセット、グループが存在しない場合
   *        Failure(ServiceException) 指定したグループが削除されている場合
   */
  def getAclUpdateDataForGroup(param: SearchAclGroupParameter): Try[AclUpdateData] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "getAclUpdateDataForGroup", param))
    val result = DB.readOnly { implicit s =>
      for {
        datasetId <- Util.require(param.datasetId, "データセットID")
        groupId <- Util.require(param.groupId, "グループID")
        dataset <- searchDatasetById(datasetId)
        group <- searchGroupById(groupId)
        ownership <- searchOwnershipForGroup(datasetId, groupId)
      } yield {
        AclUpdateData(
          datasetId = dataset.id,
          datasetName = dataset.name,
          ownership = ownership
        )
      }
    }
    Util.withErrorLogging(logger, LOG_MARKER, result)
  }

  /**
   * IDからグループを取得する。
   *
   * @param groupId グループID
   * @param s DBセッション
   * @return 取得結果
   *        Failure(ServiceException) 存在しないIDの場合
   *        Failure(ServiceException) 指定したグループが論理削除されている場合
   */
  def searchGroupById(groupId: String)(implicit s: DBSession): Try[persistence.Group] = {
    val g = persistence.Group.g
    val result = withSQL {
      select(g.result.*)
        .from(persistence.Group as g)
        .where
        .eq(g.id, sqls.uuid(groupId))
    }.map(persistence.Group(g.resultName)).single.apply()
    result match {
      case Some(group) if group.deletedAt.isDefined =>
        Failure(new ServiceException("削除されたグループが指定されました。"))
      case Some(group) => Success(group)
      case None => Failure(new ServiceException("存在しないグループが指定されました。"))
    }
  }

  /**
   * 対象のグループに対するアクセス権を取得する。
   *
   * @param datasetId データセットID
   * @param groupId グループID
   * @param s DBセッション
   * @return 取得結果
   *        Failure(ServiceException) アクセス権が取得できなかった場合
   */
  def searchOwnershipForGroup(datasetId: String, groupId: String)(implicit s: DBSession): Try[SearchResultOwnership] = {
    val o = persistence.Ownership.o
    val g = persistence.Group.g
    val result = withSQL {
      select(o.result.accessLevel, g.result.*)
        .from(persistence.Ownership as o)
        .innerJoin(persistence.Group as g).on(g.id, o.groupId)
        .where(
          sqls.toAndConditionOpt(
            Some(sqls.eq(o.datasetId, sqls.uuid(datasetId))),
            Some(sqls.ne(o.accessLevel, persistence.GroupAccessLevel.Deny)),
            Some(sqls.isNull(g.deletedBy)),
            Some(sqls.isNull(g.deletedAt)),
            Some(sqls.eq(g.groupType, persistence.GroupType.Public))
          )
        )
    }.map { rs =>
      val accessLevel = rs.int(o.resultName.accessLevel)
      val groupId = rs.string(g.resultName.id)
      val groupName = rs.string(g.resultName.name)
      val ownerType = OwnerType(persistence.OwnerType.Group)
      SearchResultOwnership(
        id = groupId,
        ownerType = ownerType,
        name = groupName,
        accessLevel = AccessLevel(ownerType, accessLevel)
      )
    }.single.apply()
    result match {
      case Some(r) => Success(r)
      case None => Failure(new ServiceException("アクセス権が未設定です。"))
    }
  }

  /**
   * データセットを取得する。
   *
   * @param datasetId データセットID
   * @param s DBセッション
   * @return 取得結果
   *        Failure(ServiceException) 存在しないIDの場合
   */
  def searchDatasetById(datasetId: String)(implicit s: DBSession): Try[persistence.Dataset] = {
    val d = persistence.Dataset.d
    val dataset = withSQL {
      select
        .from(persistence.Dataset as d)
        .where
        .eq(d.id, sqls.uuid(datasetId))
    }.map(persistence.Dataset(d)).single.apply
    dataset match {
      case Some(d) => Success(d)
      case None => Failure(new ServiceException("存在しないデータセットが指定されました。"))
    }
  }

  /**
   * データセットを検索する。
   *
   * @param condition 検索条件
   * @return 検索結果
   */
  def upperLikeQuery(column: SQLSyntax, value: String): SQLSyntax = {
    sqls.upperLikeQuery(column, value)
  }

  /**
   * データセットの持つアクセス権を取得する。
   *
   * @param datasetId データセットID
   * @param s DBセッション
   * @return 取得結果
   */
  def searchDatasetOwnerships(datasetId: String)(implicit s: DBSession): Seq[SearchResultOwnership] = {
    val o = persistence.Ownership.o
    val g = persistence.Group.g
    val m = persistence.Member.m
    val u = persistence.User.u
    val publics = withSQL {
      select(o.result.*, g.result.*)
        .from(persistence.Ownership as o)
        .innerJoin(persistence.Group as g).on(g.id, o.groupId)
        .where(
          sqls.toAndConditionOpt(
            Some(sqls.eq(o.datasetId, sqls.uuid(datasetId))),
            Some(sqls.ne(o.accessLevel, persistence.GroupAccessLevel.Deny)),
            Some(sqls.eq(g.groupType, persistence.GroupType.Public)),
            Some(sqls.isNull(g.deletedBy)),
            Some(sqls.isNull(g.deletedAt))
          )
        )
    }.map { rs =>
      val accessLevel = rs.int(o.resultName.accessLevel)
      val createdAt = rs.jodaDateTime(o.resultName.createdAt)
      val groupId = rs.string(g.resultName.id)
      val groupName = rs.string(g.resultName.name)
      val ownerType = OwnerType(persistence.OwnerType.Group)
      val result = SearchResultOwnership(
        id = groupId,
        ownerType = ownerType,
        name = groupName,
        accessLevel = AccessLevel(ownerType, accessLevel)
      )
      (result, createdAt)
    }.list.apply()
    val personals = withSQL {
      select(o.result.*, u.result.*)
        .from(persistence.Ownership as o)
        .innerJoin(persistence.Group as g).on(g.id, o.groupId)
        .innerJoin(persistence.Member as m).on(m.groupId, g.id)
        .innerJoin(persistence.User as u).on(u.id, m.userId)
        .where(
          sqls.toAndConditionOpt(
            Some(sqls.eq(o.datasetId, sqls.uuid(datasetId))),
            Some(sqls.ne(o.accessLevel, persistence.UserAccessLevel.Deny)),
            Some(sqls.eq(g.groupType, persistence.GroupType.Personal)),
            Some(sqls.isNull(g.deletedBy)),
            Some(sqls.isNull(g.deletedAt)),
            Some(sqls.eq(u.disabled, false))
          )
        )
    }.map { rs =>
      val accessLevel = rs.int(o.resultName.accessLevel)
      val createdAt = rs.jodaDateTime(o.resultName.createdAt)
      val userId = rs.string(u.resultName.id)
      val userName = rs.string(u.resultName.name)
      val ownerType = OwnerType(persistence.OwnerType.User)
      val result = SearchResultOwnership(
        id = userId,
        ownerType = ownerType,
        name = userName,
        accessLevel = AccessLevel(ownerType, accessLevel)
      )
      (result, createdAt)
    }.list.apply()
    (publics ++ personals).sortWith { case ((_, date1), (_, date2)) => date1.isBefore(date2) }
      .collect { case (result, _) => result }
  }

  /**
   * Seqが空ではないことを確認する。
   *
   * @param seq 確認対象
   * @return 処理結果、Seqが空の場合 Failure(ServiceException)
   */
  def checkNonEmpty(seq: Seq[String]): Try[Unit] = {
    if (seq.isEmpty) {
      Failure(new ServiceException("データセットが選択されていません。"))
    } else {
      Success(())
    }
  }

  /**
   * 論理削除を適用する。
   *
   * @param param 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) 要素が空の場合
   */
  def applyLogicalDelete(param: UpdateParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyLogicalDelete", param))
    DB.localTx { implicit s =>
      for {
        _ <- checkNonEmpty(param.targets)
        _ <- execApplyLogicalDelete(param.targets)
      } yield {
        ()
      }
    }
  }

  /**
   * データセットに論理削除を適用する。
   *
   * @param ids 論理削除対象のデータセットID
   * @param s DBセッション
   * @return 処理結果
   */
  def execApplyLogicalDelete(ids: Seq[String])(implicit s: DBSession): Try[Unit] = {
    Try {
      val timestamp = DateTime.now()
      val systemUserId = AppConfig.systemUserId
      val d = persistence.Dataset.column
      withSQL {
        update(persistence.Dataset)
          .set(
            d.deletedAt -> timestamp,
            d.deletedBy -> sqls.uuid(systemUserId),
            d.updatedAt -> timestamp,
            d.updatedBy -> sqls.uuid(systemUserId)
          )
          .where
          .inUuid(d.id, ids)
          .and
          .isNull(d.deletedAt)
          .and
          .isNull(d.deletedBy)
      }.update.apply()
    }
  }

  /**
   * 論理削除解除を適用する。
   *
   * @param param 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) 要素が空の場合
   */
  def applyCancelLogicalDelete(param: UpdateParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyCancelLogicalDelete", param))
    DB.localTx { implicit s =>
      for {
        _ <- checkNonEmpty(param.targets)
        _ <- execApplyCancelLogicalDelete(param.targets)
      } yield {
        ()
      }
    }
  }

  /**
   * データセットに論理削除解除を適用する。
   *
   * @param ids 論理削除解除対象のデータセットID
   * @param s DBセッション
   * @return 処理結果
   */
  def execApplyCancelLogicalDelete(ids: Seq[String])(implicit s: DBSession): Try[Unit] = {
    Try {
      val timestamp = DateTime.now()
      val systemUserId = AppConfig.systemUserId
      val d = persistence.Dataset.column
      withSQL {
        update(persistence.Dataset)
          .set(
            d.deletedAt -> None,
            d.deletedBy -> None,
            d.updatedAt -> timestamp,
            d.updatedBy -> sqls.uuid(systemUserId)
          )
          .where
          .inUuid(d.id, ids)
          .and
          .isNotNull(d.deletedAt)
          .and
          .isNotNull(d.deletedBy)
      }.update.apply()
    }
  }

  /**
   * 物理削除を適用する。
   *
   * @param param 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) 要素が空の場合
   *        Failure(ServiceException) データセットの物理削除に失敗した場合
   *        Failure(ServiceException) 物理ファイルの物理削除に失敗した場合
   */
  def applyPhysicalDelete(param: UpdateParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyPhysicalDelete", param))
    DB.localTx { implicit s =>
      for {
        _ <- checkNonEmpty(param.targets)
        _ <- execApplyPhysicalDelete(param.targets)
      } yield {
        ()
      }
    }
  }

  /**
   * データセット物理削除の削除対象のケースクラス
   *
   * @param apps AppID
   * @param images 画像ID
   * @param invalids 削除に失敗したデータセット
   * @param records 物理削除を行うデータセットオブジェクト
   * @param deleteFiles 削除対象の物理ファイル・ディレクトリ
   */
  case class DeleteInfo(
    apps: Seq[String],
    images: Seq[String],
    invalids: Seq[DeleteUtil.DeleteFailedData],
    records: Seq[persistence.Dataset],
    deleteFiles: Seq[DeleteUtil.DeleteTarget]
  )

  /**
   * データセットに物理削除を適用する。
   *
   * @param targets 物理削除対象のデータセットID
   * @param s DBセッション
   * @return 処理結果
   *        Failure(ServiceException) データセットの物理削除に失敗した場合
   *        Failure(ServiceException) 物理ファイルの物理削除に失敗した場合
   */
  def execApplyPhysicalDelete(targets: Seq[String])(implicit s: DBSession): Try[Unit] = {
    val deleteResult = Try {
      val datasets = getDatasets(targets)
      val (deleteds, notDeleteds) = datasets.partition(d => isLogicalDeleted(d))
      val (synchronizingOrDeletings, records) = datasets.partition(d => isSynchronizingState(d) || isDeletingState(d))
      val (appIds, apps) = pickupDeleteApps(records)
      val (imageIds, images) = pickupDeleteImages(records)
      val invalids = notDeleteds.map { dataset =>
        DeleteUtil.DeleteFailedData("データセット", "論理削除済みではない", dataset.name)
      } ++ synchronizingOrDeletings.map { dataset =>
        DeleteUtil.DeleteFailedData("データセット", "ファイルが移動中、または削除中", dataset.name)
      }
      val datasetFiles = records.flatMap(getDatasetFile)
      DeleteInfo(
        appIds,
        imageIds,
        invalids,
        records,
        apps ++ images ++ datasetFiles
      )
    }
    for {
      DeleteInfo(apps, images, invalids, records, deleteFiles) <- deleteResult
      _ <- deleteDatasetDBData(records, apps, images)
      deleteFaileds <- DeleteUtil.deletePhysicalFiles(deleteFiles)
      _ <- DeleteUtil.deleteResultToTry(invalids, deleteFaileds)
    } yield {
      ()
    }
  }

  /**
   * データセット関連のDBデータを物理削除する。
   *
   * @param datasets 削除対象のデータセットオブジェクトのリスト
   * @param apps 削除対象のAppIDのリスト
   * @param images 削除対象の画像IDのリスト
   * @param s DBセッション
   * @return 処理結果
   */
  def deleteDatasetDBData(
    datasets: Seq[persistence.Dataset],
    apps: Seq[String],
    images: Seq[String]
  )(implicit s: DBSession): Try[Unit] = {
    Try {
      deleteFiles(datasets.map(_.id))
      deleteAnnotations(datasets.map(_.id))
      deleteApps(apps)
      deleteImages(images)
      deleteDatasets(datasets.map(_.id))
    }
  }

  /**
   * 削除対象のAppの物理ファイルを取得する。
   *
   * @param datasets 削除対象のデータセットオブジェクトのリスト
   * @param s DBセッション
   * @return 削除対象のAppの物理ファイルのリスト
   */
  def pickupDeleteApps(
    datasets: Seq[persistence.Dataset]
  )(implicit s: DBSession): (Seq[String], Seq[DeleteUtil.DeleteTarget]) = {
    if (datasets.isEmpty) {
      return (Seq.empty, Seq.empty)
    }
    val da = persistence.DatasetApp.v
    val appIds = withSQL {
      select(sqls.distinct(da.result.appId))
        .from(persistence.DatasetApp as da)
        .where
        .in(da.datasetId, datasets.map(dataset => sqls.uuid(dataset.id)))
    }.map(_.string(da.resultName.appId)).list.apply().toSet.toSeq
    val deleteApps = appIds.map { id =>
      DeleteUtil.LocalFile(Paths.get(AppConfig.appDir, "upload", id))
    }
    (appIds, deleteApps)
  }

  /**
   * 削除対象のImageの物理ディレクトリを取得する。
   *
   * @param datasets 削除対象のデータセットオブジェクトのリスト
   * @param s DBセッション
   * @return 削除対象のImageの物理ディレクトリのリスト
   */
  def pickupDeleteImages(
    datasets: Seq[persistence.Dataset]
  )(implicit s: DBSession): (Seq[String], Seq[DeleteUtil.DeleteTarget]) = {
    if (datasets.isEmpty) {
      return (Seq.empty, Seq.empty)
    }
    val di = persistence.DatasetImage.di
    val imageIds = withSQL {
      select(sqls.distinct(di.result.imageId))
        .from(persistence.DatasetImage as di)
        .where
        .in(di.datasetId, datasets.map(dataset => sqls.uuid(dataset.id)))
    }.map(_.string(di.resultName.imageId)).list.apply()
    val deletableImageIds = imageIds.filter { id =>
      DeleteUtil.canDeleteImage(imageId = id, datasetIds = datasets.map(_.id))
    }
    val deleteImages = deletableImageIds.map { id =>
      DeleteUtil.LocalFile(Paths.get(AppConfig.imageDir, "upload", id))
    }
    (deletableImageIds, deleteImages)
  }

  /**
   * 削除対象のデータセットの物理ディレクトリを取得する。
   *
   * @param dataset 削除対象のデータセットオブジェクト
   * @return 削除対象のデータセットの物理ディレクトリのリスト
   */
  def getDatasetFile(dataset: persistence.Dataset): Seq[DeleteUtil.DeleteTarget] = {
    val localDir = if (dataset.localState == SaveStatus.Saved) {
      val path = Paths.get(AppConfig.fileDir, dataset.id)
      Seq(DeleteUtil.LocalFile(path))
    } else {
      Seq.empty
    }
    val s3Dir = if (dataset.s3State == SaveStatus.Saved) {
      Seq(DeleteUtil.S3File(AppConfig.s3UploadRoot, dataset.id))
    } else {
      Seq.empty
    }
    localDir ++ s3Dir
  }

  /**
   * 論理削除済みかを判定する。
   *
   * @param dataset データセットオブジェクト
   * @return 論理削除済みであればtrue、そうでなければfalse
   */
  def isLogicalDeleted(dataset: persistence.Dataset): Boolean = {
    dataset.deletedAt.isDefined && dataset.deletedBy.isDefined
  }

  /**
   * データセットのローカル、S3の保存状態が移動中であるかを判定する。
   * @param dataset データセットオブジェクト
   * @return 移動中であればtrue、そうでなければfalse
   */
  def isSynchronizingState(dataset: persistence.Dataset): Boolean = {
    dataset.localState == SaveStatus.Synchronizing || dataset.s3State == SaveStatus.Synchronizing
  }

  /**
   * データセットのローカル、S3の保存状態が削除中であるかを判定する。
   * @param dataset データセットオブジェクト
   * @return 削除中であればtrue、そうでなければfalse
   */
  def isDeletingState(dataset: persistence.Dataset): Boolean = {
    dataset.localState == SaveStatus.Deleting || dataset.s3State == SaveStatus.Deleting
  }

  /**
   * データセットを取得する。
   *
   * @param ids データセットID
   * @param s DBセッション
   * @return データセットオブジェクトのリスト
   */
  def getDatasets(
    ids: Seq[String]
  )(implicit s: DBSession): Seq[persistence.Dataset] = {
    if (ids.isEmpty) {
      return Seq.empty
    }
    val d = persistence.Dataset.d
    withSQL {
      select
        .from(persistence.Dataset as d)
        .where
        .inUuid(d.id, ids)
    }.map(persistence.Dataset(d.resultName)).list.apply()
  }

  /**
   * File関連のDBデータを物理削除する。
   *
   * @param datasetIds データセットIDのリスト
   * @param s DBセッション
   */
  def deleteFiles(datasetIds: Seq[String])(implicit s: DBSession): Unit = {
    if (datasetIds.isEmpty) {
      return
    }
    val f = persistence.File.f
    val fileIds = withSQL {
      select(f.result.id)
        .from(persistence.File as f)
        .where
        .in(f.datasetId, datasetIds.map(sqls.uuid))
    }.map(_.string(f.resultName.id)).list.apply()
    withSQL {
      delete
        .from(persistence.File)
        .where
        .in(persistence.File.column.id, fileIds.map(sqls.uuid))
    }.update.apply()
    val fh = persistence.FileHistory.fh
    val fileHistoryIds = withSQL {
      select(fh.result.id)
        .from(persistence.FileHistory as fh)
        .where
        .in(fh.fileId, fileIds.map(sqls.uuid))
    }.map(_.string(fh.resultName.id)).list.apply()
    withSQL {
      delete
        .from(persistence.FileHistory)
        .where
        .in(persistence.FileHistory.column.id, fileHistoryIds.map(sqls.uuid))
    }.update.apply()
    withSQL {
      delete
        .from(persistence.ZipedFiles)
        .where
        .in(persistence.ZipedFiles.column.historyId, fileHistoryIds.map(sqls.uuid))
    }.update.apply()
  }

  /**
   * Dataset関連のDBデータを物理削除する。
   *
   * @param datasetIds データセットIDのリスト
   * @param s DBセッション
   */
  def deleteDatasets(datasetIds: Seq[String])(implicit s: DBSession): Unit = {
    if (datasetIds.isEmpty) {
      return
    }
    withSQL {
      delete
        .from(persistence.Ownership)
        .where
        .inUuid(persistence.Ownership.column.datasetId, datasetIds)
    }.update.apply()
    withSQL {
      delete
        .from(persistence.DatasetImage)
        .where
        .inUuid(persistence.DatasetImage.column.datasetId, datasetIds)
    }.update.apply()
    withSQL {
      delete
        .from(persistence.DatasetApp)
        .where
        .inUuid(persistence.DatasetApp.column.datasetId, datasetIds)
    }.update.apply()
    withSQL {
      delete
        .from(persistence.Dataset)
        .where
        .inUuid(persistence.Dataset.column.id, datasetIds)
    }.update.apply()
  }

  /**
   * Imageを物理削除する。
   *
   * @param imageIds 画像IDのリスト
   * @param s DBセッション
   */
  def deleteImages(imageIds: Seq[String])(implicit s: DBSession): Unit = {
    if (imageIds.isEmpty) {
      return
    }
    withSQL {
      delete
        .from(persistence.Image)
        .where
        .inUuid(persistence.Image.column.id, imageIds)
    }.update.apply()
  }

  /**
   * Appを物理削除する。
   *
   * @param appIds AppIDのリスト
   * @param s DBセッション
   */
  def deleteApps(appIds: Seq[String])(implicit s: DBSession): Unit = {
    if (appIds.isEmpty) {
      return
    }
    withSQL {
      delete
        .from(persistence.App)
        .where
        .inUuid(persistence.App.column.id, appIds)
    }.update.apply()
  }

  /**
   * Annotation, DatasetAnnotationを物理削除する。
   * Annotationは、他のデータセットから使用されていない場合のみ削除する。
   *
   * @param datasetIds データセットIDのリスト
   * @param s DBセッション
   */
  def deleteAnnotations(datasetIds: Seq[String])(implicit s: DBSession): Unit = {
    if (datasetIds.isEmpty) {
      return
    }
    val da = persistence.DatasetAnnotation.da
    val ac = persistence.Annotation.column
    withSQL {
      delete
        .from(persistence.Annotation)
        .where
        .notExists(
          select
            .from(persistence.DatasetAnnotation as da)
            .where
            .eq(da.annotationId, ac.id)
            .and
            .notIn(da.datasetId, datasetIds.map(sqls.uuid))
        )
    }.update.apply()
    val dac = persistence.DatasetAnnotation.column
    withSQL {
      delete
        .from(persistence.DatasetAnnotation)
        .where
        .in(dac.datasetId, datasetIds.map(sqls.uuid))
    }.update.apply()
  }

  /**
   * アクセス権追加・更新時に適切なアクセスレベルかを確認する。
   *
   * @param accessLevel チェック対象のアクセスレベル
   * @param validAccessLevels 有効なアクセスレベル
   * @param 確認結果
   *        Failure(ServiceException) チェック対象が有効なアクセスレベルに含まれなかった場合
   */
  def checkAccessLevel(accessLevel: AccessLevel, validAccessLevels: Set[AccessLevel]): Try[Unit] = {
    if (validAccessLevels.contains(accessLevel)) {
      Success(())
    } else {
      Failure(new ServiceException("無効なアクセス権が指定されました。"))
    }
  }

  /**
   * ユーザーのアクセス権を更新する。
   *
   * @param param 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) データセットID、ユーザーIDが未指定の場合
   *        Failure(ServiceException) アクセスレベルの指定がLimitedRead、FullRead、Owner以外の場合
   *        Failure(ServiceException) データセット、ユーザーが存在しない場合
   *        Failure(ServiceException) 指定したユーザーが無効化されている場合
   */
  def applyUpdateAclUser(param: UpdateAclUserParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyUpdateAclUser", param))
    DB.localTx { implicit s =>
      for {
        datasetId <- Util.require(param.datasetId, "データセットID")
        userId <- Util.require(param.userId, "ユーザーID")
        _ <- checkAccessLevel(param.accessLevel, validUserAccessLevel)
        dataset <- searchDatasetById(datasetId)
        _ <- searchUserById(userId)
        group <- searchUserGroupById(userId)
        ownership <- searchOwnership(datasetId, group.id)
        notDenyOwnership <- requireNotDenyOwnership(ownership)
        _ <- updateOwnership(notDenyOwnership.id, param.accessLevel)
      } yield {
        ()
      }
    }
  }

  /**
   * ユーザーのアクセス権を削除する。
   *
   * @param param 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) データセットIDが未指定の場合
   *        Failure(ServiceException) データセット、ユーザーが存在しない場合
   *        Failure(ServiceException) 指定したユーザーが無効化されている場合
   */
  def applyDeleteAclUser(param: UpdateAclUserParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyDeleteAclUser", param))
    DB.localTx { implicit s =>
      for {
        datasetId <- Util.require(param.datasetId, "データセットID")
        userId <- Util.require(param.userId, "ユーザーID")
        dataset <- searchDatasetById(datasetId)
        _ <- searchUserById(userId)
        group <- searchUserGroupById(userId)
        ownership <- searchOwnership(datasetId, group.id)
        notDenyOwnership <- requireNotDenyOwnership(ownership)
        _ <- updateOwnership(notDenyOwnership.id, AccessLevel.Deny)
      } yield {
        ()
      }
    }
  }

  /**
   * ユーザーのOwnershipを更新する。
   *
   * @param ownershipId OwnershipId
   * @param accessLevel アクセスレベル
   * @param s DBセッション
   * @return 処理結果
   */
  def updateOwnership(
    ownershipId: String,
    accessLevel: AccessLevel
  )(implicit s: DBSession): Try[Unit] = {
    Try {
      val timestamp = DateTime.now()
      val systemUserId = AppConfig.systemUserId
      val oc = persistence.Ownership.column
      val o = persistence.Ownership.o
      val g = persistence.Group.g
      val m = persistence.Member.m
      val u = persistence.User.u
      withSQL {
        update(persistence.Ownership)
          .set(
            oc.accessLevel -> accessLevel.toDBValue,
            oc.updatedAt -> timestamp,
            oc.updatedBy -> sqls.uuid(systemUserId)
          )
          .where
          .eq(oc.id, sqls.uuid(ownershipId))
      }.update.apply()
    }
  }

  /**
   * グループのアクセス権を更新する。
   *
   * @param param 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) データセットID、グループIDが未指定の場合
   *        Failure(ServiceException) アクセスレベルの指定がLimitedRead、FullRead、Provider以外の場合
   *        Failure(ServiceException) データセット、グループが存在しない場合
   *        Failure(ServiceException) 指定したグループが削除されている場合
   */
  def applyUpdateAclGroup(param: UpdateAclGroupParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyUpdateAclGroup", param))
    DB.localTx { implicit s =>
      for {
        datasetId <- Util.require(param.datasetId, "データセットID")
        groupId <- Util.require(param.groupId, "グループID")
        _ <- checkAccessLevel(param.accessLevel, validGroupAccessLevel)
        dataset <- searchDatasetById(datasetId)
        _ <- searchGroupById(groupId)
        ownership <- searchOwnership(datasetId, groupId)
        notDenyOwnership <- requireNotDenyOwnership(ownership)
        _ <- updateOwnership(notDenyOwnership.id, param.accessLevel)
      } yield {
        ()
      }
    }
  }

  /**
   * グループのアクセス権を削除する。
   *
   * @param param 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) データセットID、グループIDが未指定の場合
   *        Failure(ServiceException) データセット、グループが存在しない場合
   *        Failure(ServiceException) 指定したグループが削除されている場合
   */
  def applyDeleteAclGroup(param: UpdateAclGroupParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyDeleteAclGroup", param))
    DB.localTx { implicit s =>
      for {
        datasetId <- Util.require(param.datasetId, "データセットID")
        groupId <- Util.require(param.groupId, "グループID")
        dataset <- searchDatasetById(datasetId)
        _ <- searchGroupById(groupId)
        ownership <- searchOwnership(datasetId, groupId)
        notDenyOwnership <- requireNotDenyOwnership(ownership)
        _ <- updateOwnership(notDenyOwnership.id, AccessLevel.Deny)
      } yield {
        ()
      }
    }
  }

  /**
   * ユーザーのアクセス権を追加する。
   *
   * @param param 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) データセットIDが未指定の場合
   *        Failure(ServiceException) ユーザー名が未指定の場合
   *        Failure(ServiceException) アクセスレベルの指定がLimitedRead、FullRead、Owner以外の場合
   *        Failure(ServiceException) データセット、ユーザーが存在しない場合
   *        Failure(ServiceException) 既にDeny以外のアクセス権を持っている場合
   *        Failure(ServiceException) 指定したユーザーが無効化されている場合
   */
  def applyAddAclUser(param: AddAclUserParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyAddAclUser", param))
    DB.localTx { implicit s =>
      for {
        datasetId <- Util.require(param.datasetId, "データセットID")
        userName <- Util.require(param.userName, "ユーザー名")
        _ <- checkAccessLevel(param.accessLevel, validUserAccessLevel)
        dataset <- searchDatasetById(datasetId)
        group <- searchUserGroupByName(userName)
        ownership <- searchOwnership(datasetId, group.id)
        _ <- checkOwnershipNotExistsOrDeny(ownership, "ユーザー")
        _ <- upsertOwnership(datasetId, group.id, ownership, param.accessLevel)
      } yield {
        ()
      }
    }
  }

  /**
   * OwnershipをUpsertする。
   *
   * @param datasetId データセットID
   * @param groupId グループID
   * @param ownership オプショナルなアクセス権
   * @param accessLevel アクセスレベル
   * @param s DBセッション
   * @return 処理結果
   */
  def upsertOwnership(
    datasetId: String,
    groupId: String,
    ownership: Option[persistence.Ownership],
    accessLevel: AccessLevel
  )(implicit s: DBSession): Try[Unit] = {
    ownership match {
      case Some(o) => updateOwnership(o.id, accessLevel)
      case None => addOwnership(datasetId, groupId, accessLevel)
    }
  }

  /**
   * ユーザーIDからユーザーのPersonalグループを取得する。
   *
   * @param userId ユーザーID
   * @param s DBセッション
   * @return 取得結果
   *        Failure(ServiceException) 存在しないユーザーの場合
   *        Failure(ServiceException) 指定したユーザーが無効化されている場合
   */
  def searchUserGroupById(userId: String)(implicit s: DBSession): Try[persistence.Group] = {
    val g = persistence.Group.g
    val m = persistence.Member.m
    val u = persistence.User.u
    val result = withSQL {
      select(g.result.*, u.result.disabled)
        .from(persistence.Group as g)
        .innerJoin(persistence.Member as m).on(m.groupId, g.id)
        .innerJoin(persistence.User as u).on(u.id, m.userId)
        .where
        .eq(g.groupType, persistence.GroupType.Personal)
        .and
        .eq(u.id, sqls.uuid(userId))
    }.map { rs =>
      val group = persistence.Group(g.resultName)(rs)
      val disabled = rs.boolean(u.resultName.disabled)
      (group, disabled)
    }.single.apply()
    result match {
      case Some((group, true)) => Failure(new ServiceException("無効なユーザーが指定されました。"))
      case Some((group, false)) => Success(group)
      case None => Failure(new ServiceException("存在しないユーザーが指定されました。"))
    }
  }

  /**
   * ユーザー名からユーザーのPersonalグループを取得する。
   *
   * @param userName ユーザー名
   * @param s DBセッション
   * @return 取得結果
   *        Failure(ServiceException) 存在しないユーザーの場合
   *        Failure(ServiceException) 指定したユーザーが無効化されている場合
   */
  def searchUserGroupByName(userName: String)(implicit s: DBSession): Try[persistence.Group] = {
    val g = persistence.Group.g
    val m = persistence.Member.m
    val u = persistence.User.u
    val result = withSQL {
      select(g.result.*, u.result.disabled)
        .from(persistence.Group as g)
        .innerJoin(persistence.Member as m).on(m.groupId, g.id)
        .innerJoin(persistence.User as u).on(u.id, m.userId)
        .where
        .eq(g.groupType, persistence.GroupType.Personal)
        .and
        .eq(u.name, userName)
    }.map { rs =>
      val group = persistence.Group(g.resultName)(rs)
      val disabled = rs.boolean(u.resultName.disabled)
      (group, disabled)
    }.single.apply()
    result match {
      case Some((group, true)) => Failure(new ServiceException("無効なユーザーが指定されました。"))
      case Some((group, false)) => Success(group)
      case None => Failure(new ServiceException("存在しないユーザーが指定されました。"))
    }
  }

  /**
   * データセットとグループの間のアクセス権を取得する。
   *
   * @param datasetId データセットID
   * @param groupId グループID
   * @return 確認結果
   */
  def searchOwnership(
    datasetId: String,
    groupId: String
  )(implicit s: DBSession): Try[Option[persistence.Ownership]] = {
    Try {
      val o = persistence.Ownership.o
      withSQL {
        select
          .from(persistence.Ownership as o)
          .where
          .eq(o.datasetId, sqls.uuid(datasetId))
          .and
          .eq(o.groupId, sqls.uuid(groupId))
      }.map(persistence.Ownership(o.resultName)).single.apply()
    }
  }

  /**
   * AccessLevelがDeny以外のアクセス権を取得する。
   *
   * @param ownership オプショナルなアクセス権
   * @return 取得結果
   *        Failure(ServiceException) AccessLevelがDenyか、アクセス権がない場合
   */
  def requireNotDenyOwnership(ownership: Option[persistence.Ownership]): Try[persistence.Ownership] = {
    ownership match {
      case Some(o) if o.accessLevel != persistence.UserAccessLevel.Deny => Success(o)
      case _ => Failure(new ServiceException(s"まだアクセス権の登録がありません。"))
    }
  }

  /**
   * アクセス権が存在しないか、またはAccessLevelがDenyであるかを確認する。
   *
   * @param ownership オプショナルなアクセス権
   * @param target アクセス権の対象(ユーザー/グループ)
   * @return 確認結果(アクセス権がない場合、AccessLevelがDenyの場合)
   *        Failure(ServiceException) AccessLevelがDenyではない場合
   */
  def checkOwnershipNotExistsOrDeny(
    ownership: Option[persistence.Ownership],
    target: String
  ): Try[Unit] = {
    ownership match {
      case Some(o) if o.accessLevel != persistence.UserAccessLevel.Deny =>
        Failure(new ServiceException(s"既に登録のある${target}が指定されました。"))
      case _ => Success(())
    }
  }

  /**
   * データセットとグループの間にアクセス権を追加する。
   *
   * @param datasetId データセットID
   * @param groupId グループID
   * @param accessLevel アクセスレベル
   * @return 処理結果
   */
  def addOwnership(
    datasetId: String,
    groupId: String,
    accessLevel: AccessLevel
  )(implicit s: DBSession): Try[Unit] = {
    Try {
      val id = UUID.randomUUID.toString
      val timestamp = DateTime.now()
      val systemUserId = AppConfig.systemUserId
      persistence.Ownership.create(
        id = id,
        datasetId = datasetId,
        groupId = groupId,
        accessLevel = accessLevel.toDBValue,
        createdBy = systemUserId,
        createdAt = timestamp,
        updatedBy = systemUserId,
        updatedAt = timestamp
      )
    }
  }

  /**
   * グループのアクセス権を追加する。
   *
   * @param param 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) データセットIDが未指定の場合
   *        Failure(ServiceException) グループ名が未指定の場合
   *        Failure(ServiceException) アクセスレベルの指定がLimitedRead、FullRead、Provider以外の場合
   *        Failure(ServiceException) データセット、グループが存在しない場合
   *        Failure(ServiceException) 既にDeny以外のアクセス権を持っている場合
   *        Failure(ServiceException) 指定したグループが削除されている場合
   */
  def applyAddAclGroup(param: AddAclGroupParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyAddAclGroup", param))
    DB.localTx { implicit s =>
      for {
        datasetId <- Util.require(param.datasetId, "データセットID")
        groupName <- Util.require(param.groupName, "グループ名")
        _ <- checkAccessLevel(param.accessLevel, validGroupAccessLevel)
        dataset <- searchDatasetById(datasetId)
        group <- searchGroupByName(groupName)
        ownership <- searchOwnership(datasetId, group.id)
        _ <- checkOwnershipNotExistsOrDeny(ownership, "グループ")
        _ <- upsertOwnership(datasetId, group.id, ownership, param.accessLevel)
      } yield {
        ()
      }
    }
  }

  /**
   * グループ名からグループを取得する。
   *
   * @param groupName グループ名
   * @param s DBセッション
   * @return 取得結果
   *        Failure(ServiceException) 存在しないグループの場合
   *        Failure(ServiceException) 指定したグループが論理削除されている場合
   */
  def searchGroupByName(groupName: String)(implicit s: DBSession): Try[persistence.Group] = {
    val g = persistence.Group.g
    val result = withSQL {
      select(g.result.*)
        .from(persistence.Group as g)
        .where
        .eq(g.name, groupName)
    }.map(persistence.Group(g.resultName)).single.apply()
    result match {
      case Some(group) if group.deletedAt.isDefined =>
        Failure(new ServiceException("削除されたグループが指定されました。"))
      case Some(group) => Success(group)
      case None => Failure(new ServiceException("存在しないグループが指定されました。"))
    }
  }

  /**
   * POST /dataset/applyの更新操作を行う。
   *
   * @param params 入力パラメータ
   * @param multiParams 入力パラメータ(複数取得可能)
   * @return 処理結果
   *        Failure(ServiceException) 存在しない操作の場合
   */
  def applyChange(params: Map[String, String], multiParams: MultiMap): Try[Unit] = {
    val param = UpdateParameter.fromMap(multiParams)
    val result = params.get("update") match {
      case Some("logical_delete") => applyLogicalDelete(param)
      case Some("cancel_logical_delete") => applyCancelLogicalDelete(param)
      case Some("physical_delete") => applyPhysicalDelete(param)
      case _ => Failure(new ServiceException("無効な操作です。"))
    }
    Util.withErrorLogging(logger, LOG_MARKER, result)
  }

  /**
   * POST /dataset/acl/update/user/applyの更新操作を行う。
   *
   * @param params 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) 存在しない操作の場合
   */
  def applyChangeForAclUpdateUser(params: Map[String, String]): Try[Unit] = {
    val param = UpdateAclUserParameter.fromMap(params)
    val result = params.get("update") match {
      case Some("update") => applyUpdateAclUser(param)
      case Some("delete") => applyDeleteAclUser(param)
      case _ => Failure(new ServiceException("無効な操作です。"))
    }
    Util.withErrorLogging(logger, LOG_MARKER, result)
  }

  /**
   * POST /dataset/acl/update/group/applyの更新操作を行う。
   *
   * @param params 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) 存在しない操作の場合
   */
  def applyChangeForAclUpdateGroup(params: Map[String, String]): Try[Unit] = {
    val param = UpdateAclGroupParameter.fromMap(params)
    val result = params.get("update") match {
      case Some("update") => applyUpdateAclGroup(param)
      case Some("delete") => applyDeleteAclGroup(param)
      case _ => Failure(new ServiceException("無効な操作です。"))
    }
    Util.withErrorLogging(logger, LOG_MARKER, result)
  }

  /**
   * POST /dataset/acl/add/user/applyの更新操作を行う。
   *
   * @param params 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) 存在しない操作の場合
   */
  def applyChangeForAclAddUser(params: Map[String, String]): Try[Unit] = {
    val param = AddAclUserParameter.fromMap(params)
    val result = params.get("update") match {
      case Some("add") => applyAddAclUser(param)
      case _ => Failure(new ServiceException("無効な操作です。"))
    }
    Util.withErrorLogging(logger, LOG_MARKER, result)
  }

  /**
   * POST /dataset/acl/add/group/applyの更新操作を行う。
   *
   * @param params 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) 存在しない操作の場合
   */
  def applyChangeForAclAddGroup(params: Map[String, String]): Try[Unit] = {
    val param = AddAclGroupParameter.fromMap(params)
    val result = params.get("update") match {
      case Some("add") => applyAddAclGroup(param)
      case _ => Failure(new ServiceException("無効な操作です。"))
    }
    Util.withErrorLogging(logger, LOG_MARKER, result)
  }
}
