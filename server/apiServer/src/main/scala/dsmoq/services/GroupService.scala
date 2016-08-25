package dsmoq.services

import java.util.ResourceBundle
import java.util.UUID

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.joda.time.DateTime
import org.scalatra.servlet.FileItem

import dsmoq.AppConf
import dsmoq.ResourceNames
import dsmoq.exceptions.AccessDeniedException
import dsmoq.exceptions.BadRequestException
import dsmoq.exceptions.NotFoundException
import dsmoq.logic.ImageSaveLogic
import dsmoq.logic.StringUtil
import dsmoq.persistence
import dsmoq.persistence.Group
import dsmoq.persistence.GroupAccessLevel
import dsmoq.persistence.GroupMemberRole
import dsmoq.persistence.GroupType
import dsmoq.persistence.Member
import dsmoq.persistence.PostgresqlHelper.PgConditionSQLBuilder
import dsmoq.persistence.PostgresqlHelper.PgSQLSyntaxType
import dsmoq.persistence.PresetType
import dsmoq.services.json.GroupData
import dsmoq.services.json.Image
import dsmoq.services.json.RangeSlice
import dsmoq.services.json.RangeSliceSummary
import scalikejdbc.ConditionSQLBuilder
import scalikejdbc.ConditionSQLBuilder
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SelectSQLBuilder
import scalikejdbc.SelectSQLBuilder
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef
import scalikejdbc.scalikejdbcSQLSyntaxToStringImplicitDef
import scalikejdbc.select
import scalikejdbc.sqls
import scalikejdbc.update
import scalikejdbc.withSQL

/**
 * グループの処理を取り扱うサービスクラス
 * @param resource ResourceBundleのインスタンス
 */
class GroupService(resource: ResourceBundle) {
  private val groupImageDownloadRoot = AppConf.imageDownloadRoot + "groups/"

  /**
   * グループを検索します。
   * @param query
   * @param member
   * @param limit
   * @param offset
   * @param user
   * @return
   */
  def search(
    query: Option[String] = None,
    member: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None,
    user: User
  ): Try[RangeSlice[GroupData.GroupsSummary]] = {
    Try {
      val offset_ = offset.getOrElse(0)
      val limit_ = limit.getOrElse(20)
      DB.readOnly { implicit s =>
        val count = countGroup(query, member)
        val result = if (count > 0) {
          val groups = selectGroup(query, member, limit_, offset_)
          val groupIds = groups.map(_.id)
          val datasetsCount = countDatasets(groupIds)
          val membersCount = countMembers(groupIds)
          val groupImages = getGroupImageIds(groups.map(_.id))
          RangeSlice(
            RangeSliceSummary(count, limit_, offset_),
            groups.map { x =>
              GroupData.GroupsSummary(
                id = x.id,
                name = x.name,
                description = x.description,
                image = groupImages.get(x.id) match {
                  case Some(image) => groupImageDownloadRoot + x.id + "/" + image
                  case None => ""
                },
                members = membersCount.get(x.id).getOrElse(0),
                datasets = datasetsCount.get(x.id).getOrElse(0)
              )
            }
          )
        } else {
          RangeSlice(RangeSliceSummary(0, limit_, offset_), List.empty[GroupData.GroupsSummary])
        }
        result
      }
    }
  }

  private def countGroup(query: Option[String], member: Option[String])(implicit s: DBSession): Int = {
    val g = persistence.Group.g
    withSQL {
      createGroupSelectSql(select.apply(sqls.countDistinct(g.id)), query, member)
    }.map(rs => rs.int(1)).single.apply().get
  }

  private def selectGroup(
    query: Option[String],
    member: Option[String],
    limit: Int,
    offset: Int
  )(implicit s: DBSession): Seq[persistence.Group] = {
    val g = persistence.Group.g
    withSQL {
      createGroupSelectSql(select.apply(sqls.distinct(g.resultAll)), query, member)
        .orderBy(g.updatedAt).desc
        .limit(limit).offset(offset)
    }.map(rs => persistence.Group(g.resultName)(rs)).list.apply()
  }

  private def createGroupSelectSql[A](
    builder: SelectSQLBuilder[A],
    query: Option[String],
    member: Option[String]
  ): ConditionSQLBuilder[A] = {
    val g = persistence.Group.g
    val m = persistence.Member.m
    builder
      .from(persistence.Group as g)
      .map { sql =>
        member match {
          case Some(_) => sql.innerJoin(persistence.Member as m).on(m.groupId, g.id)
          case None => sql
        }
      }
      .where
      .eq(g.groupType, persistence.GroupType.Public).and.isNull(g.deletedAt)
      .map { sql =>
        member match {
          case Some(x) => sql.and.eqUuid(m.userId, x).and.isNull(m.deletedAt)
          case None => sql
        }
      }
      .map { sql =>
        query match {
          case Some(x) => sql.and.likeQuery(g.name, x)
          case None => sql
        }
      }
  }

