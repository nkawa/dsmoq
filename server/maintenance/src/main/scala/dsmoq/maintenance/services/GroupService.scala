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
        from = offset + 1,
        to = offset + records.length,
        lastPage = (total / limit) + math.min(total % limit, 1),
        total = total,
        data = records
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
    DB.readOnly { implicit s =>
      for {
        id <- checkSome(param.groupId, "グループID")
        _ <- Util.checkUuid(id)
        group <- searchGroupById(id)
      } yield {
        MemberAddData(
          groupId = group.id,
          groupName = group.name
        )
      }
    }
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
    DB.readOnly { implicit s =>
      for {
        id <- checkSome(param.groupId, "グループID")
        _ <- Util.checkUuid(id)
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
    DB.readOnly { implicit s =>
      for {
        groupId <- checkSome(param.groupId, "グループID")
        userId <- checkSome(param.userId, "ユーザーID")
        group <- searchGroupById(groupId)
        user <- searchUserById(userId)
        member <- searchMember(groupId, userId)
      } yield {
        MemberUpdateData(
          groupId = group.id,
          groupName = group.name,
          member = member
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
   * 対象のユーザーに対するメンバー情報を取得する。
   *
   * @param groupId グループID
   * @param userId ユーザーID
   * @param s DBセッション
   * @return 取得結果
   *        Failure(ServiceException) メンバー情報が取得できなかった場合
   */
  def searchMember(groupId: String, userId: String)(implicit s: DBSession): Try[SearchResultMember] = {
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
          .in(g.id, ids.map(sqls.uuid))
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
   * グループに論理削除解除を適用する。
   *
   * @param ids 論理削除解除対象のグループID
   * @param s DBセッション
   * @return 処理結果
   */
  def execApplyRollbackLogicalDelete(ids: Seq[String])(implicit s: DBSession): Try[Unit] = {
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
          .in(g.id, ids.map(sqls.uuid))
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
        groupId <- checkSome(param.groupId, "グループID")
        userId <- checkSome(param.userId, "ユーザーID")
        group <- searchGroupById(groupId)
        user <- searchUserById(userId)
        _ <- updateMember(groupId, userId, param.role)
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
        groupId <- checkSome(param.groupId, "グループID")
        userId <- checkSome(param.userId, "ユーザーID")
        dataset <- searchGroupById(groupId)
        user <- searchUserById(userId)
        _ <- updateMember(groupId, userId, MemberRole.Deny)
      } yield {
        ()
      }
    }
  }

  /**
   * ユーザーのMemberを更新する。
   *
   * @param groupId グループID
   * @param userId ユーザーID
   * @param role メンバーロール
   * @param s DBセッション
   * @return 処理結果
   */
  def updateMember(
    groupId: String,
    userId: String,
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
          .in(
            mc.id,
            select(m.result.id)
              .from(persistence.Group as g)
              .innerJoin(persistence.Member as m).on(m.groupId, g.id)
              .innerJoin(persistence.User as u).on(u.id, m.userId)
              .where
              .eq(g.id, sqls.uuid(groupId))
              .and
              .eq(g.groupType, persistence.GroupType.Public)
              .and
              .eq(u.id, sqls.uuid(userId))
              .and
              .eq(u.disabled, false)
          )
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
        groupId <- checkSome(param.groupId, "グループID")
        userName <- checkSome(param.userName, "ユーザー名")
        group <- searchGroupById(groupId)
        user <- searchUserByName(userName)
        exists <- checkMemberNotExists(groupId, user.id)
        _ <- if (exists.isDefined) {
          updateMember(groupId, user.id, param.role)
        } else {
          addMember(groupId, user.id, param.role)
        }
      } yield {
        ()
      }
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
   * グループとユーザー間に既にメンバー関係があるかを確認する。
   *
   * @param groupId グループID
   * @param userId ユーザーID
   * @return 確認結果(メンバー関係がない場合、メンバー関係がありMemberRoleがDenyの場合)
   *        Failure(ServiceException) 既にメンバー関係があり、MemberRoleがDenyではない場合
   */
  def checkMemberNotExists(
    groupId: String,
    userId: String
  )(implicit s: DBSession): Try[Option[MemberRole]] = {
    val m = persistence.Member.m
    val member = withSQL {
      select(m.result.role)
        .from(persistence.Member as m)
        .where
        .eq(m.groupId, sqls.uuid(groupId))
        .and
        .eq(m.userId, sqls.uuid(userId))
    }.map(_.int(m.resultName.role)).single.apply()
    member match {
      case Some(persistence.UserAccessLevel.Deny) => Success(Some(MemberRole.Deny))
      case Some(_) => Failure(new ServiceException(s"既に登録のあるユーザーが指定されました。"))
      case None => Success(None)
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
        status = 1, // TODO この値の意味を調べて、定数化する
        createdBy = systemUserId,
        createdAt = timestamp,
        updatedBy = systemUserId,
        updatedAt = timestamp
      )
    }
  }
}
