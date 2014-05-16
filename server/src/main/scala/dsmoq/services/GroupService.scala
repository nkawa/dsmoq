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

        val summary = RangeSliceSummary(count, limit, offset)
        val results = if (count > offset) {
          groups.map(x => {
            GroupData.GroupsSummary(
              id = x.id,
              name = x.name,
              description = x.description,
              image = "http://xxx",
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
        } yield {
          GroupData.Group(
            id = group.id,
            name = group.name,
            description = group.description,
            images = images.map(x => Image(
              id = x.id,
              url = "" //TODO
            )),
            primaryImage = primaryImage.getOrElse("")
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
          val datasetIds = datasets.map(_._1.id)

          val owners = getOwnerGroups(datasetIds)
          val guestAccessLevels = getGuestAccessLevel(datasetIds)
          val attributes = getAttributes(datasetIds)
          val files = getFiles(datasetIds)

          datasets.map(x => {
            val ds = x._1
            val permission = x._2
            DatasetData.DatasetsSummary(
              id = ds.id,
              name = ds.name,
              description = ds.description,
              image = "http://xxx",
              attributes = List.empty, //TODO
              ownerships = owners.get(ds.id).getOrElse(Seq.empty),
              files = ds.filesCount,
              dataSize = ds.filesSize,
              defaultAccessLevel = guestAccessLevels.get(ds.id).getOrElse(0),
              permission = permission
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

    try {
      val group = DB localTx { implicit s =>
        val myself = persistence.User.find(params.userInfo.id).get
        val timestamp = DateTime.now()
        val groupId = UUID.randomUUID.toString

        val group = persistence.Group.create(
          id = groupId,
          name = params.name,
          description = params.description,
          groupType = 1,
          createdBy = myself.id,
          createdAt = timestamp,
          updatedBy = myself.id,
          updatedAt = timestamp
        )
        persistence.Member.create(
          id = UUID.randomUUID.toString,
          groupId = groupId,
          userId = myself.id,
          role = 1,
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
        primaryImage = null
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
          url = "" //TODO
        )),
        primaryImage = result._3.getOrElse("")
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
      val images = params.images.getOrElse(Seq.empty).map(i => {
        // FIXME ファイルサイズ=0のデータ時の措置(現状何も回避していない)
        val imageId = UUID.randomUUID().toString
        val bufferedImage = javax.imageio.ImageIO.read(i.getInputStream)

        val image = persistence.Image.create(
          id = imageId,
          name = i.getName,
          width = bufferedImage.getWidth,
          height = bufferedImage.getWidth,
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
        i.write(Paths.get(AppConf.imageDir).resolve(imageId).toFile)
        image
      })

      Success(GroupData.GroupAddImages(
        images = images.map(x => Image(
          id = x.id,
          url = "" //TODO
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
          .eq(gi.imageId, sqls.uuid(params.imageId))
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
          .ne(gi.imageId, sqls.uuid(params.imageId))
          .and
          .eq(gi.groupId, sqls.uuid(params.groupId))
          .and
          .isNull(gi.deletedAt)
      }.update().apply

      Success(params.imageId)
    }
  }

  private def getGroups(user: User, offset: Int, limit: Int)(implicit s: DBSession): Seq[persistence.Group] = {
    if (user.isGuest) {
      Seq.empty
    } else {
      val g = persistence.Group.syntax("g")
      val m = persistence.Member.syntax("m")
      withSQL {
        select(g.result.*)
          .from(persistence.Group as g)
          .innerJoin(persistence.Member as m).on(m.groupId, g.id)
          .where
          .eq(m.userId, sqls.uuid(user.id))
          .and
          .isNull(g.deletedAt)
          .and
          .isNull(m.deletedAt)
          .orderBy(m.updatedAt).desc
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
          .gt(o.accessLevel, 0)
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

  // FIXME DatasetServiceからのコピペ
  private def getOwnerGroups(datasetIds: Seq[String])(implicit s: DBSession):Map[String, Seq[DatasetData.DatasetOwnership]] = {
    if (datasetIds.nonEmpty) {
      val o = persistence.Ownership.o
      val g = persistence.Group.g
      val m = persistence.Member.m
      val u = persistence.User.u
      withSQL {
        select(o.result.*, g.result.*, u.result.*)
          .from(persistence.Ownership as o)
          .innerJoin(persistence.Group as g)
          .on(sqls.eq(g.id, o.groupId).and.isNull(g.deletedAt))
          .leftJoin(persistence.Member as m)
          .on(sqls.eq(g.id, m.groupId)
          .and.eq(g.groupType, persistence.GroupType.Personal)
          .and.eq(m.role, persistence.GroupMemberRole.Administrator)
          .and.isNull(m.deletedAt))
          .innerJoin(persistence.User as u)
          .on(sqls.eq(m.userId, u.id).and.isNull(u.deletedAt))
          .where
          .inByUuid(o.datasetId, datasetIds)
          .and
          .eq(o.accessLevel, AccessLevel.AllowAll)
          .and
          .isNull(o.deletedAt)
      }.map(rs =>
        (
          rs.string(o.resultName.datasetId),
          DatasetData.DatasetOwnership(
            id = rs.string(g.resultName.id),
            name = rs.stringOpt(u.resultName.name).getOrElse(rs.string(g.resultName.name)),
            fullname = rs.stringOpt(u.resultName.fullname).getOrElse(""),
            organization = rs.stringOpt(u.resultName.organization).getOrElse(""),
            title = rs.stringOpt(u.resultName.title).getOrElse(""),
            image = "", //TODO
            accessLevel = rs.int(o.resultName.accessLevel)
          )
          )
        ).list().apply()
        .groupBy(_._1)
        .map(x => (x._1, x._2.map(_._2)))
    } else {
      Map.empty
    }
  }

  // FIXME DatasetServiceからのコピペ
  private def getGuestAccessLevel(datasetIds: Seq[String])(implicit s: DBSession): Map[String, Int] = {
    if (datasetIds.nonEmpty) {
      val o = persistence.Ownership.syntax("o")
      withSQL {
        select(o.result.datasetId, o.result.accessLevel)
          .from(persistence.Ownership as o)
          .where
          .inByUuid(o.datasetId, datasetIds)
          .and
          .eq(o.groupId, sqls.uuid(AppConf.guestGroupId))
          .and
          .isNull(o.deletedAt)
      }.map(x => (x.string(o.resultName.datasetId), x.int(o.resultName.accessLevel)) ).list().apply().toMap
    } else {
      Map.empty
    }
  }

  // FIXME DatasetServiceからのコピペ
  private def getAttributes(datasetIds: Seq[String])(implicit s: DBSession) = {
    if (datasetIds.nonEmpty) {
      //val k = ds
      //      withSQL {
      //        select()
      //      }.map(_.toMap()).list().apply()
      Map.empty
    } else {
      Map.empty
    }

    //          // attributes
    //          val attrs = sql"""
    //            SELECT
    //              v.*,
    //              k.name
    //            FROM
    //              attribute_values AS v
    //              INNER JOIN attribute_keys AS k ON v.attribute_key_id = k.id
    //            WHERE
    //              v.dataset_id IN (${datasetIdSqls})
    //            """
    //            .map {x => (x.string(""), DatasetAttribute(name=x.string("name"), value=x.string("value"))) }
    //            .list().apply()
    //            .groupBy {x => x._1 }

  }

  // FIXME DatasetServiceからのコピペ
  private def getFiles(datasetIds: Seq[String])(implicit s: DBSession) = {
    Map.empty
  }
}
