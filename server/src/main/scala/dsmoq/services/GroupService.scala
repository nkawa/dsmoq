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
import dsmoq.persistence.AccessLevel
import org.joda.time.DateTime
import dsmoq.exceptions.{ValidationException, NotAuthorizedException}
import java.util.UUID
import java.nio.file.Paths
import scala.collection.mutable.ArrayBuffer
import dsmoq.logic.ImageSaveLogic

object GroupService {
  def search(params: GroupData.SearchGroupsParams): Try[RangeSlice[GroupData.GroupsSummary]] = {
    try {
      val offset = params.offset.getOrElse("0").toInt
      val limit = params.limit.getOrElse("20").toInt

      DB readOnly { implicit s =>
        val groups = getGroups(params.userInfo, offset, limit)
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
        (for {
          group <- persistence.Group.find(params.groupId)
          images = getGroupImage(group.id)
          primaryImage = getGroupPrimaryImageId(group.id)
          groupRole = getGroupRole(params.userInfo, group.id)
        } yield {
          GroupData.Group(
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
          )
        }).map(x => Success(x)).getOrElse(Failure(new RuntimeException()))
      }
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def getGroupMembers(params: GroupData.GetGroupMembersParams) = {
    try {
      val offset = params.offset.getOrElse("0").toInt
      val limit = params.limit.getOrElse("20").toInt

      DB readOnly { implicit s =>
        val members = getMembers(params.userInfo, params.groupId, offset, limit)
        val count = members.size

        val summary = RangeSliceSummary(count, limit, offset)
        val results = if (count > offset) {
          members.map(x => {
            GroupData.MemberSummary(
              id = x._1.id,
              name = x._1.name,
              organization = x._1.organization,
              title = x._1.title,
              image = "",
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

  def getGroupDatasets(params: GroupData.GetGroupDatasetsParams) = {
    try {
      val offset = params.offset.getOrElse("0").toInt
      val limit = params.limit.getOrElse("20").toInt

      DB readOnly { implicit s =>
        val datasets = getDatasets(params.userInfo, params.groupId, offset, limit)
        val count = datasets.size

        val summary = RangeSliceSummary(count, limit, offset)
        val results = if (count > offset) {
          DatasetService.getDatasetSummary(ArrayBuffer(params.groupId), limit, offset)
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

    try {
      val group = DB localTx { implicit s =>
        val myself = persistence.User.find(params.userInfo.id).get
        val timestamp = DateTime.now()
        val groupId = UUID.randomUUID.toString

        val group = persistence.Group.create(
          id = groupId,
          name = params.name,
          description = params.description,
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
          role = persistence.GroupMemberRole.Administrator,
          status = 1,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp
        )
        group
      }
      Success(GroupData.Group(
        id = group.id,
        name = group.name,
        description = group.description,
        images = Seq.empty,
        primaryImage = null,
        isMember = true,
        role = 1
      ))
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def modifyGroup(params: GroupData.ModifyGroupParams) = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    try {
      val result = DB localTx { implicit s =>
        val myself = persistence.User.find(params.userInfo.id).get
        val timestamp = DateTime.now()

        withSQL {
          val g = persistence.Group.column
          update(persistence.Group)
            .set(g.name -> params.name, g.description -> params.description,
            g.updatedBy -> sqls.uuid(myself.id), g.updatedAt -> timestamp)
            .where
            .eq(g.id, sqls.uuid(params.groupId))
            .and
            .isNull(g.deletedAt)
        }.update().apply

        val group = persistence.Group.find(params.groupId)
        val images = getGroupImage(params.groupId)
        val primaryImage = getGroupPrimaryImageId(params.groupId)
        (group, images, primaryImage)
      }
      Success(GroupData.Group(
        id = result._1.get.id,
        name = result._1.get.name,
        description = result._1.get.description,
        images = result._2.map(x => Image(
          id = x.id,
          url = AppConf.imageDownloadRoot + x.id
        )),
        primaryImage = result._3.getOrElse(""),
        isMember = true,
        role = 1
      ))
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def addUser(params: GroupData.AddUserToGroupParams) = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    try {
      val result = DB localTx { implicit s =>
        val myself = persistence.User.find(params.userInfo.id).get
        val addUser = persistence.User.find(params.userId).get
        val timestamp = DateTime.now()

        persistence.Member.create(
          id = UUID.randomUUID.toString,
          groupId = params.groupId,
          userId = addUser.id,
          role = params.role,
          status = 1,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp
        )
        addUser
      }
      Success(GroupData.AddMember(
        id = result.id,
        name = result.name,
        organization = result.organization,
        role = params.role
      ))
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def modifyMemberRole(params: GroupData.ModifyMemberRoleParams) = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    try {
      val result = DB localTx { implicit s =>
        val myself = persistence.User.find(params.userInfo.id).get
        val timestamp = DateTime.now()

        withSQL {
          val m = persistence.Member.column
          update(persistence.Member)
            .set(m.role -> params.role,
              m.updatedBy -> sqls.uuid(myself.id), m.updatedAt -> timestamp)
            .where
            .eq(m.id, sqls.uuid(params.memberId))
            .and
            .eq(m.groupId, sqls.uuid(params.groupId))
            .and
            .isNull(m.deletedAt)
        }.update().apply
      }
      Success(result)
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def deleteMember(params: GroupData.DeleteMemberParams) = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    try {
      val result = DB localTx { implicit s =>
        val myself = persistence.User.find(params.userInfo.id).get
        val timestamp = DateTime.now()

        withSQL {
          val m = persistence.Member.column
          update(persistence.Member)
            .set(m.deletedBy -> sqls.uuid(myself.id), m.deletedAt -> timestamp,
              m.updatedBy -> sqls.uuid(myself.id), m.updatedAt -> timestamp)
            .where
            .eq(m.id, sqls.uuid(params.memberId))
            .and
            .eq(m.groupId, sqls.uuid(params.groupId))
            .and
            .isNull(m.deletedAt)
        }.update().apply
      }
      Success(result)
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def deleteGroup(params: GroupData.DeleteGroupParams) = {
    if (params.userInfo.isGuest) throw new NotAuthorizedException

    try {
      val result = DB localTx { implicit s =>
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
    if (params.images.getOrElse(Seq.empty).isEmpty) throw new ValidationException

    DB localTx { implicit s =>
      val myself = persistence.User.find(params.userInfo.id).get
      val timestamp = DateTime.now()
      val primaryImage = getPrimaryImageId(params.groupId)
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
        val groupImage = persistence.GroupImage.create(
          id = UUID.randomUUID.toString,
          groupId = params.groupId,
          imageId = imageId,
          isPrimary = if (isFirst && primaryImage.isEmpty) 1 else 0,
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

    DB localTx { implicit s =>
      val myself = persistence.User.find(params.userInfo.id).get
      val timestamp = DateTime.now()
      withSQL {
        val gi = persistence.GroupImage.column
        update(persistence.GroupImage)
          .set(gi.isPrimary -> 1, gi.updatedBy -> sqls.uuid(myself.id), gi.updatedAt -> timestamp)
          .where
          .eq(gi.imageId, sqls.uuid(params.id))
          .and
          .eq(gi.groupId, sqls.uuid(params.groupId))
          .and
          .isNull(gi.deletedAt)
      }.update().apply

      withSQL{
        val gi = persistence.GroupImage.column
        update(persistence.GroupImage)
          .set(gi.isPrimary -> 0, gi.updatedBy -> sqls.uuid(myself.id), gi.updatedAt -> timestamp)
          .where
          .ne(gi.imageId, sqls.uuid(params.id))
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
      val myself = persistence.User.find(params.userInfo.id).get
      val timestamp = DateTime.now()
      withSQL {
        val gi = persistence.GroupImage.column
        update(persistence.GroupImage)
          .set(gi.deletedBy -> sqls.uuid(myself.id), gi.deletedAt -> timestamp, gi.isPrimary -> 0,
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
                  .set(gi.isPrimary -> 1, gi.updatedBy -> sqls.uuid(myself.id), gi.updatedAt -> timestamp)
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

  private def getGroups(user: User, offset: Int, limit: Int)(implicit s: DBSession): Seq[persistence.Group] = {
    if (user.isGuest) {
      Seq.empty
    } else {
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
        .gt(o.accessLevel, 0)
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

  private def getGroupImageIds(groupIds: Seq[String])(implicit s: DBSession) = {
    val gi = persistence.GroupImage.syntax("gi")
    withSQL {
      select(gi.result.*)
      .from(persistence.GroupImage as gi)
      .where
      .inByUuid(gi.groupId, groupIds)
      .and
      .eq(gi.isPrimary, 1)
      .and
      .isNull(gi.deletedAt)
    }.map(rs => (rs.string(gi.resultName.groupId), rs.string(gi.resultName.imageId))).list().apply().toMap
  }

  private def getGroupPrimaryImageId(groupId: String)(implicit s: DBSession) = {
    val gi = persistence.GroupImage.syntax("gi")
    withSQL {
      select(gi.id)
        .from(persistence.GroupImage as gi)
        .where
        .eq(gi.groupId, sqls.uuid(groupId))
        .and
        .eq(gi.isPrimary, 1)
        .and
        .isNull(gi.deletedAt)
    }.map(_.string("id")).single().apply()
  }

  private def getMembers(user: User, groupId: String, offset: Int, limit: Int)(implicit s: DBSession): Seq[(persistence.User, Int)] = {
    if (user.isGuest) {
      Seq.empty
    } else {
      val m = persistence.Member.syntax("m")
      val u = persistence.User.syntax("u")
      withSQL {
        select(u.result.*, m.role)
          .from(persistence.User as u)
          .innerJoin(persistence.Member as m).on(m.userId, u.id)
          .where
          .eq(m.groupId, sqls.uuid(groupId))
          .and
          .isNull(m.deletedAt)
          .orderBy(m.updatedAt).desc
          .offset(offset)
          .limit(limit)
      }.map(rs => (persistence.User(u.resultName)(rs), rs.int(persistence.Member.column.role))).list().apply()
    }
  }

  private def getDatasets(user: User, groupId: String, offset: Int, limit: Int)(implicit s: DBSession): Seq[(persistence.Dataset, Int)] = {
    if (user.isGuest) {
      Seq.empty
    } else {
      val ds = persistence.Dataset.syntax("ds")
      val o = persistence.Ownership.syntax("o")
      withSQL {
        select(ds.result.*, sqls.max(o.accessLevel).append(sqls"access_level"))
          .from(persistence.Dataset as ds)
          .innerJoin(persistence.Ownership as o).on(ds.id, o.datasetId)
          .where
          .eq(o.groupId, sqls.uuid(groupId))
          .and
          .eq(o.accessLevel, persistence.AccessLevel.AllowAll)
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
        .eq(gi.isPrimary, 1)
        .and
        .isNull(gi.deletedAt)
        .and
        .isNull(i.deletedAt)
    }.map(rs => rs.string(i.resultName.id)).single().apply
  }

  private def getGroupRole(user: User, groupId: String)(implicit s: DBSession) = {
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