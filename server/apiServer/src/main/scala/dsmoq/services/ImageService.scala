package dsmoq.services

import java.nio.file.Paths
import java.util.ResourceBundle

import scala.util.Try

import dsmoq.AppConf
import dsmoq.ResourceNames
import dsmoq.exceptions.AccessDeniedException
import dsmoq.exceptions.NotFoundException
import dsmoq.logic.ImageSaveLogic
import dsmoq.persistence
import dsmoq.persistence.GroupAccessLevel
import dsmoq.persistence.GroupType
import dsmoq.persistence.PostgresqlHelper.PgConditionSQLBuilder
import dsmoq.persistence.PostgresqlHelper.PgSQLSyntaxType
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.interpolation.Implicits.scalikejdbcSQLInterpolationImplicitDef
import scalikejdbc.interpolation.Implicits.scalikejdbcSQLSyntaxToStringImplicitDef
import scalikejdbc.select
import scalikejdbc.sqls
import scalikejdbc.withSQL

/**
 * 画像ファイル取得操作を取り扱うサービスクラス
 *
 * @param resource リソースバンドルのインスタンス
 */
class ImageService(resource: ResourceBundle) {

  /**
   * 指定されたユーザの画像を取得します。
   *
   * @param userId 画像を持っているユーザのID
   * @param imageId 取得する画像ID
   * @param size 取得する画像サイズ、Noneでオリジナル
   * @return
   *   Success(ファイルオブジェクトとファイル名のペア) 成功時
   *   Failure(NullPointerException) 引数がnullの場合
   *   Failure(NotFoundException) 対象画像が存在しない場合
   */
  def getUserFile(userId: String, imageId: String, size: Option[String]): Try[(java.io.File, String)] = {
    Try {
      CheckUtil.checkNull(userId, "userId")
      CheckUtil.checkNull(imageId, "imageId")
      CheckUtil.checkNull(size, "size")
      DB.readOnly { implicit s =>
        val user = persistence.User.find(userId)
        if (user.filter(_.imageId == imageId).isEmpty) {
          // ユーザが存在しないが、ユーザの画像が指定の画像IDと一致しない場合、画像が存在しないとして処理を打ち切る
          throw new NotFoundException
        }
      }
      getFile(imageId, size)
    }
  }

  /**
   * 指定されたデータセットの画像を取得します。
   *
   * @param datasetId 画像を持っているデータセットのID
   * @param imageId 取得する画像ID
   * @param size 取得する画像サイズ、Noneでオリジナル
   * @param user 画像を取得しようとしてるユーザ
   * @return
   *   Success(ファイルオブジェクトとファイル名のペア) 成功時
   *   Failure(NullPointerException) 引数がnullの場合
   *   Failure(NotFoundException) 対象画像が存在しない場合
   *   Failure(AccessDeniedException) 画像を取得しようとしているユーザに、対象データセットへのアクセス権限がない場合
   */
  def getDatasetFile(
    datasetId: String,
    imageId: String,
    size: Option[String],
    user: User
  ): Try[(java.io.File, String)] = {
    Try {
      CheckUtil.checkNull(datasetId, "datasetId")
      CheckUtil.checkNull(imageId, "imageId")
      CheckUtil.checkNull(size, "size")
      CheckUtil.checkNull(user, "user")
      DB.readOnly { implicit s =>
        if (!isRelatedToDataset(datasetId, imageId)) {
          // 指定した画像が、指定のデータセットのものではない場合、画像が存在しないとして処理を打ち切る
          throw new NotFoundException
        }
        val groups = getJoinedGroups(user)
        if (getPermission(datasetId, groups) == GroupAccessLevel.Deny) {
          // 指定したユーザが所属しているグループが、指定したデータセットに対してアクセス権を持っていない場合、アクセス権がないとして処理を打ち切る
          throw new AccessDeniedException(resource.getString(ResourceNames.NO_ACCESS_PERMISSION), Some(user))
        }
        getFile(imageId, size)
      }
    }
  }

  /**
   * 指定されたグループの画像を取得します。
   *
   * @param groupId 画像を持っているグループのID
   * @param imageId 取得する画像ID
   * @param size 取得する画像サイズ、Noneでオリジナル
   * @return
   *   Success(ファイルオブジェクトとファイル名のペア) 成功時
   *   Failure(NullPointerException) 引数がnullの場合
   *   Failure(NotFoundException) 対象画像が存在しない場合
   */
  def getGroupFile(groupId: String, imageId: String, size: Option[String]): Try[(java.io.File, String)] = {
    Try {
      CheckUtil.checkNull(groupId, "groupId")
      CheckUtil.checkNull(imageId, "imageId")
      CheckUtil.checkNull(size, "size")
      DB.readOnly { implicit s =>
        if (!isRelatedToGroup(groupId, imageId)) {
          // 指定した画像が、指定のグループのものではない場合、画像が存在しないとして処理を打ち切る
          throw new NotFoundException
        }
        getFile(imageId, size)
      }
    }
  }