  /**
   * 指定したIDのグループを取得します。
   * @param groupId
   * @param user
   * @return
   */
  def get(groupId: String, user: User): Try[GroupData.Group] = {
    Try {
      DB.readOnly { implicit s =>
        val group = getGroup(groupId)
        val images = getGroupImage(group.id)
        val primaryImage = getGroupPrimaryImageId(group.id)
        val groupRole = getGroupRole(user, group.id)
        val datasetCount = countDatasets(Seq(groupId)).get(groupId).getOrElse(0)

        GroupData.Group(
          id = group.id,
          name = group.name,
          description = group.description,
          images = images.map(x => Image(
            id = x.id,
            url = groupImageDownloadRoot + group.id + "/" + x.id
          )),
          primaryImage = primaryImage.getOrElse(""),
          isMember = groupRole.isDefined,
          role = groupRole.getOrElse(0),
          providedDatasetCount = datasetCount
        )
      }
    }
  }

  /**
   * 指定したグループに所属するメンバーの一覧を取得します。
   * @param groupId
   * @param limit
   * @param offset
   * @param user
   * @return
   */
  def getGroupMembers(
    groupId: String,
    limit: Option[Int],
    offset: Option[Int],
    user: User
  ): Try[RangeSlice[GroupData.MemberSummary]] = {
    Try {
      val limit_ = limit.getOrElse(20)
      val offset_ = offset.getOrElse(0)

      DB.readOnly { implicit s =>
        getGroup(groupId)
        val count = countMembers(groupId)
        val members = if (count > 0) {
          getMembers(groupId, user.id, offset_, limit_).map { x =>
            GroupData.MemberSummary(
              id = x._1.id,
              name = x._1.name,
              fullname = x._1.fullname,
              organization = x._1.organization,
              description = x._1.description,
              title = x._1.title,
              image = AppConf.imageDownloadRoot + "user/" + x._1.id + "/" + x._1.imageId,
              role = x._2
            )
          }
        } else {
          Seq.empty
        }

        RangeSlice(RangeSliceSummary(count, limit_, offset_), members)
      }
    }
  }

  /**
   * グループを新規作成します。
   * @param name
   * @param description
   * @param user
   * @return
   */
  def createGroup(name: String, description: String, user: User): Try[GroupData.Group] = {
    Try {
      DB.localTx { implicit s =>
        val trimmedName = StringUtil.trimAllSpaces(name)
        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        val groupId = UUID.randomUUID.toString

        if (existsSameNameGroup(trimmedName)) {
          val message = resource.getString(ResourceNames.ALREADY_REGISTERED_GROUP_NAME).format(trimmedName)
          throw new BadRequestException(message)
        }

        val group = persistence.Group.create(
          id = groupId,
          name = trimmedName,
          description = description,
          groupType = persistence.GroupType.Public,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp
        )
        persistence.Member.create(
          id = UUID.randomUUID.toString,
          groupId = groupId,
          userId = myself.id,
          role = persistence.GroupMemberRole.Manager,
          status = 1,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp
        )
        persistence.GroupImage.create(
          id = UUID.randomUUID.toString,
          groupId = groupId,
          imageId = AppConf.defaultGroupImageId,
          isPrimary = true,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp
        )

        GroupData.Group(
          id = group.id,
          name = group.name,
          description = group.description,
          images = Seq(Image(
            id = AppConf.defaultGroupImageId,
            url = groupImageDownloadRoot + groupId + "/" + AppConf.defaultGroupImageId
          )),
          primaryImage = AppConf.defaultGroupImageId,
          isMember = true,
          role = persistence.GroupMemberRole.Manager,
          providedDatasetCount = 0
        )
      }
    }
  }

  def existsSameNameGroup(name: String)(implicit s: DBSession): Boolean = {
    val g = persistence.Group.g
    withSQL {
      select(sqls"1")
        .from(persistence.Group as g)
        .where
        .lowerEq(g.name, name)
        .and
        .eq(g.groupType, GroupType.Public)
        .and
        .isNull(g.deletedAt)
        .limit(1)
    }.map(_ => ()).single.apply().isDefined
  }

