package dsmoq.services

import dsmoq.services.data._
import scala.util.{Failure, Success, Try}
import scalikejdbc.{DBSession, DB}
import dsmoq.{AppConf, persistence}
import scalikejdbc.SQLInterpolation._
import scala.util.Failure
import dsmoq.services.data.RangeSliceSummary
import scala.Some
import scala.util.Success
import dsmoq.services.data.RangeSlice
import dsmoq.persistence.PostgresqlHelper._

object GroupService {
//  def search(params: GroupData.SearchGroupsParams): Try[RangeSlice[GroupData.GroupsSummary]] = {
//    // FIXME
//    try {
//      val offset = params.offset.getOrElse("0").toInt
//      val limit = params.limit.getOrElse("20").toInt
//
//      DB readOnly { implicit s =>
//        val groups = getJoinedGroups(params.userInfo)
//        val count = countGroups(groups)
//
//        val summary = RangeSliceSummary(count, limit, offset)
//        val results = if (count > offset) {
//          val datasets = findDatasets(groups, limit, offset)
//          val datasetIds = datasets.map(_._1.id)
//
//          val owners = getOwnerGroups(datasetIds)
//          val guestAccessLevels = getGuestAccessLevel(datasetIds)
//          val attributes = getAttributes(datasetIds)
//          val files = getFiles(datasetIds)
//
//          datasets.map(x => {
//            val ds = x._1
//            val permission = x._2
//            DatasetData.DatasetsSummary(
//              id = ds.id,
//              name = ds.name,
//              description = ds.description,
//              image = "http://xxx",
//              attributes = List.empty, //TODO
//              ownerships = owners.get(ds.id).getOrElse(Seq.empty),
//              files = ds.filesCount,
//              dataSize = ds.filesSize,
//              defaultAccessLevel = guestAccessLevels.get(ds.id).getOrElse(0),
//              permission = permission
//            )
//          })
//        } else {
//          List.empty
//        }
//        Success(RangeSlice(summary, results))
//      }
//    } catch {
//      case e: Exception => Failure(e)
//    }
//  }

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
    }.map(rs => (persistence.Image(i.resultName)(rs))).list().apply()
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
}