  /**
   * 指定された画像IDのファイルを取得します。
   *
   * @param imageId 取得する画像ファイルのImageID
   * @param size 取得する画像サイズ、Noneでオリジナル
   * @return ファイルオブジェクトとファイル名のペア
   * @throws NotFoundException 対象画像IDがDBに存在しない、またはファイルが存在しない場合
   */
  def getFile(imageId: String, size: Option[String]): (java.io.File, String) = {
    val ret = for {
      image <- DB.readOnly { implicit s => persistence.Image.find(imageId) }
      file <- getImageFile(image, size)
    } yield {
      (file, image.name)
    }
    ret.getOrElse {
      // 対象画像IDがDBに存在しない、またはファイルが存在しない
      throw new NotFoundException
    }
  }
  /**
   * 指定された画像のファイルを取得します。
   *
   * @param image DB上の画像情報
   * @param size 取得する画像のサイズ、Noneでオリジナル
   * @return 画像ファイル、存在しない場合None
   */
  def getImageFile(image: persistence.Image, size: Option[String]): Option[java.io.File] = {
    // ファイルサイズ指定が合致すればその画像サイズ
    val fileName = size
      .filter(x => ImageSaveLogic.imageSizes.contains(x.toInt))
      .getOrElse(ImageSaveLogic.defaultFileName)
    val file = new java.io.File(
      Paths.get(AppConf.imageDir, image.filePath, fileName).toString
    )
    Option(file).filter(_.exists)
  }

  /**
   * ユーザの所属するグループ(Personal/Public問わず)のIDを取得する。
   *
   * @param user ユーザ情報
   * @param s DBセッション
   * @return 取得結果
   */
  def getJoinedGroups(user: User)(implicit s: DBSession): Seq[String] = {
    if (user.isGuest) {
      Seq.empty
    } else {
      val g = persistence.Group.syntax("g")
      val m = persistence.Member.syntax("m")
      withSQL {
        select(g.id)
          .from(persistence.Group as g)
          .innerJoin(persistence.Member as m).on(m.groupId, g.id)
          .where
          .eq(m.userId, sqls.uuid(user.id))
          .and
          .isNull(g.deletedAt)
          .and
          .isNull(m.deletedAt)
      }.map(_.string("id")).list.apply()
    }
  }

  /**
   * 指定されたDatasetに対する権限を取得します。
   *
   * @param id Dataset ID
   * @param groups 所属しているグループ
   * @return アクセスレベル (@see dsmoq.persistence.GroupAccessLevel)
   */
  private def getPermission(id: String, groups: Seq[String])(implicit s: DBSession): Int = {
    val o = persistence.Ownership.syntax("o")
    val g = persistence.Group.g
    val permissions = withSQL {
      select(o.result.accessLevel, g.result.groupType)
        .from(persistence.Ownership as o)
        .innerJoin(persistence.Group as g).on(o.groupId, g.id)
        .where
        .eq(o.datasetId, sqls.uuid(id))
        .and
        .inUuid(o.groupId, Seq.concat(groups, Seq(AppConf.guestGroupId)))
    }.map(rs => (rs.int(o.resultName.accessLevel), rs.int(g.resultName.groupType))).list.apply()
    // 上記のSQLではゲストユーザーは取得できないため、別途取得する必要がある
    val guestPermission = (getGuestAccessLevel(id), GroupType.Personal)
    // Provider権限のGroupはWriteできない
    (guestPermission :: permissions).map {
      case (accessLevel, groupType) =>
        if (accessLevel == GroupAccessLevel.Provider && groupType == GroupType.Public) {
          GroupAccessLevel.FullPublic
        } else {
          accessLevel
        }
    }.max
  }

  private def isRelatedToDataset(datasetId: String, imageId: String)(implicit s: DBSession): Boolean = {
    val di = persistence.DatasetImage.di
    val count = withSQL {
      select(sqls"count(1)")
        .from(persistence.DatasetImage as di)
        .where
        .eqUuid(di.imageId, imageId)
        .and
        .eqUuid(di.datasetId, datasetId)
    }.map(_.long(1)).single.apply().get
    count > 0
  }

  private def isRelatedToGroup(groupId: String, imageId: String)(implicit s: DBSession): Boolean = {
    val gi = persistence.GroupImage.gi
    val count = withSQL {
      select(sqls"count(1)")
        .from(persistence.GroupImage as gi)
        .where
        .eqUuid(gi.imageId, imageId)
        .and
        .eqUuid(gi.groupId, groupId)
    }.map(_.long(1)).single.apply().get
    count > 0
  }

  private def getGuestAccessLevel(datasetId: String)(implicit s: DBSession) = {
    val o = persistence.Ownership.syntax("o")
    withSQL {
      select(o.result.accessLevel)
        .from(persistence.Ownership as o)
        .where
        .eq(o.datasetId, sqls.uuid(datasetId))
        .and
        .eq(o.groupId, sqls.uuid(AppConf.guestGroupId))
        .and
        .isNull(o.deletedAt)
    }.map(_.int(o.resultName.accessLevel)).single.apply().getOrElse(GroupAccessLevel.Deny)
  }
}
