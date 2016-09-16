package dsmoq.maintenance.services

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
import dsmoq.maintenance.data.dataset.AclListData
import dsmoq.maintenance.data.dataset.AclUpdateData
import dsmoq.maintenance.data.dataset.SearchResultDataset
import dsmoq.maintenance.data.dataset.SearchResultOwnership
import dsmoq.maintenance.data.dataset.AccessLevel
import dsmoq.maintenance.data.dataset.OwnerType
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
   * データセットを検索する。
   *
   * @param condition 検索条件
   * @return 検索結果
   */
  def search(condition: SearchCondition): SearchResult[SearchResultDataset] = {
    logger.info(LOG_MARKER, Util.createLogMessage("DatasetService", "search", Map("condition" -> condition)))
    val d = persistence.Dataset.d
    val o = persistence.Ownership.o
    val g = persistence.Group.g
    val m = persistence.Member.m
    val u = persistence.User.u
    def createSqlBase(select: SelectSQLBuilder[Unit]): ConditionSQLBuilder[Unit] = {
      select
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
      val eles = withSQL {
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
        to = offset + eles.length,
        total = total,
        data = eles
      )
    }
  }

  /**
   * データセットアクセス権一覧画面を表示するための情報を取得する。
   *
   * @param datasetId データセットID
   * @return 取得結果
   *        Failure(ServiceException) データセットIDが未指定の場合
   *        Failure(ServiceException) UUIDではないIDの場合
   *        Failure(ServiceException) 存在しないIDの場合
   */
  def getAclListData(datasetId: Option[String]): Try[AclListData] = {
    DB.readOnly { implicit s =>
      for {
        id <- checkSome(datasetId, "データセットID")
        _ <- Util.checkUuid(id)
        dataset <- findDataset(id)
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

  def getAclUpdateDataForUser(datasetIdOpt: Option[String], userIdOpt: Option[String]): Try[AclUpdateData] = {
    DB.readOnly { implicit s =>
      for {
        datasetId <- checkSome(datasetIdOpt, "データセットID")
        userId <- checkSome(userIdOpt, "ユーザーID")
        _ <- Util.checkUuids(Seq(datasetId, userId))
        dataset <- findDataset(datasetId)
        // TODO ユーザーの無効化状態確認処理
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
            Some(sqls.isNull(g.deletedBy)),
            Some(sqls.isNull(g.deletedAt)),
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
      case None => Failure(new ServiceException("")) // TODO エラーメッセージ決める
    }
  }

  def checkSome(target: Option[String], name: String): Try[String] = {
    target match {
      case Some(t) => Success(t)
      case None => Failure(new ServiceException(s"${name}の指定がありません。"))
    }
  }

  def findDataset(datasetId: String)(implicit s: DBSession): Try[persistence.Dataset] = {
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
   * 指定されたデータセットIDがDBに存在することを確認する。
   *
   * @param ids データセットID
   * @return 処理結果、存在しないIDが含まれていた場合 Failure(ServiceException)
   */
  def checkDatasetIds(ids: Seq[String])(implicit s: DBSession): Try[Unit] = {
    Try {
      val d = persistence.Dataset.d
      val checks = ids.map { id =>
        val contains = withSQL {
          select(d.result.id)
            .from(persistence.Dataset as d)
            .where
            .eq(d.id, sqls.uuid(id))
        }.map { rs =>
          rs.string(d.resultName.id)
        }.single.apply().isDefined
        (id, contains)
      }
      checks.collect { case (id, false) => id }
    }.flatMap { invalids =>
      if (invalids.isEmpty) {
        Success(())
      } else {
        Failure(new ServiceException("存在しないデータセットが指定されました。"))
      }
    }
  }

  /**
   * 論理削除を適用する。
   *
   * @param ids 論理削除対象のデータセットID
   * @return 処理結果
   *        Failure(ServiceException) 要素が空の場合
   *        Failure(ServiceException) UUIDではないIDが含まれていた場合
   *        Failure(ServiceException) 存在しないIDが含まれていた場合
   */
  def applyLogicalDelete(ids: Seq[String]): Try[Unit] = {
    DB.localTx { implicit s =>
      for {
        _ <- checkNonEmpty(ids)
        _ <- Util.checkUuids(ids)
        _ <- checkDatasetIds(ids)
        _ <- execApplyLogicalDelete(ids)
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
   * @param ids 論理削除解除対象のデータセットID
   * @return 処理結果
   *        Failure(ServiceException) 要素が空の場合
   *        Failure(ServiceException) UUIDではないIDが含まれていた場合
   *        Failure(ServiceException) 存在しないIDが含まれていた場合
   */
  def applyRollbackLogicalDelete(ids: Seq[String]): Try[Unit] = {
    DB.localTx { implicit s =>
      for {
        _ <- checkNonEmpty(ids)
        _ <- Util.checkUuids(ids)
        _ <- checkDatasetIds(ids)
        _ <- execApplyRollbackLogicalDelete(ids)
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
   * @param ids 物理削除対象のデータセットID
   * @return 処理結果
   *        Failure(ServiceException) 要素が空の場合
   *        Failure(ServiceException) UUIDではないIDが含まれていた場合
   *        Failure(ServiceException) 存在しないIDが含まれていた場合
   */
  def applyPhysicalDelete(ids: Seq[String]): Try[Unit] = {
    // TODO 実装
    Failure(new ServiceException("未実装です"))
  }
}
