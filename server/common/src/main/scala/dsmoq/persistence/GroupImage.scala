package dsmoq.persistence

import org.joda.time.DateTime

import PostgresqlHelper.PgSQLSyntaxType
import scalikejdbc.AutoSession
import scalikejdbc.DBSession
import scalikejdbc.ResultName
import scalikejdbc.ResultName
import scalikejdbc.SQLSyntax
import scalikejdbc.SQLSyntaxSupport
import scalikejdbc.SQLSyntaxSupport
import scalikejdbc.WrappedResultSet
import scalikejdbc.convertJavaSqlTimestampToConverter
import scalikejdbc.delete
import scalikejdbc.insert
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef
import scalikejdbc.scalikejdbcSQLSyntaxToStringImplicitDef
import scalikejdbc.select
import scalikejdbc.sqls
import scalikejdbc.update
import scalikejdbc.withSQL

case class GroupImage(
  id: String,
  groupId: String,
  imageId: String,
  isPrimary: Boolean,
  createdBy: String,
  createdAt: DateTime,
  updatedBy: String,
  updatedAt: DateTime,
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None
) {

  def save()(implicit session: DBSession = GroupImage.autoSession): GroupImage = GroupImage.save(this)(session)

  def destroy()(implicit session: DBSession = GroupImage.autoSession): Unit = GroupImage.destroy(this)(session)

}

object GroupImage extends SQLSyntaxSupport[GroupImage] {

  override val tableName = "group_images"

  override val columns = Seq(
    "id", "group_id", "image_id", "is_primary",
    "created_by", "created_at",
    "updated_by", "updated_at",
    "deleted_by", "deleted_at"
  )

  def apply(gi: ResultName[GroupImage])(rs: WrappedResultSet): GroupImage = GroupImage(
    id = rs.string(gi.id),
    groupId = rs.string(gi.groupId),
    imageId = rs.string(gi.imageId),
    isPrimary = rs.boolean(gi.isPrimary),
    createdBy = rs.string(gi.createdBy),
    createdAt = rs.timestamp(gi.createdAt).toJodaDateTime,
    updatedBy = rs.string(gi.updatedBy),
    updatedAt = rs.timestamp(gi.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(gi.deletedBy),
    deletedAt = rs.timestampOpt(gi.deletedAt).map(_.toJodaDateTime)
  )

  val gi = GroupImage.syntax("gi")

  override val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[GroupImage] = {
    withSQL {
      select.from(GroupImage as gi).where.eq(gi.id, sqls.uuid(id))
    }.map(GroupImage(gi.resultName)).single.apply()
  }

  def findAll()(implicit session: DBSession = autoSession): List[GroupImage] = {
    withSQL(select.from(GroupImage as gi)).map(GroupImage(gi.resultName)).list.apply()
  }

  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(GroupImage as gi)).map(rs => rs.long(1)).single.apply().get
  }

  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[GroupImage] = {
    withSQL {
      select.from(GroupImage as gi).where.append(sqls"${where}")
    }.map(GroupImage(gi.resultName)).list.apply()
  }

  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL {
      select(sqls"count(1)").from(GroupImage as gi).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }

  def create(
    id: String,
    groupId: String,
    imageId: String,
    isPrimary: Boolean,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None
  )(implicit session: DBSession = autoSession): GroupImage = {
    withSQL {
      insert.into(GroupImage).columns(
        column.id,
        column.groupId,
        column.imageId,
        column.isPrimary,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
        sqls.uuid(id),
        sqls.uuid(groupId),
        sqls.uuid(imageId),
        isPrimary,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(sqls.uuid),
        deletedAt
      )
    }.update.apply()

    GroupImage(
      id = id,
      groupId = groupId,
      imageId = imageId,
      isPrimary = isPrimary,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt
    )
  }

  def save(entity: GroupImage)(implicit session: DBSession = autoSession): GroupImage = {
    withSQL {
      update(GroupImage).set(
        column.id -> sqls.uuid(entity.id),
        column.groupId -> sqls.uuid(entity.groupId),
        column.imageId -> sqls.uuid(entity.imageId),
        column.isPrimary -> entity.isPrimary,
        column.createdBy -> sqls.uuid(entity.createdBy),
        column.createdAt -> entity.createdAt,
        column.updatedBy -> sqls.uuid(entity.updatedBy),
        column.updatedAt -> entity.updatedAt,
        column.deletedBy -> entity.deletedBy.map(sqls.uuid),
        column.deletedAt -> entity.deletedAt
      ).where.eq(column.id, sqls.uuid(entity.id))
    }.update.apply()
    entity
  }

  def destroy(entity: GroupImage)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(GroupImage).where.eq(column.id, sqls.uuid(entity.id)) }.update.apply()
  }

}