  /**
   * グループの基本情報を更新します。
   * @param groupId グループID
   * @param name グループ名
   * @param description グループの説明
   * @param user ログインユーザ情報
   * @return
   *        Success(GroupData.Group) 更新後のグループ情報
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) グループが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザがグループのマネージャでない場合
   *        Failure(BadRequestException) 同名のグループが既に登録されている場合
   */
  def updateGroup(groupId: String, name: String, description: String, user: User): Try[GroupData.Group] = {
    Try {
      CheckUtil.checkNull(groupId, "groupId")
      CheckUtil.checkNull(name, "name")
      CheckUtil.checkNull(description, "description")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        val group = getGroup(groupId)
        val trimmedName = StringUtil.trimAllSpaces(name)
        if (!isGroupAdministrator(user, groupId)) {
          throw new AccessDeniedException(resource.getString(ResourceNames.NO_UPDATE_PERMISSION))
        }
        if (group.name != trimmedName && existsSameNameGroup(trimmedName)) {
          val message = resource.getString(ResourceNames.ALREADY_REGISTERED_GROUP_NAME).format(trimmedName)
          throw new BadRequestException(message)
        }
        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()

        updateGroupDetail(groupId, trimmedName, description, myself, timestamp)

        val images = getGroupImage(groupId)
        val primaryImage = getGroupPrimaryImageId(groupId)
        val datasetCount = countDatasets(List(groupId)).get(groupId).getOrElse(0)

        GroupData.Group(
          id = group.id,
          name = trimmedName,
          description = description,
          images = images.map(x => Image(
            id = x.id,
            url = groupImageDownloadRoot + group.id + "/" + x.id
          )),
          primaryImage = primaryImage.getOrElse(""),
          isMember = true,
          role = getGroupRole(user, group.id).getOrElse(0),
          providedDatasetCount = datasetCount
        )
      }
    }
  }

  private def updateGroupDetail(
    groupId: String,
    name: String,
    description: String,
    myself: persistence.User,
    timestamp: DateTime
  )(implicit s: DBSession): Int = {
    withSQL {
      val g = persistence.Group.column
      update(persistence.Group)
        .set(
          g.name -> name,
          g.description -> description,
          g.updatedBy -> sqls.uuid(myself.id),
          g.updatedAt -> timestamp
        )
        .where
        .eq(g.id, sqls.uuid(groupId))
        .and
        .isNull(g.deletedAt)
    }.update.apply()
  }

  /**
   * 指定したグループに画像を追加します。
   *
   * @param groupId グループID
   * @param images 追加する画像の一覧
   * @param user ユーザ情報
   * @return
   *        Success(GroupData.GroupAddImages) 追加した画像情報
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) グループが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザがグループのマネージャでない場合
   */
  def addImages(groupId: String, images: Seq[FileItem], user: User): Try[GroupData.GroupAddImages] = {
    Try {
      CheckUtil.checkNull(groupId, "groupId")
      CheckUtil.checkNull(images, "images")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        val group = getGroup(groupId)
        if (!isGroupAdministrator(user, groupId)) {
          throw new AccessDeniedException(resource.getString(ResourceNames.NO_UPDATE_PERMISSION))
        }
        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        val primaryImage = getPrimaryImageId(groupId)
        var isFirst = true

        val addedImages = images.filter(x => x.name.nonEmpty).map { i =>
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
          val groupImage = persistence.GroupImage.create(
            id = UUID.randomUUID.toString,
            groupId = groupId,
            imageId = imageId,
            isPrimary = isFirst && primaryImage.isEmpty,
            createdBy = myself.id,
            createdAt = timestamp,
            updatedBy = myself.id,
            updatedAt = timestamp
          )
          isFirst = false
          // write image
          ImageSaveLogic.writeImageFile(imageId, i)
          (image, groupImage.isPrimary)
        }

        GroupData.GroupAddImages(
          images = addedImages.map {
            case (image, isPrimary) => {
              GroupData.GroupGetImage(
                id = image.id,
                name = image.name,
                url = groupImageDownloadRoot + groupId + "/" + image.id,
                isPrimary = isPrimary
              )
            }
          },
          primaryImage = getPrimaryImageId(groupId).getOrElse("")
        )
      }
    }
  }

  /**
   * 指定したグループのユーザロールを設定します。
   * @param groupId グループID
   * @param members 追加するメンバーオブジェクト
   * @param user ログインユーザオブジェクト
   * @return
   *        Success(GroupData.AddMembers) 追加されたメンバー情報
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) グループが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザがグループのマネージャでない場合
   *        Failure(BadRequestException) membersに不正なユーザが指定されている場合
   */
  def addMembers(groupId: String, members: Seq[GroupMember], user: User): Try[GroupData.AddMembers] = {
    Try {
      CheckUtil.checkNull(groupId, "groupId")
      CheckUtil.checkNull(members, "members")
      CheckUtil.checkNull(user, "user")
      val u = persistence.User.u
      val m = persistence.Member.m

      DB.localTx { implicit s =>
        val group = getGroup(groupId)
        if (!isGroupAdministrator(user, groupId)) {
          throw new AccessDeniedException(resource.getString(ResourceNames.NO_UPDATE_PERMISSION))
        }
        // 登録処理（既に登録されているユーザが送られてきた場合は無視する）
        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        val userMap = persistence.User
          .findAllBy(sqls.inUuid(u.id, members.map { _.userId }).and.eq(u.disabled, false))
          .map { x => (x.id, x) }.toMap
        val memberMap = persistence.Member
          .findAllBy(sqls.inUuid(m.userId, members.map { _.userId }).and.eq(m.groupId, sqls.uuid(groupId)))
          .map { x => (x.userId, x) }.toMap
        val updatedMembers = members.map { item =>
          val user = userMap.getOrElse(item.userId, {
            throw new BadRequestException(resource.getString(ResourceNames.DISABLED_USER))
          })
          val updatedMember = if (!memberMap.contains(item.userId)) {
            persistence.Member.create(
              id = UUID.randomUUID.toString,
              groupId = groupId,
              userId = item.userId,
              role = item.role,
              status = 1,
              createdBy = myself.id,
              createdAt = timestamp,
              updatedBy = myself.id,
              updatedAt = timestamp
            )
          } else {
            val member = memberMap(item.userId)
            if (member.deletedAt.isDefined || member.role == GroupMemberRole.Deny) {
              persistence.Member(
                id = member.id,
                groupId = groupId,
                userId = item.userId,
                role = item.role,
                status = 1,
                createdBy = myself.id,
                createdAt = timestamp,
                updatedBy = myself.id,
                updatedAt = timestamp,
                deletedAt = None,
                deletedBy = None
              ).save()
            } else {
              member
            }
          }
          GroupData.MemberSummary(
            id = user.id,
            name = user.name,
            fullname = user.fullname,
            organization = user.organization,
            description = user.description,
            title = user.title,
            image = AppConf.imageDownloadRoot + "user/" + user.id + "/" + user.imageId,
            role = updatedMember.role
          )
        }
        GroupData.AddMembers(updatedMembers)
      }
    }
  }

  /**
   * 指定したグループメンバーのロールレベルを変更します。
   * @param groupId グループID
   * @param userId ユーザID
   * @param role ロール
   * @param user ログインユーザ情報
   * @return
   *        Success(GroupData.MemberSummary) 更新されたメンバー情報
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) グループ、ユーザが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザがグループのマネージャでない場合
   *        Failure(BadRequestException) 更新によってマネージャが0人になる場合
   */
  def updateMemberRole(
    groupId: String,
    userId: String,
    role: Int,
    user: User
  ): Try[GroupData.MemberSummary] = {
    Try {
      CheckUtil.checkNull(groupId, "groupId")
      CheckUtil.checkNull(userId, "userId")
      CheckUtil.checkNull(role, "role")
      CheckUtil.checkNull(user, "user")
      val m = persistence.Member.m
      DB.localTx { implicit s =>
        val ret = for {
          group <- findGroupById(groupId)
          groupUser <- findUserById(userId)
          member <- findMemberById(groupId, userId)
        } yield {
          if (!isGroupAdministrator(user, groupId)) {
            throw new AccessDeniedException(resource.getString(ResourceNames.NO_UPDATE_PERMISSION))
          }
          // 更新によってマネージャが0人になる場合をエラーとしている
          if (getOtherManagerCount(groupId, userId) == 0 && role != GroupMemberRole.Manager) {
            throw new BadRequestException(resource.getString(ResourceNames.NO_MANAGER))
          }
          persistence.Member(
            id = member.id,
            groupId = member.groupId,
            userId = member.userId,
            role = role,
            status = member.status,
            createdBy = member.createdBy,
            createdAt = member.createdAt,
            updatedBy = groupUser.id,
            updatedAt = DateTime.now(),
            deletedBy = None,
            deletedAt = None
          ).save()
          GroupData.MemberSummary(
            id = member.userId,
            name = groupUser.name,
            fullname = groupUser.fullname,
            organization = groupUser.organization,
            description = groupUser.description,
            title = groupUser.title,
            image = AppConf.imageDownloadRoot + "user/" + groupUser.id + "/" + groupUser.imageId,
            role = role
          )
        }
        ret.getOrElse {
          throw new NotFoundException
        }
      }
    }
  }

  def findMemberById(groupId: String, userId: String)(implicit s: DBSession): Option[Member] = {
    val m = persistence.Member.m
    withSQL {
      select(m.resultAll).from(persistence.Member as m)
        .where.eqUuid(m.userId, userId).and.eqUuid(m.groupId, groupId)
    }.map(persistence.Member(m.resultName)).single.apply()
  }

  private def findGroupById(groupId: String)(implicit s: DBSession): Option[Group] = {
    val g = persistence.Group.g
    withSQL {
      select(g.resultAll).from(persistence.Group as g)
        .where.eqUuid(g.id, groupId).and.isNull(g.deletedAt)
    }.map(persistence.Group(g.resultName)).single.apply()
  }

  private def findUserById(userId: String)(implicit s: DBSession): Option[persistence.User] = {
    val u = persistence.User.u
    withSQL {
      select(u.resultAll).from(persistence.User as u)
        .where.eqUuid(u.id, userId).and.eq(u.disabled, false)
    }.map(persistence.User(u.resultName)).single.apply()
  }

  /**
   * 指定したグループメンバーを削除します。
   * @param groupId グループID
   * @param userId ユーザID
   * @param user ログインユーザオブジェクト
   * @return
   *        Success(Unit) グループメンバー削除が成功した場合
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) グループ、またはメンバーが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザがグループのマネージャでない場合
   *        Failure(BadRequestException) 削除によってマネージャが0人になる場合
   */
  def removeMember(groupId: String, userId: String, user: User): Try[Unit] = {
    Try {
      CheckUtil.checkNull(groupId, "groupId")
      CheckUtil.checkNull(userId, "userId")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        val ret = for {
          group <- findGroupById(groupId)
          groupUser <- findUserById(userId)
          member <- findMemberById(groupId, userId)
        } yield {
          if (!isGroupAdministrator(user, groupId)) {
            throw new AccessDeniedException(resource.getString(ResourceNames.NO_UPDATE_PERMISSION))
          }
          // 削除によってマネージャが0人になる場合をエラーとしている
          if (getOtherManagerCount(groupId, userId) == 0) {
            throw new BadRequestException(resource.getString(ResourceNames.NO_MANAGER))
          }
          persistence.Member(
            id = member.id,
            groupId = member.groupId,
            userId = member.userId,
            role = GroupMemberRole.Deny,
            status = member.status,
            createdBy = member.createdBy,
            createdAt = member.createdAt,
            updatedBy = groupUser.id,
            updatedAt = DateTime.now(),
            deletedBy = None,
            deletedAt = None
          ).save()
        }
        if (ret.isEmpty) {
          throw new NotFoundException
        }
      }
    }
  }

  /**
   * 指定したユーザー以外のグループのマネージャの数を取得する。
   *
   * @param groupId グループID
   * @param userId 除外するユーザーID
   * @param session DBセッション
   * @return マネージャの数
   * @throws NullPointerException 引数がnullの場合
   */
  private def getOtherManagerCount(groupId: String, userId: String)(implicit session: DBSession): Int = {
    CheckUtil.checkNull(groupId, "groupId")
    CheckUtil.checkNull(userId, "userId")
    CheckUtil.checkNull(session, "session")
    // テーブルGroupsのエイリアス
    val g = persistence.Group.g
    // テーブルMembersのエイリアス
    val m = persistence.Member.m
    val count = withSQL {
      select(sqls.count(m.id))
        .from(persistence.Member as m)
        .innerJoin(persistence.Group as g).on(sqls.eq(g.id, m.groupId).and.eq(g.groupType, GroupType.Public))
        .where.ne(m.userId, sqls.uuid(userId))
        .and.eq(m.groupId, sqls.uuid(groupId))
        .and.eq(m.role, GroupMemberRole.Manager)
    }.map(rs => rs.int(1)).single.apply().getOrElse(0)
    count
  }

  /**
   * 指定したグループを削除します。
   * @param groupId グループID
   * @param user ログインユーザ情報
   * @return
   *        Success(Unit) 削除が成功した場合
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) グループが見つからない場合
   *        Failure(AccessDeniedException) ログインユーザがグループのマネージャでない場合
   */
  def deleteGroup(groupId: String, user: User): Try[Unit] = {
    Try {
      CheckUtil.checkNull(groupId, "groupId")
      CheckUtil.checkNull(user, "user")
      val result = DB.localTx { implicit s =>
        getGroup(groupId)
        if (!isGroupAdministrator(user, groupId)) {
          throw new AccessDeniedException(resource.getString(ResourceNames.NO_UPDATE_PERMISSION))
        }
        deleteGroupById(groupId, user)
      }
    }
  }

  private def deleteGroupById(groupId: String, user: User)(implicit s: DBSession): Int = {
    val myself = persistence.User.find(user.id).get
    val timestamp = DateTime.now()

    withSQL {
      val g = persistence.Group.column
      update(persistence.Group)
        .set(
          g.deletedBy -> sqls.uuid(myself.id),
          g.deletedAt -> timestamp,
          g.updatedBy -> sqls.uuid(myself.id),
          g.updatedAt -> timestamp
        )
        .where
        .eq(g.id, sqls.uuid(groupId))
        .and
        .isNull(g.deletedAt)
    }.update.apply()
  }

  /**
   * 指定したグループのプライマリ画像を変更します。
   * @param groupId グループID
   * @param imageId 画像ID
   * @param user ログインユーザ情報
   * @return
   *        Success(GroupData.ChangeGroupImage) 変更後の画像ID
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) グループ、または画像が見つからない場合
   *        Failure(AccessDeniedException) ログインユーザがグループのマネージャでない場合
   */
  def changePrimaryImage(groupId: String, imageId: String, user: User): Try[GroupData.ChangeGroupImage] = {
    Try {
      CheckUtil.checkNull(groupId, "groupId")
      CheckUtil.checkNull(imageId, "imageId")
      CheckUtil.checkNull(user, "user")
      DB.localTx { implicit s =>
        getGroup(groupId)
        if (!existsGroupImage(groupId, imageId)) {
          throw new NotFoundException
        }
        if (!isGroupAdministrator(user, groupId)) {
          throw new AccessDeniedException(resource.getString(ResourceNames.NO_UPDATE_PERMISSION))
        }
        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        // 対象のイメージをPrimaryに
        turnGroupImageToPrimary(groupId, imageId, myself, timestamp)
        // 対象以外のイメージをPrimary以外に
        turnOffPrimaryOtherGroupImage(groupId, imageId, myself, timestamp)
        GroupData.ChangeGroupImage(imageId)
      }
    }
  }

  private def turnOffPrimaryOtherGroupImage(
    groupId: String,
    imageId: String,
    myself: persistence.User,
    timestamp: DateTime
  )(implicit s: DBSession): Int = {
    withSQL {
      val gi = persistence.GroupImage.column
      update(persistence.GroupImage)
        .set(
          gi.isPrimary -> false,
          gi.updatedBy -> sqls.uuid(myself.id),
          gi.updatedAt -> timestamp
        )
        .where
        .ne(gi.imageId, sqls.uuid(imageId))
        .and
        .eq(gi.groupId, sqls.uuid(groupId))
        .and
        .isNull(gi.deletedAt)
    }.update.apply()
  }

  private def turnGroupImageToPrimary(
    groupId: String,
    imageId: String,
    myself: persistence.User,
    timestamp: DateTime
  )(implicit s: DBSession): Int = {
    withSQL {
      val gi = persistence.GroupImage.column
      update(persistence.GroupImage)
        .set(
          gi.isPrimary -> true,
          gi.updatedBy -> sqls.uuid(myself.id),
          gi.updatedAt -> timestamp
        )
        .where
        .eq(gi.imageId, sqls.uuid(imageId))
        .and
        .eq(gi.groupId, sqls.uuid(groupId))
        .and
        .isNull(gi.deletedAt)
    }.update.apply()
  }

  /**
   * 指定したグループの画像を削除します。
   * @param groupId グループID
   * @param imageId 画像ID
   * @param user ログインユーザ情報
   * @return
   *        Success(GroupData.GroupDeleteImage) 画像削除後のPrimary画像情報
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) グループ、または画像が見つからない場合
   *        Failure(AccessDeniedException) ログインユーザがグループのマネージャでない場合
   *        Failure(BadRequestException) デフォルトイメージを削除しようとした場合
   */
  def deleteImage(groupId: String, imageId: String, user: User): Try[GroupData.GroupDeleteImage] = {
    Try {
      CheckUtil.checkNull(groupId, "groupId")
      CheckUtil.checkNull(imageId, "imageId")
      CheckUtil.checkNull(user, "user")
      val primaryImage = DB.localTx { implicit s =>
        val group = getGroup(groupId)
        if (!existsGroupImage(groupId, imageId)) {
          throw new NotFoundException
        }
        if (!isGroupAdministrator(user, groupId)) {
          throw new AccessDeniedException(resource.getString(ResourceNames.NO_UPDATE_PERMISSION))
        }
        val cantDeleteImages = Seq(AppConf.defaultGroupImageId)
        if (cantDeleteImages.contains(imageId)) {
          throw new BadRequestException(resource.getString(ResourceNames.CANT_DELETE_DEFAULTIMAGE))
        }

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        deleteGroupImage(groupId, imageId, myself, timestamp)

        getPrimaryImageId(groupId) match {
          case Some(x) => x
          case None =>
            // primaryImageの差し替え
            // primaryImageとなるImageを取得
            val primaryImage = getNextPrimaryGroupImage(groupId)

            primaryImage match {
              case Some((id, primaryImage)) =>
                turnGroupImageToPrimaryById(id, myself, timestamp)
                primaryImage
              case None => ""
            }
        }
      }

      GroupData.GroupDeleteImage(
        primaryImage = primaryImage
      )
    }
  }

  private def turnGroupImageToPrimaryById(
    id: String,
    myself: persistence.User,
    timestamp: DateTime
  )(implicit s: DBSession): Int = {
    val gi = persistence.GroupImage.column
    withSQL {
      update(persistence.GroupImage)
        .set(
          gi.isPrimary -> true,
          gi.updatedBy -> sqls.uuid(myself.id),
          gi.updatedAt -> timestamp
        )
        .where
        .eq(gi.id, sqls.uuid(id))
    }.update.apply()
  }

  private def getNextPrimaryGroupImage(groupId: String)(implicit s: DBSession): Option[(String, String)] = {
    val gi = persistence.GroupImage.gi
    val i = persistence.Image.i
    withSQL {
      select(gi.result.id, i.result.id)
        .from(persistence.Image as i)
        .innerJoin(persistence.GroupImage as gi).on(i.id, gi.imageId)
        .where
        .eq(gi.groupId, sqls.uuid(groupId))
        .and
        .isNull(gi.deletedAt)
        .and
        .isNull(i.deletedAt)
        .orderBy(gi.createdAt).asc
        .limit(1)
    }.map(rs => (rs.string(gi.resultName.id), rs.string(i.resultName.id))).single.apply()
  }

  private def deleteGroupImage(
    groupId: String,
    imageId: String,
    myself: persistence.User,
    timestamp: DateTime
  )(implicit s: DBSession): Int = {
    withSQL {
      val gi = persistence.GroupImage.column
      update(persistence.GroupImage)
        .set(
          gi.deletedBy -> sqls.uuid(myself.id),
          gi.deletedAt -> timestamp,
          gi.isPrimary -> false,
          gi.updatedBy -> sqls.uuid(myself.id),
          gi.updatedAt -> timestamp
        )
        .where
        .eq(gi.groupId, sqls.uuid(groupId))
        .and
        .eq(gi.imageId, sqls.uuid(imageId))
        .and
        .isNull(gi.deletedAt)
    }.update.apply()
  }

  private def getGroup(groupId: String)(implicit s: DBSession): Group = {
    if (!StringUtil.isUUID(groupId)) {
      throw new NotFoundException
    }
    val g = persistence.Group.syntax("g")
    val ret = withSQL {
      select(g.result.*)
        .from(persistence.Group as g)
        .where
        .eq(g.id, sqls.uuid(groupId))
        .and
        .eq(g.groupType, persistence.GroupType.Public)
        .and
        .isNull(g.deletedAt)
    }.map(persistence.Group(g.resultName)).single.apply()
    ret.getOrElse {
      throw new NotFoundException
    }
  }

  private def countDatasets(groups: Seq[String])(implicit s: DBSession): Map[String, Int] = {
    val ds = persistence.Dataset.syntax("ds")
    val o = persistence.Ownership.syntax("o")

    withSQL {
      select(o.groupId, sqls.count(sqls.distinct(ds.id)).append(sqls"count"))
        .from(persistence.Dataset as ds)
        .innerJoin(persistence.Ownership as o).on(o.datasetId, ds.id)
        .where
        .inUuid(o.groupId, Seq.concat(groups, Seq(AppConf.guestGroupId)))
        .and
        .eq(o.accessLevel, GroupAccessLevel.Provider)
        .and
        .isNull(ds.deletedAt)
        .and
        .isNull(o.deletedAt)
        .groupBy(o.groupId)
    }.map(rs => (rs.string(persistence.Ownership.column.groupId), rs.int("count"))).list.apply().toMap
  }

  private def countMembers(groups: Seq[String])(implicit s: DBSession): Map[String, Int] = {
    val m = persistence.Member.syntax("m")
    val u = persistence.User.u
    withSQL {
      select(m.groupId, sqls.count(sqls.distinct(m.id)).append(sqls"count"))
        .from(persistence.Member as m)
        .innerJoin(persistence.User as u).on(sqls.eq(m.userId, u.id).and.eq(u.disabled, false))
        .where
        .inUuid(m.groupId, Seq.concat(groups, Seq(AppConf.guestGroupId)))
        .and
        .not.eq(m.role, GroupMemberRole.Deny)
        .and
        .isNull(m.deletedAt)
        .groupBy(m.groupId)
    }.map(rs => (rs.string(persistence.Member.column.groupId), rs.int("count"))).list.apply().toMap
  }

  private def getGroupImage(groupId: String)(implicit s: DBSession): List[persistence.Image] = {
    val gi = persistence.GroupImage.syntax("gi")
    val i = persistence.Image.syntax("i")
    withSQL {
      select(i.result.*)
        .from(persistence.GroupImage as gi)
        .innerJoin(persistence.Image as i).on(gi.imageId, i.id)
        .where
        .eq(gi.groupId, sqls.uuid(groupId))
        .and
        .isNull(gi.deletedAt)
        .and
        .isNull(i.deletedAt)
    }.map(rs => persistence.Image(i.resultName)(rs)).list.apply()
  }

  private def getGroupImageIds(groupIds: Seq[String])(implicit s: DBSession): Map[String, String] = {
    if (groupIds.isEmpty) {
      return Map.empty
    }
    val gi = persistence.GroupImage.syntax("gi")
    withSQL {
      select(gi.result.*)
        .from(persistence.GroupImage as gi)
        .where
        .inUuid(gi.groupId, groupIds)
        .and
        .eq(gi.isPrimary, true)
        .and
        .isNull(gi.deletedAt)
    }.map(rs => (rs.string(gi.resultName.groupId), rs.string(gi.resultName.imageId))).list.apply().toMap
  }

  private def getGroupPrimaryImageId(groupId: String)(implicit s: DBSession): Option[String] = {
    val gi = persistence.GroupImage.syntax("gi")
    withSQL {
      select(gi.result.imageId)
        .from(persistence.GroupImage as gi)
        .where
        .eq(gi.groupId, sqls.uuid(groupId))
        .and
        .eq(gi.isPrimary, true)
        .and
        .isNull(gi.deletedAt)
    }.map(_.string(gi.resultName.imageId)).single.apply()
  }

  private def countMembers(groupId: String)(implicit s: DBSession): Int = {
    val m = persistence.Member.m
    withSQL {
      createMembersSql(select(sqls.count(m.id)), groupId)
    }.map(rs => rs.int(1)).single.apply().get
  }

  private def getMembers(
    groupId: String,
    userId: String,
    offset: Int,
    limit: Int
  )(implicit s: DBSession): Seq[(persistence.User, Int)] = {
    val m = persistence.Member.m
    val u = persistence.User.u
    withSQL {
      createMembersSql(
        select.apply(
          u.result.*,
          m.role,
          sqls.eqUuid(u.id, userId).and.eq(m.role, GroupMemberRole.Manager).append(sqls"own")
        ),
        groupId
      )
        .orderBy(sqls"own desc", m.role.desc, m.createdAt.desc)
        .offset(offset)
        .limit(limit)
    }.map(rs => (persistence.User(u.resultName)(rs), rs.int(persistence.Member.column.role))).list.apply()
  }

  private def createMembersSql[A](builder: SelectSQLBuilder[A], groupId: String): ConditionSQLBuilder[A] = {
    val m = persistence.Member.m
    val u = persistence.User.u
    val g = persistence.Group.g
    builder
      .from(persistence.User as u)
      .innerJoin(persistence.Member as m).on(sqls.eq(m.userId, u.id).and.isNull(m.deletedAt))
      .innerJoin(persistence.Group as g).on(sqls.eq(g.id, m.groupId).and.isNull(g.deletedAt))
      .where
      .eqUuid(m.groupId, groupId)
      .and
      .not.eq(m.role, GroupMemberRole.Deny)
      .and
      .isNull(g.deletedAt)
      .and
      .isNull(m.deletedAt)
      .and
      .eq(u.disabled, false)
  }

  private def getPrimaryImageId(groupId: String)(implicit s: DBSession): Option[String] = {
    val gi = persistence.GroupImage.syntax("gi")
    val i = persistence.Image.syntax("i")
    withSQL {
      select(i.result.id)
        .from(persistence.Image as i)
        .innerJoin(persistence.GroupImage as gi).on(i.id, gi.imageId)
        .where
        .eq(gi.groupId, sqls.uuid(groupId))
        .and
        .eq(gi.isPrimary, true)
        .and
        .isNull(gi.deletedAt)
        .and
        .isNull(i.deletedAt)
    }.map(rs => rs.string(i.resultName.id)).single.apply()
  }

  private def getGroupRole(user: User, groupId: String)(implicit s: DBSession): Option[Int] = {
    if (user.isGuest) {
      return None
    }
    val m = persistence.Member.syntax("m")
    withSQL {
      select(m.result.*)
        .from(persistence.Member as m)
        .where
        .eq(m.groupId, sqls.uuid(groupId))
        .and
        .eq(m.userId, sqls.uuid(user.id))
        .and
        .isNull(m.deletedAt)
    }.map(_.int(m.resultName.role)).single.apply()
  }

  private def isGroupAdministrator(user: User, groupId: String)(implicit s: DBSession): Boolean = {
    val g = persistence.Group.g
    val m = persistence.Member.m
    withSQL {
      select(sqls"1")
        .from(persistence.Group as g)
        .innerJoin(persistence.Member as m).on(sqls.eq(g.id, m.groupId).and.isNull(m.deletedAt))
        .where
        .eq(g.id, sqls.uuid(groupId))
        .and
        .eq(g.groupType, GroupType.Public)
        .and
        .eq(m.role, GroupMemberRole.Manager)
        .and
        .eq(m.userId, sqls.uuid(user.id))
        .and
        .isNull(g.deletedAt)
        .limit(1)
    }.map(x => true).single.apply().getOrElse(false)
  }

  private def existsGroupImage(groupId: String, imageId: String)(implicit s: DBSession): Boolean = {
    val gi = persistence.GroupImage.syntax("gi")
    val i = persistence.Image.syntax("i")
    withSQL {
      select(gi.result.id)
        .from(persistence.GroupImage as gi)
        .innerJoin(persistence.Image as i).on(i.id, gi.imageId)
        .where
        .eq(gi.groupId, sqls.uuid(groupId))
        .and
        .eq(i.id, sqls.uuid(imageId))
        .and
        .isNull(gi.deletedAt)
        .and
        .isNull(i.deletedAt)
    }.map(rs => rs.string(gi.resultName.id)).single.apply().isDefined
  }

  private def isValidUser(userId: String)(implicit s: DBSession): Boolean = {
    if (!StringUtil.isUUID(userId)) {
      return false
    }
    val u = persistence.User.syntax("u")
    withSQL {
      select(u.result.id)
        .from(persistence.User as u)
        .where
        .eq(u.id, sqls.uuid(userId))
        .and
        .eq(u.disabled, false)
    }.map(rs => rs.string(u.resultName.id)).single.apply().isDefined
  }

  private def isValidGroupMember(groupId: String, memberId: String)(implicit s: DBSession): Boolean = {
    val m = persistence.Member.syntax("m")
    val g = persistence.Group.syntax("g")
    withSQL {
      select(m.result.id)
        .from(persistence.Member as m)
        .innerJoin(persistence.Group as g).on(g.id, m.groupId)
        .where
        .eq(g.id, sqls.uuid(groupId))
        .and
        .eq(m.userId, sqls.uuid(memberId))
        .and
        .isNull(m.deletedAt)
        .and
        .isNull(g.deletedAt)
    }.map(rs => rs.string(m.resultName.id)).single.apply().isDefined
  }

  /**
   * グループの画像一覧を取得する。
   *
   * @param groupId グループID
   * @param limit 検索上限
   * @param offset 検索の開始位置
   * @param user ユーザー情報
   * @return
   *        Success(GroupData.Group) グループが保持する画像情報
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) グループが見つからない場合
   */
  def getImages(
    groupId: String,
    offset: Option[Int],
    limit: Option[Int],
    user: User
  ): Try[RangeSlice[GroupData.GroupGetImage]] = {
    Try {
      CheckUtil.checkNull(groupId, "groupId")
      CheckUtil.checkNull(offset, "offset")
      CheckUtil.checkNull(limit, "limit")
      CheckUtil.checkNull(user, "user")
      DB.readOnly { implicit s =>
        val group = getGroup(groupId)
        val gi = persistence.GroupImage.gi
        val i = persistence.Image.i
        val totalCount = withSQL {
          select(sqls"count(1)")
            .from(persistence.GroupImage as gi)
            .innerJoin(persistence.Image as i).on(gi.imageId, i.id)
            .where
            .eqUuid(gi.groupId, groupId)
            .and
            .isNull(gi.deletedBy)
            .and
            .isNull(gi.deletedAt)
        }.map(rs => rs.int(1)).single.apply()
        val result = withSQL {
          select(i.result.*, gi.result.isPrimary)
            .from(persistence.GroupImage as gi)
            .innerJoin(persistence.Image as i).on(gi.imageId, i.id)
            .where
            .eqUuid(gi.groupId, groupId)
            .and
            .isNull(gi.deletedBy)
            .and
            .isNull(gi.deletedAt)
            .offset(offset.getOrElse(0))
            .limit(limit.getOrElse(20))
        }.map { rs =>
          (
            rs.string(i.resultName.id),
            rs.string(i.resultName.name),
            rs.boolean(gi.resultName.isPrimary)
          )
        }.list.apply().map { x =>
          GroupData.GroupGetImage(
            id = x._1,
            name = x._2,
            url = groupImageDownloadRoot + groupId + "/" + x._1,
            isPrimary = x._3
          )
        }

        RangeSlice(
          RangeSliceSummary(
            total = totalCount.getOrElse(0),
            count = limit.getOrElse(20),
            offset = offset.getOrElse(0)
          ),
          result
        )
      }
    }
  }
}
