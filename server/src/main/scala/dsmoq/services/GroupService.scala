package dsmoq.services

import java.util.UUID

import dsmoq.exceptions._
import dsmoq.logic.{ImageSaveLogic, StringUtil}
import dsmoq.persistence.{GroupAccessLevel, GroupMemberRole, GroupType, PresetType}
import dsmoq.services.json.{Image, RangeSlice, RangeSliceSummary, _}
import dsmoq.{AppConf, persistence}
import org.joda.time.DateTime
import org.scalatra.servlet.FileItem
import scalikejdbc._
import dsmoq.persistence.PostgresqlHelper._

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

object GroupService {
  /**
   * グループを検索します。
   * @param query
   * @param member
   * @param limit
   * @param offset
   * @param user
   * @return
   */
  def search(query: Option[String] = None,
             member: Option[String] = None,
             limit: Option[Int] = None,
             offset: Option[Int] = None,
             user: User): Try[RangeSlice[GroupData.GroupsSummary]] = {
    try {
      val offset_ = offset.getOrElse(0)
      val limit_ = limit.getOrElse(20)

      DB readOnly { implicit s =>
        val count = countGroup(query, member)

        val result = if (count > 0) {
          val groups = selectGroup(query, member, limit_, offset_)

          val groupIds = groups.map(_.id)
          val datasetsCount = countDatasets(groupIds)
          val membersCount = countMembers(groupIds)
          val groupImages = getGroupImageIds(groups.map(_.id))

          RangeSlice(RangeSliceSummary(count, limit_, offset_), groups.map(x => {
            GroupData.GroupsSummary(
              id = x.id,
              name = x.name,
              description = x.description,
              image = groupImages.get(x.id) match {
                case Some(x) => AppConf.imageDownloadRoot + x
                case None => ""
              },
              members = membersCount.get(x.id).getOrElse(0),
              datasets = datasetsCount.get(x.id).getOrElse(0)
            )
          }))
        } else {
          RangeSlice(RangeSliceSummary(0, limit_, offset_), List.empty[GroupData.GroupsSummary])
        }

        Success(result)
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  private def countGroup(query: Option[String], member: Option[String])(implicit s: DBSession): Int = {
    val g = persistence.Group.g
    withSQL {
      createGroupSelectSql(select.apply(sqls.countDistinct(g.id)), query, member)
    }.map(rs => rs.int(1)).single().apply.get
  }

  private def selectGroup(query: Option[String], member: Option[String], limit: Int, offset: Int)(implicit s: DBSession) = {
    val g = persistence.Group.g
    withSQL {
      createGroupSelectSql(select.apply(sqls.distinct(g.resultAll)), query, member)
        .orderBy(g.updatedAt).desc
        .limit(limit).offset(offset)
    }.map(rs => persistence.Group(g.resultName)(rs)).list().apply
  }

  private def createGroupSelectSql[A](builder: SelectSQLBuilder[A], query: Option[String], member: Option[String]) = {
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
            case Some(x) => sql.and.like(g.name, "%" + x + "%")
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
    try {
      DB readOnly { implicit s =>
        val group = getGroup(groupId)
        val images = getGroupImage(group.id)
        val primaryImage = getGroupPrimaryImageId(group.id)
        val groupRole = getGroupRole(user, group.id)

        Success(GroupData.Group(
          id = group.id,
          name = group.name,
          description = group.description,
          images = images.map(x => Image(
            id = x.id,
            url = AppConf.imageDownloadRoot + x.id
          )),
          primaryImage = primaryImage.getOrElse(""),
          isMember = groupRole match {
            case Some(x) => true
            case None => false
          },
          role = groupRole.getOrElse(0)
        ))
      }
    } catch {
      case e: Throwable => Failure(e)
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
  def getGroupMembers(groupId: String, limit: Option[Int], offset: Option[Int], user: User) = {
    try {
      val limit_ = limit.getOrElse(20)
      val offset_ = offset.getOrElse(0)

      DB readOnly { implicit s =>
        val count = countMembers(groupId)
        val members = if (count > 0) {
          getMembers(groupId, user.id, offset_, limit_).map{x =>
            GroupData.MemberSummary(
              id = x._1.id,
              name = x._1.name,
              fullname = x._1.fullname,
              organization = x._1.organization,
              description = x._1.description,
              title = x._1.title,
              image = AppConf.imageDownloadRoot + x._1.imageId,
              role = x._2
            )
          }
        } else {
          List.empty
        }

        Success(RangeSlice(RangeSliceSummary(count, limit_, offset_), members))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  /**
   * グループを新規作成します。
   * @param name
   * @param description
   * @param user
   * @return
   */
  def createGroup(name: String, description: String, user: User) = {
    try {
      DB localTx { implicit s =>
        val name_ = StringUtil.trimAllSpaces(name)

        // input validation
        val errors = mutable.LinkedHashMap.empty[String, String]

        if (name_.isEmpty) {
          errors.put("name", "name is empty")
        } else if (existsSameNameGroup(name_)) {
          errors.put("name", "same name")
        }

        if (errors.nonEmpty) {
          throw new InputValidationException(errors)
        }

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        val groupId = UUID.randomUUID.toString

        val group = persistence.Group.create(
          id = groupId,
          name = name_,
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

        Success(GroupData.Group(
          id = group.id,
          name = group.name,
          description = group.description,
          images = Seq(Image(
            id = AppConf.defaultGroupImageId,
            url = AppConf.imageDownloadRoot + AppConf.defaultGroupImageId
          )),
          primaryImage = AppConf.defaultGroupImageId,
          isMember = true,
          role = persistence.GroupMemberRole.Manager
        ))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  private def existsSameNameGroup(name: String)(implicit s: DBSession): Boolean = {
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
    }.map(_ => Unit).single().apply.isDefined
  }

  /**
   * グループの基本情報を更新します。
   * @param groupId
   * @param name
   * @param description
   * @param user
   * @return
   */
  def updateGroup(groupId: String, name: String, description: String, user: User) = {
    try {
      DB localTx { implicit s =>
        val group = getGroup(groupId)
        val name_ = StringUtil.trimAllSpaces(name)

        // input validation
        val errors = mutable.LinkedHashMap.empty[String, String]

        if (!isGroupAdministrator(user, groupId)) throw new NotAuthorizedException

        if (name_.isEmpty) {
          errors.put("name", "name is empty")
        } else if (group.name != name_ && existsSameNameGroup(name_)) {
          errors.put("name", "same name")
        }

        if (errors.nonEmpty) {
          throw new InputValidationException(errors)
        }

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()

        withSQL {
          val g = persistence.Group.column
          update(persistence.Group)
            .set(g.name -> name_, g.description -> description,
                 g.updatedBy -> sqls.uuid(myself.id), g.updatedAt -> timestamp)
            .where
            .eq(g.id, sqls.uuid(groupId))
            .and
            .isNull(g.deletedAt)
        }.update().apply

        val images = getGroupImage(groupId)
        val primaryImage = getGroupPrimaryImageId(groupId)

        Success(GroupData.Group(
          id = group.id,
          name = name_,
          description = description,
          images = images.map(x => Image(
            id = x.id,
            url = AppConf.imageDownloadRoot + x.id
          )),
          primaryImage = primaryImage.getOrElse(""),
          isMember = true,
          role = getGroupRole(user, group.id).getOrElse(0)
        ))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  /**
   * 指定したグループに画像を追加します。
   * @param groupId
   * @param images
   * @param user
   * @return
   */
  def addImages(groupId: String, images: Seq[FileItem], user: User) = {
    try {
      DB localTx { implicit s =>
        val group = getGroup(groupId)
        if (!isGroupAdministrator(user, groupId)) throw new NotAuthorizedException

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        val primaryImage = getPrimaryImageId(groupId)
        var isFirst = true

        val addedImages = images.filter(x => x.name.nonEmpty).map(i => {
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

        Success(GroupData.GroupAddImages(
          images = addedImages.map(x => Image(id = x.id, url = AppConf.imageDownloadRoot + x.id)),
          primaryImage = getPrimaryImageId(groupId).getOrElse("")
        ))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  /**
   * 指定したグループのユーザロールを設定します。
   * @param groupId
   * @param roles
   * @param user
   * @return
   */
  def addMembers(groupId: String, roles: Seq[GroupMember], user: User) = {
    try {
      val u = persistence.User.u
      val m = persistence.Member.m

      DB localTx { implicit s =>
        // input parameter check
        val errors = mutable.MutableList.empty[(String, String)]
        roles.foreach {x =>
          if (x.userId.isEmpty) {
            errors += ("id" -> "ID is empty")
          } else if (!List(GroupMemberRole.Deny, GroupMemberRole.Member, GroupMemberRole.Manager).contains(x.role)) {
            errors += ("role" -> "role is empty")
          }
        }

        if (errors.nonEmpty) {
          throw new InputValidationException(errors)
        }

        val group = getGroup(groupId)
        if (!isGroupAdministrator(user, groupId)) throw new NotAuthorizedException

        // 登録処理（既に登録されているユーザが送られてきた場合は無視する）
        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        val userSet = persistence.User
                        .findAllBy(sqls.inUuid(u.id, roles.map{_.userId}).and.isNull(u.deletedAt))
                        .map{ _.id }.toSet
        val memberMap = persistence.Member
                          .findAllBy(sqls.inUuid(m.userId, roles.map{_.userId}).and.eq(m.groupId, sqls.uuid(groupId)))
                          .map{ x => (x.userId, x) }.toMap
        roles.filter{ x => userSet.contains(x.userId) }.foreach {item =>
          if (!memberMap.contains(item.userId)) {
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
            }
          }
        }
        Success(Unit)
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  /**
   * 指定したグループメンバーのロールレベルを変更します。
   * @param groupId
   * @param userId
   * @param role
   * @param user
   */
  def updateMemberRole(groupId: String, userId: String, role: Int, user: User) = {
    try {
      if (role != GroupMemberRole.Deny && role != GroupMemberRole.Member && role != GroupMemberRole.Manager) {
        throw new InputValidationException(Map("role" -> "invalid role"))
      }

      val g = persistence.Group.g
      val u = persistence.User.u
      val m = persistence.Member.m

      DB localTx { implicit s =>
        (for {
          group <- withSQL {
            select(g.resultAll).from(persistence.Group as g)
              .where.eqUuid(g.id, groupId).and.isNull(g.deletedAt)
          }.map(persistence.Group(g.resultName)).single.apply

          user <- withSQL {
            select(u.resultAll).from(persistence.User as u)
              .where.eqUuid(u.id, userId).and.isNull(u.deletedAt)
          }.map(persistence.User(u.resultName)).single.apply

          member <- withSQL {
            select(m.resultAll).from(persistence.Member as m)
              .where.eqUuid(m.userId, userId).and.eqUuid(m.groupId, groupId)
          }.map(persistence.Member(m.resultName)).single.apply
        } yield {
          persistence.Member(
            id = member.id,
            groupId = member.groupId,
            userId = member.userId,
            role = role,
            status = member.status,
            createdBy = member.createdBy,
            createdAt = member.createdAt,
            updatedBy = user.id,
            updatedAt = DateTime.now(),
            deletedBy = None,
            deletedAt = None
          ).save()
        }) match {
          case Some(_) => Success(Unit)
          case None => Failure(new NotFoundException())
        }
      }
    } catch {
      case e: Throwable => Failure(e)
    }  }

  /**
   * 指定したグループメンバーを削除します。
   * @param groupId
   * @param userId
   * @param user
   */
  def removeMember(groupId: String, userId: String, user: User) = {
    try {
      val g = persistence.Group.g
      val u = persistence.User.u
      val m = persistence.Member.m

      DB localTx { implicit s =>
        (for {
          group <- withSQL {
            select(g.resultAll).from(persistence.Group as g)
              .where.eqUuid(g.id, groupId).and.isNull(g.deletedAt)
          }.map(persistence.Group(g.resultName)).single.apply

          user <- withSQL {
            select(u.resultAll).from(persistence.User as u)
              .where.eqUuid(u.id, userId).and.isNull(u.deletedAt)
          }.map(persistence.User(u.resultName)).single.apply

          member <- withSQL {
            select(m.resultAll).from(persistence.Member as m)
              .where.eqUuid(m.userId, userId).and.eqUuid(m.groupId, groupId)
          }.map(persistence.Member(m.resultName)).single.apply
        } yield {
          persistence.Member(
            id = member.id,
            groupId = member.groupId,
            userId = member.userId,
            role = GroupMemberRole.Deny,
            status = member.status,
            createdBy = member.createdBy,
            createdAt = member.createdAt,
            updatedBy = user.id,
            updatedAt = DateTime.now(),
            deletedBy = None,
            deletedAt = None
          ).save()
        }) match {
          case Some(_) => Success(Unit)
          case None => Failure(new NotFoundException())
        }
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }


  /**
   * 指定したグループを削除します。
   * @param groupId
   * @param user
   * @return
   */
  def deleteGroup(groupId: String, user: User) = {
    if (user.isGuest) throw new NotAuthorizedException

    try {
      val result = DB localTx { implicit s =>
        val group = getGroup(groupId)
        if (!isGroupAdministrator(user, groupId)) throw new NotAuthorizedException

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()

        withSQL {
          val g = persistence.Group.column
          update(persistence.Group)
            .set(g.deletedBy -> sqls.uuid(myself.id), g.deletedAt -> timestamp,
              g.updatedBy -> sqls.uuid(myself.id), g.updatedAt -> timestamp)
            .where
            .eq(g.id, sqls.uuid(groupId))
            .and
            .isNull(g.deletedAt)
        }.update().apply
      }
      Success(result)
    } catch {
      case e: Exception => Failure(e)
    }
  }

  /**
   * 指定したグループのプライマリ画像を変更します。
   * @param groupId
   * @param imageId
   * @param user
   * @return
   */
  def changePrimaryImage(groupId: String, imageId: String, user: User): Try[Unit] = {
    try {
      DB localTx { implicit s =>
        val group = getGroup(groupId)
        if (!isGroupAdministrator(user, groupId)) throw new NotAuthorizedException
        if (!existsGroupImage(groupId, imageId)) throw new NotFoundException

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        withSQL {
          val gi = persistence.GroupImage.column
          update(persistence.GroupImage)
            .set(gi.isPrimary -> true, gi.updatedBy -> sqls.uuid(myself.id), gi.updatedAt -> timestamp)
            .where
            .eq(gi.imageId, sqls.uuid(imageId))
            .and
            .eq(gi.groupId, sqls.uuid(groupId))
            .and
            .isNull(gi.deletedAt)
        }.update().apply

        withSQL {
          val gi = persistence.GroupImage.column
          update(persistence.GroupImage)
            .set(gi.isPrimary -> false, gi.updatedBy -> sqls.uuid(myself.id), gi.updatedAt -> timestamp)
            .where
            .ne(gi.imageId, sqls.uuid(imageId))
            .and
            .eq(gi.groupId, sqls.uuid(groupId))
            .and
            .isNull(gi.deletedAt)
        }.update().apply

        Success(Unit)
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }

  /**
   * 指定したグループの画像を削除します。
   * @param groupId
   * @param imageId
   * @param user
   * @return
   */
  def deleteImage(groupId: String, imageId: String, user: User) = {
    try {
      val primaryImage = DB localTx { implicit s =>
        val group = getGroup(groupId)
        if (!isGroupAdministrator(user, groupId)) throw new NotAuthorizedException
        if (!existsGroupImage(groupId, imageId)) throw new NotFoundException

        val myself = persistence.User.find(user.id).get
        val timestamp = DateTime.now()
        withSQL {
          val gi = persistence.GroupImage.column
          update(persistence.GroupImage)
            .set(gi.deletedBy -> sqls.uuid(myself.id), gi.deletedAt -> timestamp, gi.isPrimary -> false,
              gi.updatedBy -> sqls.uuid(myself.id), gi.updatedAt -> timestamp)
            .where
            .eq(gi.groupId, sqls.uuid(groupId))
            .and
            .eq(gi.imageId, sqls.uuid(imageId))
            .and
            .isNull(gi.deletedAt)
        }.update().apply

        getPrimaryImageId(groupId) match {
          case Some(x) => x
          case None =>
            // primaryImageの差し替え
            val gi = persistence.GroupImage.syntax("di")
            val i = persistence.Image.syntax("i")

            // primaryImageとなるImageを取得
            val primaryImage = withSQL {
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
            }.map(rs => (rs.string(gi.resultName.id), rs.string(i.resultName.id))).single().apply

            primaryImage match {
              case Some(x) =>
                val gi = persistence.GroupImage.column
                withSQL {
                  update(persistence.GroupImage)
                    .set(gi.isPrimary -> true, gi.updatedBy -> sqls.uuid(myself.id), gi.updatedAt -> timestamp)
                    .where
                    .eq(gi.id, sqls.uuid(x._1))
                }.update().apply
                x._2
              case None => ""
            }
        }
      }

      Success(GroupData.GroupDeleteImage(
        primaryImage = primaryImage
      ))
    } catch {
      case e: Throwable => Failure(e)
    }
  }
  private def getGroup(groupId: String)(implicit s: DBSession) = {
    if (StringUtil.isUUID(groupId)) {
      val g = persistence.Group.syntax("g")
      withSQL {
        select(g.result.*)
          .from(persistence.Group as g)
          .where
          .eq(g.id, sqls.uuid(groupId))
          .and
          .eq(g.groupType, persistence.GroupType.Public)
          .and
          .isNull(g.deletedAt)
      }.map(persistence.Group(g.resultName)).single().apply match {
        case Some(x) => x
        case None =>  throw new NotFoundException
      }
    } else {
      throw new NotFoundException
    }
  }


  private def countDatasets(groups : Seq[String])(implicit s: DBSession) = {
    val ds = persistence.Dataset.syntax("ds")
    val o = persistence.Ownership.syntax("o")

    withSQL {
      select(o.groupId, sqls.count(sqls.distinct(ds.id)).append(sqls"count"))
        .from(persistence.Dataset as ds)
        .innerJoin(persistence.Ownership as o).on(o.datasetId, ds.id)
        .where
        .inUuid(o.groupId, Seq.concat(groups, Seq(AppConf.guestGroupId)))
        .and
        .gt(o.accessLevel, GroupAccessLevel.Deny)
        .and
        .isNull(ds.deletedAt)
        .and
        .isNull(o.deletedAt)
        .groupBy(o.groupId)
    }.map(rs => (rs.string(persistence.Ownership.column.groupId), rs.int("count"))).list().apply.toMap
  }

  private def countMembers(groups: Seq[String])(implicit s: DBSession) = {
    val m = persistence.Member.syntax("m")
    withSQL {
      select(m.groupId, sqls.count(sqls.distinct(m.id)).append(sqls"count"))
        .from(persistence.Member as m)
        .where
        .inUuid(m.groupId, Seq.concat(groups, Seq(AppConf.guestGroupId)))
        .and
        .not.eq(m.role, GroupMemberRole.Deny)
        .and
        .isNull(m.deletedAt)
        .groupBy(m.groupId)
    }.map(rs => (rs.string(persistence.Member.column.groupId), rs.int("count"))).list().apply.toMap
  }

  private def getGroupImage(groupId: String)(implicit s: DBSession) = {
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
    }.map(rs => persistence.Image(i.resultName)(rs)).list().apply()
  }

  private def getGroupImageIds(groupIds: Seq[String])(implicit s: DBSession): Map[String, String] = {
    if (groupIds.nonEmpty) {
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
      }.map(rs => (rs.string(gi.resultName.groupId), rs.string(gi.resultName.imageId))).list().apply().toMap
    } else {
      Map.empty
    }
  }

  private def getGroupPrimaryImageId(groupId: String)(implicit s: DBSession) = {
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
    }.map(_.string(gi.resultName.imageId)).single().apply()
  }

  private def countMembers(groupId: String)(implicit s: DBSession): Int = {
    val m = persistence.Member.m
    withSQL {
      createMembersSql(select(sqls.count(m.id)), groupId)
    }.map(rs => rs.int(1)).single().apply().get
  }

  private def getMembers(groupId: String, userId: String, offset: Int, limit: Int)(implicit s: DBSession): Seq[(persistence.User, Int)] = {
    val m = persistence.Member.m
    val u = persistence.User.u
    withSQL {
      createMembersSql(select
        .apply(u.result.*, m.role, sqls.eqUuid(u.id, userId).and.eq(m.role, GroupMemberRole.Manager).append(sqls"own")), groupId)
        .orderBy(sqls"own desc", m.role.desc, m.createdAt.desc)
        .offset(offset)
        .limit(limit)
    }.map(rs => (persistence.User(u.resultName)(rs), rs.int(persistence.Member.column.role))).list().apply()
  }

  private def createMembersSql[A](builder: SelectSQLBuilder[A], groupId: String) = {
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
      .isNull(u.deletedAt)
  }

  private def getPrimaryImageId(groupId: String)(implicit s: DBSession) = {
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
    }.map(rs => rs.string(i.resultName.id)).single().apply
  }

  private def getGroupRole(user: User, groupId: String)(implicit s: DBSession) = {
    if (user.isGuest) {
      None
    } else {
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
      }.map(_.int(m.resultName.role)).single().apply
    }
  }

  private def isGroupAdministrator(user: User, groupId: String)(implicit s: DBSession) = {
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
  
  private def existsGroupImage(groupId: String, imageId: String)(implicit s: DBSession) = {
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
    }.map(rs => rs.string(gi.resultName.id)).single().apply match {
      case Some(x) => true
      case None => false
    }
  }

  private def isValidUser(userId: String)(implicit s: DBSession) = {
    if (StringUtil.isUUID(userId)) {
      val u = persistence.User.syntax("u")
      withSQL {
        select(u.result.id)
          .from(persistence.User as u)
          .where
          .eq(u.id, sqls.uuid(userId))
          .and
          .isNull(u.deletedAt)
      }.map(rs => rs.string(u.resultName.id)).single().apply match {
        case Some(x) => true
        case None => false
      }
    } else {
      false
    }
  }

  private def isValidGroupMember(groupId: String, memberId: String)(implicit s: DBSession) = {
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
    }.map(rs => rs.string(m.resultName.id)).single().apply match {
      case Some(x) => true
      case None => false
    }
  }
}
