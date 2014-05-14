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
}
