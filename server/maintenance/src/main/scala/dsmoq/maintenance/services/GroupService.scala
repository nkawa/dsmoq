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
import dsmoq.maintenance.data.group.SearchCondition
import dsmoq.maintenance.data.group.SearchCondition.GroupType
import dsmoq.maintenance.data.group.MemberAddData
import dsmoq.maintenance.data.group.MemberListData
import dsmoq.maintenance.data.group.MemberUpdateData
import dsmoq.maintenance.data.group.MemberRole
import dsmoq.maintenance.data.group.SearchMemberParameter
import dsmoq.maintenance.data.group.SearchMembersParameter
import dsmoq.maintenance.data.group.SearchCondition
import dsmoq.maintenance.data.group.SearchResultGroup
import dsmoq.maintenance.data.group.SearchResultMember
import dsmoq.maintenance.data.group.UpdateParameter
import dsmoq.maintenance.data.group.AddMemberParameter
import dsmoq.maintenance.data.group.UpdateMemberParameter
import dsmoq.persistence
import dsmoq.persistence.PostgresqlHelper.PgConditionSQLBuilder
import dsmoq.persistence.PostgresqlHelper.PgSQLSyntaxType
import org.scalatra.util.MultiMap
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
 * グループ処理サービス
 */
object GroupService extends LazyLogging {
  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("MAINTENANCE_GROUP_LOG")

  /**
   * サービス名
   */
  val SERVICE_NAME = "GroupService"

  /**
   * 追加・更新時に使用できるユーザーのメンバーロール
   */
  val validRoles = Seq(MemberRole.Member, MemberRole.Manager)

