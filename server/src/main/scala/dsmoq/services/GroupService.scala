package dsmoq.services

import dsmoq.services.data._
import scala.util.{Failure, Success, Try}
import scalikejdbc.{DBSession, DB}
import dsmoq.{AppConf, persistence}
import scalikejdbc.SQLInterpolation._
import scala.util.Failure
import dsmoq.services.data.RangeSliceSummary
import scala.util.Success
import dsmoq.services.data.RangeSlice
import dsmoq.persistence.PostgresqlHelper._
import dsmoq.persistence.{GroupType, PresetType, GroupMemberRole, AccessLevel}
import org.joda.time.DateTime
import dsmoq.exceptions._
import java.util.UUID
import dsmoq.logic.{StringUtil, ImageSaveLogic}
import scala.util.Failure
import scala.Some
import scala.util.Success
import dsmoq.services.data.RangeSlice
import dsmoq.services.data.RangeSliceSummary
import dsmoq.services.data.Image
import scala.collection.mutable

object GroupService {
  def search(params: GroupData.SearchGroupsParams): Try[RangeSlice[GroupData.GroupsSummary]] = {
    try {
      // FIXME input parameter check
      val offset = try {
        params.offset.getOrElse("0").toInt
      } catch {
        case e: Exception => throw new InputValidationException(mutable.LinkedHashMap[String, String]("offset" -> "wrong parameter"))
      }
      val limit = try {
        params.limit.getOrElse("20").toInt
      } catch {
        case e: Exception => throw new InputValidationException(mutable.LinkedHashMap[String, String]("limit" -> "wrong parameter"))
      }

      DB readOnly { implicit s =>
        val groups = params.user match {
          case Some(x) => getUserGroups(x, offset, limit)
          case None => getGroups(offset, limit)
        }
        val count = groups.size
        val groupIds = groups.map(_.id)
        val datasetsCount = countDatasets(groupIds)
        val membersCount = countMembers(groupIds)
        val groupImages = getGroupImageIds(groups.map(_.id))

        val summary = RangeSliceSummary(count, limit, offset)
        val results = if (count > offset) {
          groups.map(x => {
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
          })
        } else {
          List.empty
        }
        Success(RangeSlice(summary, results))
      }
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def get(params: GroupData.GetGroupParams): Try[GroupData.Group] = {
    try {
      DB readOnly { implicit s =>
        val group = try {
          getGroup(params.groupId) match {
            case Some(x) => x
            case None => throw new NotFoundException
          }
        } catch {
          case e: Exception => throw new NotFoundException
        }

        val images = getGroupImage(group.id)
        val primaryImage = getGroupPrimaryImageId(group.id)
        println(primaryImage)
        val groupRole = getGroupRole(params.userInfo, group.id)

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
      case e: Exception => Failure(e)
    }
  }

  def getGroupMembers(params: GroupData.GetGroupMembersParams) = {
    try {
      // FIXME input parameter check
      val offset = try {
        params.offset.getOrElse("0").toInt
      } catch {
        case e: Exception => throw new InputValidationException(mutable.LinkedHashMap[String, String]("offset" -> "wrong parameter"))
      }
      val limit = try {
        params.limit.getOrElse("20").toInt
      } catch {
        case e: Exception => throw new InputValidationException(mutable.LinkedHashMap[String, String]("limit" -> "wrong parameter"))
      }

      DB readOnly { implicit s =>
        val members = getMembers(params.groupId, offset, limit)
        val count = members.size

        val summary = RangeSliceSummary(count, limit, offset)
        val results = if (count > offset) {
          members.map(x => {
            GroupData.MemberSummary(
              id = x._1.id,
              name = x._1.name,
              fullname = x._1.fullname,
              organization = x._1.organization,
              title = x._1.title,
              image = AppConf.imageDownloadRoot + x._1.imageId,
              role = x._2
            )
          })
        } else {
          List.empty
        }
        Success(RangeSlice(summary, results))
      }
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def createGroup(params: GroupData.CreateGroupParams) = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    // input validation
    val errors = mutable.LinkedHashMap.empty[String, String]
    val name = if (params.name.isDefined && StringUtil.trimAllSpaces(params.name.get).length != 0) {
      StringUtil.trimAllSpaces(params.name.get)
    } else {
      errors.put("name", "name is empty")
      ""
    }
    val description = if (params.description.isDefined && StringUtil.trimAllSpaces(params.description.get).length != 0) {
      StringUtil.trimAllSpaces(params.description.get)
    } else {
      errors.put("description", "description is empty")
      ""
    }
    if (errors.size != 0) {
      throw new InputValidationException(errors)
    }

    try {
      DB localTx { implicit s =>
        // 同名チェック
        val g = persistence.Group.syntax("g")
        val sameNameGroups = withSQL {
          select(g.result.id)
          .from(persistence.Group as g)
          .where
          .lowerEq(g.name, name)
          .and
          .eq(g.groupType, GroupType.Public)
          .and
          .isNull(g.deletedAt)
        }.map(_.string(g.resultName.id)).list().apply
        if (sameNameGroups.size != 0) {
          throw new InputValidationException(mutable.LinkedHashMap[String, String]("name" -> "same name"))
        }

        val myself = persistence.User.find(params.userInfo.id).get
        val timestamp = DateTime.now()
        val groupId = UUID.randomUUID.toString

        val group = persistence.Group.create(
          id = groupId,
          name = name,
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
      case e: Exception => Failure(e)
    }
  }

  def modifyGroup(params: GroupData.ModifyGroupParams) = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException
    
    // input validation
    val errors = mutable.LinkedHashMap.empty[String, String]
    val name = if (params.name.isDefined && StringUtil.trimAllSpaces(params.name.get).length != 0) {
      StringUtil.trimAllSpaces(params.name.get)
    } else {
      errors.put("name", "name is empty")
      ""
    }
    val description = if (params.description.isDefined && StringUtil.trimAllSpaces(params.description.get).length != 0) {
      StringUtil.trimAllSpaces(params.description.get)
    } else {
      errors.put("description", "description is empty")
      ""
    }
    if (errors.size != 0) {
      throw new InputValidationException(errors)
    }

    try {
      DB localTx { implicit s =>
        try {
          getGroup(params.groupId) match {
            case Some(x) =>
              // 権限チェック
              if (!isGroupAdministrator(params.userInfo, params.groupId)) throw new NotAuthorizedException
            case None => throw new NotFoundException
          }
        } catch {
          case e: NotAuthorizedException => throw e
          case e: Exception => throw new NotFoundException
        }

        // 同名チェック
        val g = persistence.Group.syntax("g")
        val sameNameGroups = withSQL {
          select(g.result.id)
            .from(persistence.Group as g)
            .where
            .lowerEq(g.name, name)
            .and
            .ne(g.id, sqls.uuid(params.groupId))
            .and
            .eq(g.groupType, GroupType.Public)
            .and
            .isNull(g.deletedAt)
        }.map(_.string(g.resultName.id)).list().apply
        if (sameNameGroups.size != 0) {
          throw new InputValidationException(mutable.LinkedHashMap[String, String]("name" -> "same name"))
        }

        val myself = persistence.User.find(params.userInfo.id).get
        val timestamp = DateTime.now()

        withSQL {
          val g = persistence.Group.column
          update(persistence.Group)
            .set(g.name -> name, g.description -> description,
            g.updatedBy -> sqls.uuid(myself.id), g.updatedAt -> timestamp)
            .where
            .eq(g.id, sqls.uuid(params.groupId))
            .and
            .isNull(g.deletedAt)
        }.update().apply

        val group = persistence.Group.find(params.groupId)
        val images = getGroupImage(params.groupId)
        val primaryImage = getGroupPrimaryImageId(params.groupId)

        Success(GroupData.Group(
          id = group.get.id,
          name = group.get.name,
          description = group.get.description,
          images = images.map(x => Image(
            id = x.id,
            url = AppConf.imageDownloadRoot + x.id
          )),
          primaryImage = primaryImage.getOrElse(""),
          isMember = true,
          role = getGroupRole(params.userInfo, group.get.id).getOrElse(0)
        ))
      }
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def setUserRole(params: GroupData.SetUserRoleParams) = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    // input parameter check
    val errors = mutable.LinkedHashMap.empty[String, String]
    val userIds = params.userIds match {
      case Some(x) => x
      case None =>
        errors.put("id", "ID is empty")
        Seq.empty
    }
    val roles = params.roles match {
      case Some(x) =>
        try {
          x.map(_.toInt)
        } catch {
          case e: Exception =>
            errors.put("role", "role format error")
            Seq.empty
        }
      case None =>
        errors.put("role", "role is empty")
        Seq.empty
    }
    if (errors.size != 0) {
      throw new InputValidationException(errors)
    }

    if (userIds.size != roles.size) {
      throw new InputValidationException(mutable.LinkedHashMap[String, String]("id" -> "parameters are not same size"))
    }
    roles.foreach { r =>
      r match {
        case GroupMemberRole.Deny => // do nothing
        case GroupMemberRole.Manager => // do nothing
        case GroupMemberRole.Member => // do nothing
        case _ => throw new InputValidationException(mutable.LinkedHashMap[String, String]("role" -> "role value error"))
      }
    }

    try {
      DB localTx { implicit s =>
        try {
          getGroup(params.groupId) match {
            case Some(x) =>
              // 権限チェック
              if (!isGroupAdministrator(params.userInfo, params.groupId)) throw new NotAuthorizedException
            case None => throw new NotFoundException
          }
          userIds.foreach { x =>
            if (!isValidUser(x)) throw new InputValidationException(mutable.LinkedHashMap[String, String]("id" -> "ID is not found"))
          }
        } catch {
          case e: NotAuthorizedException => throw e
          case e: InputValidationException => throw e
          case e: Exception => throw new NotFoundException
        }

        val myself = persistence.User.find(params.userInfo.id).get
        val timestamp = DateTime.now()

        (0 to userIds.size - 1).foreach {i =>
          val user = persistence.User.find(userIds(i)).get
          val m = persistence.Member.syntax("m")
          withSQL {
            select(m.result.*)
              .from(persistence.Member as m)
              .where
              .eq(m.userId, sqls.uuid(userIds(i)))
              .and
              .eq(m.groupId, sqls.uuid(params.groupId))
          }.map(persistence.Member(m.resultName)).single().apply match {
            case Some(x) =>
              // create
              val m = persistence.Member.column
              withSQL {
                update(persistence.Member)
                  .set(m.role -> roles(i), m.status -> 1, m.updatedAt -> timestamp, m.updatedBy -> sqls.uuid(myself.id),
                    m.deletedAt -> null, m.deletedBy -> null)
                  .where
                  .eq(m.userId, sqls.uuid(userIds(i)))
                  .and
                  .eq(m.groupId, sqls.uuid(params.groupId))
              }.update().apply
            case None =>
              // update
              persistence.Member.create(
                id = UUID.randomUUID.toString,
                groupId = params.groupId,
                userId = user.id,
                role = roles(i),
                status = 1,
                createdBy = myself.id,
                createdAt = timestamp,
                updatedBy = myself.id,
                updatedAt = timestamp
              )
          }
        }
      }
      Success(userIds)
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def deleteGroup(params: GroupData.DeleteGroupParams) = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    try {
      val result = DB localTx { implicit s =>
        try {
          getGroup(params.groupId) match {
            case Some(x) => // do nothing
            case None => throw new NotFoundException
          }
        } catch {
          case e: Exception => throw new NotFoundException
        }

        // 権限チェック
        if (!isGroupAdministrator(params.userInfo, params.groupId)) throw new NotAuthorizedException

        val myself = persistence.User.find(params.userInfo.id).get
        val timestamp = DateTime.now()

        withSQL {
          val g = persistence.Group.column
          update(persistence.Group)
            .set(g.deletedBy -> sqls.uuid(myself.id), g.deletedAt -> timestamp,
              g.updatedBy -> sqls.uuid(myself.id), g.updatedAt -> timestamp)
            .where
            .eq(g.id, sqls.uuid(params.groupId))
            .and
            .isNull(g.deletedAt)
        }.update().apply
      }
      Success(result)
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def addImages(params: GroupData.AddImagesToGroupParams) = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    val inputImages = params.images match {
      case Some(x) => x.filter(_.name.length != 0)
      case None => Seq.empty
    }
    if (inputImages.size == 0) throw new InputValidationException(mutable.LinkedHashMap[String, String]("image" -> "image is empty"))

    DB localTx { implicit s =>
      try {
        getGroup(params.groupId) match {
          case Some(x) =>
            // 権限チェック
            if (!isGroupAdministrator(params.userInfo, params.groupId)) throw new NotAuthorizedException
          case None => throw new NotFoundException
        }
      } catch {
        case e: NotAuthorizedException => throw e
        case e: Exception => throw new NotFoundException
      }

      val myself = persistence.User.find(params.userInfo.id).get
      val timestamp = DateTime.now()
      val primaryImage = getPrimaryImageId(params.groupId)
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
        val groupImage = persistence.GroupImage.create(
          id = UUID.randomUUID.toString,
          groupId = params.groupId,
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
        images = images.map(x => Image(
          id = x.id,
          url = AppConf.imageDownloadRoot + x.id
        )),
        primaryImage = getPrimaryImageId(params.groupId).getOrElse("")
      ))
    }
  }
  
  def changePrimaryImage(params: GroupData.ChangeGroupPrimaryImageParams) = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    // FIXME input validation check
    val imageId = params.id match {
      case Some(x) => x
      case None => throw new InputValidationException(mutable.LinkedHashMap[String, String]("id" -> "id is empty"))
    }

    DB localTx { implicit s =>
      try {
        getGroup(params.groupId) match {
          case Some(x) =>
            // 権限チェック
            if (!isGroupAdministrator(params.userInfo, params.groupId)) throw new NotAuthorizedException
            // image_idの存在チェック
            if (!isValidGroupImage(params.groupId, imageId)) throw new NotFoundException
          case None => throw new NotFoundException
        }
      } catch {
        case e: NotAuthorizedException => throw e
        case e: Exception => throw new NotFoundException
      }

      val myself = persistence.User.find(params.userInfo.id).get
      val timestamp = DateTime.now()
      withSQL {
        val gi = persistence.GroupImage.column
        update(persistence.GroupImage)
          .set(gi.isPrimary -> true, gi.updatedBy -> sqls.uuid(myself.id), gi.updatedAt -> timestamp)
          .where
          .eq(gi.imageId, sqls.uuid(imageId))
          .and
          .eq(gi.groupId, sqls.uuid(params.groupId))
          .and
          .isNull(gi.deletedAt)
      }.update().apply

      withSQL{
        val gi = persistence.GroupImage.column
        update(persistence.GroupImage)
          .set(gi.isPrimary -> false, gi.updatedBy -> sqls.uuid(myself.id), gi.updatedAt -> timestamp)
          .where
          .ne(gi.imageId, sqls.uuid(imageId))
          .and
          .eq(gi.groupId, sqls.uuid(params.groupId))
          .and
          .isNull(gi.deletedAt)
      }.update().apply

      Success(params.id)
    }
  }

  def deleteImage(params: GroupData.DeleteGroupImageParams) = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    val primaryImage = DB localTx { implicit s =>
      try {
        getGroup(params.groupId) match {
          case Some(x) =>
            // 権限チェック
            if (!isGroupAdministrator(params.userInfo, params.groupId)) throw new NotAuthorizedException
            // image_idの存在チェック
            if (!isValidGroupImage(params.groupId, params.imageId)) throw new NotFoundException
          case None => throw new NotFoundException
        }
      } catch {
        case e: NotAuthorizedException => throw e
        case e: Exception => throw new NotFoundException
      }

      val myself = persistence.User.find(params.userInfo.id).get
      val timestamp = DateTime.now()
      withSQL {
        val gi = persistence.GroupImage.column
        update(persistence.GroupImage)
          .set(gi.deletedBy -> sqls.uuid(myself.id), gi.deletedAt -> timestamp, gi.isPrimary -> false,
            gi.updatedBy -> sqls.uuid(myself.id), gi.updatedAt -> timestamp)
          .where
          .eq(gi.groupId, sqls.uuid(params.groupId))
          .and
          .eq(gi.imageId, sqls.uuid(params.imageId))
          .and
          .isNull(gi.deletedAt)
      }.update().apply

      getPrimaryImageId(params.groupId) match {
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
              .eq(gi.groupId, sqls.uuid(params.groupId))
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
  }
  private def getGroup(groupId: String)(implicit s: DBSession) = {
//    persistence.Group.find(groupId)
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
    }.map(persistence.Group(g.resultName)).single().apply
  }


  private def getGroups(offset: Int, limit: Int)(implicit s: DBSession): Seq[persistence.Group] = {
    val g = persistence.Group.syntax("g")
    withSQL {
      select(g.result.*)
        .from(persistence.Group as g)
        .where
        .eq(g.groupType, persistence.GroupType.Public)
        .and
        .isNull(g.deletedAt)
        .orderBy(g.updatedAt).desc
        .offset(offset)
        .limit(limit)
    }.map(rs => persistence.Group(g.resultName)(rs)).list().apply
  }

  private def getUserGroups(userId: String, offset: Int, limit: Int)(implicit s: DBSession): Seq[persistence.Group] = {
    try {
      val g = persistence.Group.syntax("g")
      val m = persistence.Member.syntax("m")
      withSQL {
        select(g.result.*)
          .from(persistence.Group as g)
          .innerJoin(persistence.Member as m).on(g.id, m.groupId)
          .where
          .eq(g.groupType, persistence.GroupType.Public)
          .and
          .eq(m.userId, sqls.uuid(userId))
          .and
          .isNull(g.deletedAt)
          .and
          .isNull(m.deletedAt)
          .orderBy(g.updatedAt).desc
          .offset(offset)
          .limit(limit)
      }.map(rs => persistence.Group(g.resultName)(rs)).list().apply
    } catch {
      case e: Exception => Seq.empty
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
        .inByUuid(o.groupId, Seq.concat(groups, Seq(AppConf.guestGroupId)))
        .and
        .gt(o.accessLevel, AccessLevel.Deny)
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
        .inByUuid(m.groupId, Seq.concat(groups, Seq(AppConf.guestGroupId)))
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
          .inByUuid(gi.groupId, groupIds)
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

  private def getMembers(groupId: String, offset: Int, limit: Int)(implicit s: DBSession): Seq[(persistence.User, Int)] = {
    val m = persistence.Member.syntax("m")
    val u = persistence.User.syntax("u")
    val g = persistence.Group.syntax("g")
    withSQL {
      select(u.result.*, m.role)
        .from(persistence.User as u)
        .innerJoin(persistence.Member as m).on(sqls.eq(m.userId, u.id).and.isNull(m.deletedAt))
        .innerJoin(persistence.Group as g).on(sqls.eq(g.id, m.groupId).and.isNull(g.deletedAt))
        .where
        .eq(m.groupId, sqls.uuid(groupId))
        .and
        .not.eq(m.role, GroupMemberRole.Deny)
        .and
        .isNull(m.deletedAt)
        .orderBy(m.role).desc.append(sqls", ").append(m.createdAt).desc
        .offset(offset)
        .limit(limit)
    }.map(rs => (persistence.User(u.resultName)(rs), rs.int(persistence.Member.column.role))).list().apply()
  }

  private def getDatasets(user: User, groupId: String, offset: Int, limit: Int)(implicit s: DBSession): Seq[(persistence.Dataset, Int)] = {
    if (user.isGuest) {
      Seq.empty
    } else {
      val ds = persistence.Dataset.syntax("ds")
      val o = persistence.Ownership.syntax("o")
      val g = persistence.Group.syntax("g")
      withSQL {
        select(ds.result.*, sqls.max(o.accessLevel).append(sqls"access_level"))
          .from(persistence.Dataset as ds)
          .innerJoin(persistence.Ownership as o).on(sqls.eq(ds.id, o.datasetId).and.isNull(o.deletedAt))
          .innerJoin(persistence.Group as g).on(sqls.eq(g.id, o.groupId).and.isNull(g.deletedAt))
          .where
          .eq(o.groupId, sqls.uuid(groupId))
          .and
          .gt(o.accessLevel, persistence.AccessLevel.Deny)
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
  
  private def isValidGroupImage(groupId: String, imageId: String)(implicit s: DBSession) = {
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
