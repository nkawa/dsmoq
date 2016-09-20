package dsmoq.maintenance.services

import java.util.UUID

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
import scalikejdbc.ConditionSQLBuilder
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SelectSQLBuilder
import scalikejdbc.SQLSyntax
import scalikejdbc.interpolation.Implicits.scalikejdbcSQLInterpolationImplicitDef
import scalikejdbc.interpolation.Implicits.scalikejdbcSQLSyntaxToStringImplicitDef
import scalikejdbc.select
import scalikejdbc.sqls
import scalikejdbc.update
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
          .orderBy(d.createdAt)
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
        from = offset + 1,
        to = offset + records.length,
        lastPage = (total / limit) + math.min(total % limit, 1),
        total = total,
        data = records
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
    DB.readOnly { implicit s =>
      for {
        id <- checkSome(param.datasetId, "データセットID")
        _ <- Util.checkUuid(id)
        dataset <- searchDatasetById(id)
      } yield {
        AclAddData(
          datasetId = dataset.id,
          datasetName = dataset.name
        )
      }
    }
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
    DB.readOnly { implicit s =>
      for {
        id <- checkSome(param.datasetId, "データセットID")
        _ <- Util.checkUuid(id)
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
    DB.readOnly { implicit s =>
      for {
        datasetId <- checkSome(param.datasetId, "データセットID")
        userId <- checkSome(param.userId, "ユーザーID")
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
        .innerJoin(persistence.Member as m)
        .on(
          sqls.eq(m.groupId, g.id)
            .and
            .eq(g.groupType, persistence.GroupType.Personal)
        )
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
    DB.readOnly { implicit s =>
      for {
        datasetId <- checkSome(param.datasetId, "データセットID")
        groupId <- checkSome(param.groupId, "グループID")
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
   * オプショナルな文字列から値を取得できるか確認する。
   *
   * @param target オプショナルな文字列
   * @param name 対象の名前(見つからなかった時のメッセージに含まれます)
   * @param 文字列
   *        Failure(ServiceException) オプショナルな文字列が未指定の場合
   */
  def checkSome(target: Option[String], name: String): Try[String] = {
    target match {
      case Some(t) => Success(t)
      case None => Failure(new ServiceException(s"${name}の指定がありません。"))
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
    withSQL {
      select(o.result.accessLevel, g.result.*, u.result.*)
        .from(persistence.Ownership as o)
        .innerJoin(persistence.Group as g).on(g.id, o.groupId)
        .leftJoin(persistence.Member as m)
        .on(
          sqls.eq(m.groupId, g.id)
            .and
            .eq(g.groupType, persistence.GroupType.Personal)
        )
        .leftJoin(persistence.User as u).on(u.id, m.userId)
        .where(
          sqls.toAndConditionOpt(
            Some(sqls.eq(o.datasetId, sqls.uuid(datasetId))),
            Some(sqls.ne(o.accessLevel, persistence.UserAccessLevel.Deny)),
            Some(sqls.isNull(g.deletedBy)),
            Some(sqls.isNull(g.deletedAt)),
            sqls.toOrConditionOpt(
              Some(sqls.isNull(u.disabled)),
              Some(sqls.eq(u.disabled, false))
            )
          )
        )
    }.map { rs =>
      val accessLevel = rs.int(o.resultName.accessLevel)
      val groupType = rs.int(g.resultName.groupType)
      groupType match {
        case persistence.GroupType.Public => {
          val groupId = rs.string(g.resultName.id)
          val groupName = rs.string(g.resultName.name)
          val ownerType = OwnerType(persistence.OwnerType.Group)
          SearchResultOwnership(
            id = groupId,
            ownerType = ownerType,
            name = groupName,
            accessLevel = AccessLevel(ownerType, accessLevel)
          )
        }
        case _ => {
          val userId = rs.string(u.resultName.id)
          val userName = rs.string(u.resultName.name)
          val ownerType = OwnerType(persistence.OwnerType.User)
          SearchResultOwnership(
            id = userId,
            ownerType = ownerType,
            name = userName,
            accessLevel = AccessLevel(ownerType, accessLevel)
          )
        }
      }
    }.list.apply()
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
          .in(d.id, ids.map(sqls.uuid))
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
  def applyRollbackLogicalDelete(param: UpdateParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyRollbackLogicalDelete", param))
    DB.localTx { implicit s =>
      for {
        _ <- checkNonEmpty(param.targets)
        _ <- execApplyRollbackLogicalDelete(param.targets)
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
  def execApplyRollbackLogicalDelete(ids: Seq[String])(implicit s: DBSession): Try[Unit] = {
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
          .in(d.id, ids.map(sqls.uuid))
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
   */
  def applyPhysicalDelete(param: UpdateParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyPhysicalDelete", param))
    // TODO 実装
    Failure(new ServiceException("未実装です"))
  }

  /**
   * ユーザーのアクセス権を更新する。
   *
   * @param param 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) データセットIDが未指定の場合
   *        Failure(ServiceException) データセット、ユーザーが存在しない場合
   *        Failure(ServiceException) 指定したユーザーが無効化されている場合
   */
  def applyUpdateAclUser(param: UpdateAclUserParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyUpdateAclUser", param))
    DB.localTx { implicit s =>
      for {
        datasetId <- checkSome(param.datasetId, "データセットID")
        userId <- checkSome(param.userId, "ユーザーID")
        dataset <- searchDatasetById(datasetId)
        user <- searchUserById(userId)
        _ <- updateOwnershipForUser(datasetId, userId, param.accessLevel)
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
        datasetId <- checkSome(param.datasetId, "データセットID")
        userId <- checkSome(param.userId, "ユーザーID")
        dataset <- searchDatasetById(datasetId)
        user <- searchUserById(userId)
        _ <- updateOwnershipForUser(datasetId, userId, AccessLevel.Deny)
      } yield {
        ()
      }
    }
  }

  /**
   * ユーザーのOwnershipを更新する。
   *
   * @param datasetId データセットID
   * @param userId ユーザーID
   * @param accessLevel アクセスレベル
   * @param s DBセッション
   * @return 処理結果
   */
  def updateOwnershipForUser(
    datasetId: String,
    userId: String,
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
          .in(
            oc.id,
            select(o.result.id)
              .from(persistence.Ownership as o)
              .innerJoin(persistence.Group as g).on(g.id, o.groupId)
              .innerJoin(persistence.Member as m).on(m.groupId, g.id)
              .innerJoin(persistence.User as u).on(u.id, m.userId)
              .where
              .eq(o.datasetId, sqls.uuid(datasetId))
              .and
              .eq(g.groupType, persistence.GroupType.Personal)
              .and
              .eq(u.id, sqls.uuid(userId))
              .and
              .eq(u.disabled, false)
          )
      }.update.apply()
    }
  }

  /**
   * グループのアクセス権を更新する。
   *
   * @param param 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) データセットID、グループIDが未指定の場合
   *        Failure(ServiceException) データセット、グループが存在しない場合
   *        Failure(ServiceException) 指定したグループが削除されている場合
   */
  def applyUpdateAclGroup(param: UpdateAclGroupParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyUpdateAclGroup", param))
    DB.localTx { implicit s =>
      for {
        datasetId <- checkSome(param.datasetId, "データセットID")
        groupId <- checkSome(param.groupId, "グループID")
        dataset <- searchDatasetById(datasetId)
        group <- searchGroupById(groupId)
        _ <- updateOwnershipForGroup(datasetId, groupId, param.accessLevel)
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
        datasetId <- checkSome(param.datasetId, "データセットID")
        groupId <- checkSome(param.groupId, "グループID")
        dataset <- searchDatasetById(datasetId)
        group <- searchGroupById(groupId)
        _ <- updateOwnershipForGroup(datasetId, groupId, AccessLevel.Deny)
      } yield {
        ()
      }
    }
  }

  /**
   * グループのOwnershipを更新する。
   *
   * @param datasetId データセットID
   * @param groupId グループID
   * @param accessLevel アクセスレベル
   * @param s DBセッション
   * @return 処理結果
   */
  def updateOwnershipForGroup(
    datasetId: String,
    groupId: String,
    accessLevel: AccessLevel
  )(implicit s: DBSession): Try[Unit] = {
    Try {
      val timestamp = DateTime.now()
      val systemUserId = AppConfig.systemUserId
      val oc = persistence.Ownership.column
      val o = persistence.Ownership.o
      val g = persistence.Group.g
      withSQL {
        update(persistence.Ownership)
          .set(
            oc.accessLevel -> accessLevel.toDBValue,
            oc.updatedAt -> timestamp,
            oc.updatedBy -> sqls.uuid(systemUserId)
          )
          .where
          .in(
            oc.id,
            select(o.result.id)
              .from(persistence.Ownership as o)
              .innerJoin(persistence.Group as g).on(g.id, o.groupId)
              .where
              .eq(o.datasetId, sqls.uuid(datasetId))
              .and
              .eq(g.id, sqls.uuid(groupId))
              .and
              .eq(g.groupType, persistence.GroupType.Public)
              .and
              .isNull(g.deletedAt)
              .and
              .isNull(g.deletedBy)
          )
      }.update.apply()
    }
  }

  /**
   * ユーザーのアクセス権を追加する。
   *
   * @param param 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) データセットIDが未指定の場合
   *        Failure(ServiceException) ユーザー名が未指定の場合
   *        Failure(ServiceException) データセット、ユーザーが存在しない場合
   *        Failure(ServiceException) 既にDeny以外のアクセス権を持っている場合
   *        Failure(ServiceException) 指定したユーザーが無効化されている場合
   */
  def applyAddAclUser(param: AddAclUserParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyAddAclUser", param))
    DB.localTx { implicit s =>
      for {
        datasetId <- checkSome(param.datasetId, "データセットID")
        userName <- checkSome(param.userName, "ユーザー名")
        dataset <- searchDatasetById(datasetId)
        (group, user) <- searchUserGroupByName(userName)
        exists <- checkOwnershipNotExists(datasetId, group.id, "ユーザー")
        _ <- if (exists.isDefined) {
          updateOwnershipForUser(datasetId, user.id, param.accessLevel)
        } else {
          addOwnership(datasetId, group.id, param.accessLevel)
        }
      } yield {
        ()
      }
    }
  }

  /**
   * ユーザー名からユーザーのPersonalグループとユーザーを取得する。
   *
   * @param userName ユーザー名
   * @param s DBセッション
   * @return 取得結果
   *        Failure(ServiceException) 存在しないユーザーの場合
   *        Failure(ServiceException) 指定したユーザーが無効化されている場合
   */
  def searchUserGroupByName(userName: String)(implicit s: DBSession): Try[(persistence.Group, persistence.User)] = {
    val g = persistence.Group.g
    val m = persistence.Member.m
    val u = persistence.User.u
    val result = withSQL {
      select(g.result.*, u.result.*)
        .from(persistence.Group as g)
        .innerJoin(persistence.Member as m).on(m.groupId, g.id)
        .innerJoin(persistence.User as u).on(u.id, m.userId)
        .where
        .eq(g.groupType, persistence.GroupType.Personal)
        .and
        .eq(u.name, userName)
    }.map { rs =>
      val group = persistence.Group(g.resultName)(rs)
      val user = persistence.User(u.resultName)(rs)
      (group, user)
    }.single.apply()
    result match {
      case Some((group, user)) if user.disabled => Failure(new ServiceException("無効なユーザーが指定されました。"))
      case Some((group, user)) => Success((group, user))
      case None => Failure(new ServiceException("存在しないユーザーが指定されました。"))
    }
  }

  /**
   * データセットとグループの間に既にアクセス権があるかを確認する。
   *
   * @param datasetId データセットID
   * @param groupId グループID
   * @param target アクセス権の対象(ユーザー/グループ)
   * @return 確認結果(アクセス権がない場合、アクセス権がありAccessLevelがDenyの場合)
   *        Failure(ServiceException) 既にアクセス権があり、AccessLevelがDenyではない場合
   */
  def checkOwnershipNotExists(
    datasetId: String,
    groupId: String,
    target: String
  )(implicit s: DBSession): Try[Option[AccessLevel]] = {
    val o = persistence.Ownership.o
    val ownership = withSQL {
      select(o.result.accessLevel)
        .from(persistence.Ownership as o)
        .where
        .eq(o.datasetId, sqls.uuid(datasetId))
        .and
        .eq(o.groupId, sqls.uuid(groupId))
    }.map(_.int(o.resultName.accessLevel)).single.apply()
    ownership match {
      case Some(persistence.UserAccessLevel.Deny) => Success(Some(AccessLevel.Deny))
      case Some(_) => Failure(new ServiceException(s"既に登録のある${target}が指定されました。"))
      case None => Success(None)
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
   *        Failure(ServiceException) データセット、グループが存在しない場合
   *        Failure(ServiceException) 既にDeny以外のアクセス権を持っている場合
   *        Failure(ServiceException) 指定したグループが削除されている場合
   */
  def applyAddAclGroup(param: AddAclGroupParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyAddAclGroup", param))
    DB.localTx { implicit s =>
      for {
        datasetId <- checkSome(param.datasetId, "データセットID")
        groupName <- checkSome(param.groupName, "グループ名")
        dataset <- searchDatasetById(datasetId)
        group <- searchGroupByName(groupName)
        exists <- checkOwnershipNotExists(datasetId, group.id, "グループ")
        _ <- if (exists.isDefined) {
          updateOwnershipForGroup(datasetId, group.id, param.accessLevel)
        } else {
          addOwnership(datasetId, group.id, param.accessLevel)
        }
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
}