  /**
   * グループを検索する。
   *
   * @param condition 検索条件
   * @return 検索結果
   */
  def search(condition: SearchCondition): SearchResult[SearchResultGroup] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "search", condition))
    val g = persistence.Group.g
    val m = persistence.Member.m
    val u = persistence.User.u
    def createSqlBase(topSelect: SelectSQLBuilder[Unit]): ConditionSQLBuilder[Unit] = {
      topSelect
        .from(persistence.Group as g)
        .where(
          sqls.toAndConditionOpt(
            condition.groupType match {
              case GroupType.NotDeleted => Some(sqls.isNull(g.deletedBy).and.isNull(g.deletedAt))
              case GroupType.Deleted => Some(sqls.isNotNull(g.deletedBy).and.isNotNull(g.deletedAt))
              case _ => None
            },
            if (condition.managerId.isEmpty) {
              None
            } else {
              Some(
                sqls.exists(
                  select
                  .from(persistence.Member as m)
                  .innerJoin(persistence.User as u).on(u.id, m.userId)
                  .where
                  .eq(m.groupId, g.id)
                  .and
                  .eq(m.role, persistence.GroupMemberRole.Manager)
                  .and
                  .eq(u.disabled, false)
                  .and
                  .upperLikeQuery(u.name, condition.managerId)
                  .toSQLSyntax
                )
              )
            },
            if (condition.groupName.isEmpty) {
              None
            } else {
              Some(upperLikeQuery(g.name, condition.groupName))
            },
            Some(sqls.eq(g.groupType, persistence.GroupType.Public))
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
        createSqlBase(select(g.result.*))
          .orderBy(g.createdAt)
          .offset(offset)
          .limit(limit)
      }.map { rs =>
        val group = persistence.Group(g.resultName)(rs)
        val members = searchGroupMembers(group.id)
        SearchResultGroup(
          id = group.id,
          name = group.name,
          description = group.description,
          managers = members.collect { case SearchResultMember(_, name, MemberRole.Manager) => name },
          createdAt = group.createdAt,
          updatedAt = group.updatedAt,
          deletedAt = group.deletedAt
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
   * グループメンバー追加画面を表示するための情報を取得する。
   *
   * @param param 入力パラメータ
   * @return 取得結果
   *        Failure(ServiceException) グループIDが未指定の場合
   *        Failure(ServiceException) 存在しないIDの場合
   */
  def getMemberAddData(param: SearchMembersParameter): Try[MemberAddData] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "getMemberAddData", param))
    val result = DB.readOnly { implicit s =>
      for {
        id <- Util.require(param.groupId, "グループID")
        group <- searchGroupById(id)
      } yield {
        MemberAddData(
          groupId = group.id,
          groupName = group.name
        )
      }
    }
    Util.withErrorLogging(logger, LOG_MARKER, result)
  }

  /**
   * グループメンバー一覧画面を表示するための情報を取得する。
   *
   * @param param 入力パラメータ
   * @return 取得結果
   *        Failure(ServiceException) グループIDが未指定の場合
   *        Failure(ServiceException) 存在しないIDの場合
   */
  def getMemberListData(param: SearchMembersParameter): Try[MemberListData] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "getMemberListData", param))
    val result = DB.readOnly { implicit s =>
      for {
        id <- Util.require(param.groupId, "グループID")
        group <- searchGroupById(id)
      } yield {
        val members = searchGroupMembers(id)
        MemberListData(
          groupId = group.id,
          groupName = group.name,
          members = members
        )
      }
    }
    Util.withErrorLogging(logger, LOG_MARKER, result)
  }

  /**
   * グループメンバー更新画面を表示するための情報を取得する。
   *
   * @param param 入力パラメータ
   * @return 取得結果
   *        Failure(ServiceException) グループID、ユーザーIDが未指定の場合
   *        Failure(ServiceException) グループ、ユーザーが存在しない場合
   *        Failure(ServiceException) 指定したユーザーが無効化されている場合
   */
  def getMemberUpdateData(param: SearchMemberParameter): Try[MemberUpdateData] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "getMemberUpdateData", param))
    val result = DB.readOnly { implicit s =>
      for {
        groupId <- Util.require(param.groupId, "グループID")
        userId <- Util.require(param.userId, "ユーザーID")
        group <- searchGroupById(groupId)
        user <- searchUserById(userId)
        member <- searchMemberData(groupId, userId)
      } yield {
        MemberUpdateData(
          groupId = group.id,
          groupName = group.name,
          member = member
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
   * 対象のユーザーに対するメンバー情報を取得する。
   *
   * @param groupId グループID
   * @param userId ユーザーID
   * @param s DBセッション
   * @return 取得結果
   *        Failure(ServiceException) メンバー情報が取得できなかった場合
   */
  def searchMemberData(groupId: String, userId: String)(implicit s: DBSession): Try[SearchResultMember] = {
    val g = persistence.Group.g
    val m = persistence.Member.m
    val u = persistence.User.u
    val result = withSQL {
      select(m.result.role, u.result.*)
        .from(persistence.Group as g)
        .innerJoin(persistence.Member as m).on(sqls.eq(m.groupId, g.id))
        .innerJoin(persistence.User as u).on(u.id, m.userId)
        .where(
          sqls.toAndConditionOpt(
            Some(sqls.eq(g.id, sqls.uuid(groupId))),
            Some(sqls.eq(g.groupType, persistence.GroupType.Public)),
            Some(sqls.ne(m.role, persistence.GroupMemberRole.Deny)),
            Some(sqls.eq(u.id, sqls.uuid(userId))),
            Some(sqls.eq(u.disabled, false))
          )
        )
    }.map { rs =>
      val role = rs.int(m.resultName.role)
      val userId = rs.string(u.resultName.id)
      val userName = rs.string(u.resultName.name)
      SearchResultMember(
        id = userId,
        name = userName,
        role = MemberRole(role)
      )
    }.single.apply()
    result match {
      case Some(r) => Success(r)
      case None => Failure(new ServiceException("メンバー登録がありません。"))
    }
  }

  /**
   * IDからグループを取得する。
   *
   * @param groupId グループID
   * @param s DBセッション
   * @return 取得結果
   *        Failure(ServiceException) 存在しないIDの場合
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
      case Some(group) => Success(group)
      case None => Failure(new ServiceException("存在しないグループが指定されました。"))
    }
  }

  /**
   * グループを検索する。
   *
   * @param condition 検索条件
   * @return 検索結果
   */
  def upperLikeQuery(column: SQLSyntax, value: String): SQLSyntax = {
    sqls.upperLikeQuery(column, value)
  }

  /**
   * グループの持つメンバーを取得する。
   *
   * @param groupId グループID
   * @param s DBセッション
   * @return 取得結果
   */
  def searchGroupMembers(groupId: String)(implicit s: DBSession): Seq[SearchResultMember] = {
    val o = persistence.Ownership.o
    val g = persistence.Group.g
    val m = persistence.Member.m
    val u = persistence.User.u
    withSQL {
      select(m.result.role, u.result.*)
        .from(persistence.Group as g)
        .innerJoin(persistence.Member as m).on(sqls.eq(m.groupId, g.id))
        .innerJoin(persistence.User as u).on(u.id, m.userId)
        .where(
          sqls.toAndConditionOpt(
            Some(sqls.eq(g.id, sqls.uuid(groupId))),
            Some(sqls.eq(g.groupType, persistence.GroupType.Public)),
            Some(sqls.ne(m.role, persistence.GroupMemberRole.Deny)),
            Some(sqls.eq(u.disabled, false))
          )
        )
    }.map { rs =>
      val role = rs.int(m.resultName.role)
      val userId = rs.string(u.resultName.id)
      val userName = rs.string(u.resultName.name)
      SearchResultMember(
        id = userId,
        name = userName,
        role = MemberRole(role)
      )
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
      Failure(new ServiceException("グループが選択されていません。"))
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
   * グループに論理削除を適用する。
   *
   * @param ids 論理削除対象のグループID
   * @param s DBセッション
   * @return 処理結果
   */
  def execApplyLogicalDelete(ids: Seq[String])(implicit s: DBSession): Try[Unit] = {
    Try {
      val timestamp = DateTime.now()
      val systemUserId = AppConfig.systemUserId
      val g = persistence.Group.column
      withSQL {
        update(persistence.Group)
          .set(
            g.deletedAt -> timestamp,
            g.deletedBy -> sqls.uuid(systemUserId),
            g.updatedAt -> timestamp,
            g.updatedBy -> sqls.uuid(systemUserId)
          )
          .where
          .inUuid(g.id, ids)
          .and
          .isNull(g.deletedAt)
          .and
          .isNull(g.deletedBy)
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
   * グループに論理削除解除を適用する。
   *
   * @param ids 論理削除解除対象のグループID
   * @param s DBセッション
   * @return 処理結果
   */
  def execApplyCancelLogicalDelete(ids: Seq[String])(implicit s: DBSession): Try[Unit] = {
    Try {
      val timestamp = DateTime.now()
      val systemUserId = AppConfig.systemUserId
      val g = persistence.Group.column
      withSQL {
        update(persistence.Group)
          .set(
            g.deletedAt -> None,
            g.deletedBy -> None,
            g.updatedAt -> timestamp,
            g.updatedBy -> sqls.uuid(systemUserId)
          )
          .where
          .inUuid(g.id, ids)
          .and
          .isNotNull(g.deletedAt)
          .and
          .isNotNull(g.deletedBy)
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
   * メンバー追加・更新時に適切なメンバーロールかを確認する。
   *
   * @param role チェック対象のメンバーロール
   * @param 確認結果
   *        Failure(ServiceException) チェック対象が有効なロールに含まれなかった場合
   */
  def checkRole(role: MemberRole): Try[Unit] = {
    if (validRoles.contains(role)) {
      Success(())
    } else {
      Failure(new ServiceException("無効なロールが指定されました。"))
    }
  }

  /**
   * メンバーを更新する。
   *
   * @param param 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) グループIDが未指定の場合
   *        Failure(ServiceException) グループ、ユーザーが存在しない場合
   *        Failure(ServiceException) 指定したユーザーが無効化されている場合
   */
  def applyUpdateMember(param: UpdateMemberParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyUpdateMember", param))
    DB.localTx { implicit s =>
      for {
        groupId <- Util.require(param.groupId, "グループID")
        userId <- Util.require(param.userId, "ユーザーID")
        _ <- checkRole(param.role)
        _ <- searchGroupById(groupId)
        _ <- searchUserById(userId)
        member <- searchMember(groupId, userId)
        notDenyMember <- requireNotDenyMember(member)
        _ <- updateMember(notDenyMember.id, param.role)
      } yield {
        ()
      }
    }
  }

  /**
   * メンバーを削除する。
   *
   * @param param 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) グループIDが未指定の場合
   *        Failure(ServiceException) グループ、ユーザーが存在しない場合
   *        Failure(ServiceException) 指定したユーザーが無効化されている場合
   */
  def applyDeleteMember(param: UpdateMemberParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyDeleteMember", param))
    DB.localTx { implicit s =>
      for {
        groupId <- Util.require(param.groupId, "グループID")
        userId <- Util.require(param.userId, "ユーザーID")
        _ <- searchGroupById(groupId)
        _ <- searchUserById(userId)
        member <- searchMember(groupId, userId)
        notDenyMember <- requireNotDenyMember(member)
        _ <- updateMember(notDenyMember.id, MemberRole.Deny)
      } yield {
        ()
      }
    }
  }

  /**
   * Memberを更新する。
   *
   * @param memberId メンバーID
   * @param role メンバーロール
   * @param s DBセッション
   * @return 処理結果
   */
  def updateMember(
    memberId: String,
    role: MemberRole
  )(implicit s: DBSession): Try[Unit] = {
    Try {
      val timestamp = DateTime.now()
      val systemUserId = AppConfig.systemUserId
      val mc = persistence.Member.column
      val g = persistence.Group.g
      val m = persistence.Member.m
      val u = persistence.User.u
      withSQL {
        update(persistence.Member)
          .set(
            mc.role -> role.toDBValue,
            mc.updatedAt -> timestamp,
            mc.updatedBy -> sqls.uuid(systemUserId)
          )
          .where
          .eq(mc.id, sqls.uuid(memberId))
      }.update.apply()
    }
  }

  /**
   * メンバーを追加する。
   *
   * @param param 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) グループIDが未指定の場合
   *        Failure(ServiceException) ユーザー名が未指定の場合
   *        Failure(ServiceException) グループ、ユーザーが存在しない場合
   *        Failure(ServiceException) 既にDeny以外のアクセス権を持っている場合
   *        Failure(ServiceException) 指定したユーザーが無効化されている場合
   */
  def applyAddMember(param: AddMemberParameter): Try[Unit] = {
    logger.info(LOG_MARKER, Util.formatLogMessage(SERVICE_NAME, "applyAddMember", param))
    DB.localTx { implicit s =>
      for {
        groupId <- Util.require(param.groupId, "グループID")
        userName <- Util.require(param.userName, "ユーザー名")
        _ <- checkRole(param.role)
        group <- searchGroupById(groupId)
        user <- searchUserByName(userName)
        member <- searchMember(groupId, user.id)
        _ <- checkMemberNotExistsOrDeny(member)
        _ <- upsertMember(groupId, user.id, member, param.role)
      } yield {
        ()
      }
    }
  }

  /**
   * MemberをUpsertする。
   *
   * @param groupId グループID
   * @param userId ユーザーID
   * @param member オプショナルなメンバー
   * @param role メンバーロール
   * @param s DBセッション
   * @return 処理結果
   */
  def upsertMember(
    groupId: String,
    userId: String,
    member: Option[persistence.Member],
    role: MemberRole
  )(implicit s: DBSession): Try[Unit] = {
    member match {
      case Some(m) => updateMember(m.id, role)
      case None => addMember(groupId, userId, role)
    }
  }

  /**
   * ユーザー名からユーザーを取得する。
   *
   * @param userName ユーザー名
   * @param s DBセッション
   * @return 取得結果
   *        Failure(ServiceException) 存在しないユーザーの場合
   *        Failure(ServiceException) 指定したユーザーが無効化されている場合
   */
  def searchUserByName(userName: String)(implicit s: DBSession): Try[persistence.User] = {
    val u = persistence.User.u
    val result = withSQL {
      select(u.result.*)
        .from(persistence.User as u)
        .where
        .eq(u.name, userName)
    }.map(persistence.User(u.resultName)).single.apply()
    result match {
      case Some(user) if user.disabled => Failure(new ServiceException("無効なユーザーが指定されました。"))
      case Some(user) => Success(user)
      case None => Failure(new ServiceException("存在しないユーザーが指定されました。"))
    }
  }

  /**
   * グループとユーザー間のメンバーを取得する。
   *
   * @param groupId グループID
   * @param userId ユーザーID
   * @return 取得結果
   */
  def searchMember(
    groupId: String,
    userId: String
  )(implicit s: DBSession): Try[Option[persistence.Member]] = {
    Try {
      val m = persistence.Member.m
      withSQL {
        select
          .from(persistence.Member as m)
          .where
          .eq(m.groupId, sqls.uuid(groupId))
          .and
          .eq(m.userId, sqls.uuid(userId))
      }.map(persistence.Member(m.resultName)).single.apply()
    }
  }

  /**
   * MemberRoleがDeny以外のメンバーを取得する。
   *
   * @param ownership オプショナルなメンバー
   * @return 取得結果
   *        Failure(ServiceException) MemberRoleがDenyか、メンバーがない場合
   */
  def requireNotDenyMember(member: Option[persistence.Member]): Try[persistence.Member] = {
    member match {
      case Some(m) if m.role != persistence.GroupMemberRole.Deny => Success(m)
      case _ => Failure(new ServiceException(s"まだメンバーの登録がありません。"))
    }
  }

  /**
   * メンバー関係が存在しないか、RoleがDenyであるかを確認する。
   *
   * @param member オプショナルなメンバー
   * @return 確認結果(メンバー関係がない場合、RoleがDenyの場合)
   *        Failure(ServiceException) メンバー関係があり、MemberRoleがDenyではない場合
   */
  def checkMemberNotExistsOrDeny(
    member: Option[persistence.Member]
  )(implicit s: DBSession): Try[Unit] = {
    member match {
      case Some(m) if m.role != persistence.GroupMemberRole.Deny =>
        Failure(new ServiceException(s"既に登録のあるユーザーが指定されました。"))
      case _ => Success(())
    }
  }

  /**
   * グループとメンバー間にメンバー関係を追加する。
   *
   * @param groupId グループID
   * @param userId ユーザーID
   * @param role メンバーロール
   * @return 処理結果
   */
  def addMember(
    groupId: String,
    userId: String,
    role: MemberRole
  )(implicit s: DBSession): Try[Unit] = {
    Try {
      val id = UUID.randomUUID.toString
      val timestamp = DateTime.now()
      val systemUserId = AppConfig.systemUserId
      persistence.Member.create(
        id = id,
        groupId = groupId,
        userId = userId,
        role = role.toDBValue,
        status = 1,
        createdBy = systemUserId,
        createdAt = timestamp,
        updatedBy = systemUserId,
        updatedAt = timestamp
      )
    }
  }

  /**
   * POST /group/applyの更新操作を行う。
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
   * POST /group/member/update/applyの更新操作を行う。
   *
   * @param params 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) 存在しない操作の場合
   */
  def applyChangeForMemberUpdate(params: Map[String, String]): Try[Unit] = {
    val param = UpdateMemberParameter.fromMap(params)
    val result = params.get("update") match {
      case Some("update") => applyUpdateMember(param)
      case Some("delete") => applyDeleteMember(param)
      case _ => Failure(new ServiceException("無効な操作です。"))
    }
    Util.withErrorLogging(logger, LOG_MARKER, result)
  }

  /**
   * POST /group/member/add/applyの更新操作を行う。
   *
   * @param params 入力パラメータ
   * @return 処理結果
   *        Failure(ServiceException) 存在しない操作の場合
   */
  def applyChangeForMemberAdd(params: Map[String, String]): Try[Unit] = {
    val param = AddMemberParameter.fromMap(params)
    val result = params.get("update") match {
      case Some("add") => applyAddMember(param)
      case _ => Failure(new ServiceException("無効な操作です。"))
    }
    Util.withErrorLogging(logger, LOG_MARKER, result)
  }
}
