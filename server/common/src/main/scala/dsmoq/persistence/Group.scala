package dsmoq.persistence

import org.joda.time.DateTime

import PostgresqlHelper.PgSQLSyntaxType
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

case class Group(
    id: String,
    name: String,
    description: String,
    groupType: Int,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None) {

  def save()(implicit session: DBSession = Group.autoSession): Group = Group.save(this)(session)

  def destroy()(implicit session: DBSession = Group.autoSession): Unit = Group.destroy(this)(session)

}

object Group extends SQLSyntaxSupport[Group] {

  override val tableName = "groups"

  override val columns = Seq(
    "id", "name", "description", "group_type",
    "created_by", "created_at",
    "updated_by", "updated_at",
    "deleted_by", "deleted_at"
  )

  def apply(g: ResultName[Group])(rs: WrappedResultSet): Group = Group(
    id = rs.string(g.id),
    name = rs.string(g.name),
    description = rs.string(g.description),
    groupType = rs.int(g.groupType),
    createdBy = rs.string(g.createdBy),
    createdAt = rs.timestamp(g.createdAt).toJodaDateTime,
    updatedBy = rs.string(g.updatedBy),
    updatedAt = rs.timestamp(g.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(g.deletedBy),
    deletedAt = rs.timestampOpt(g.deletedAt).map(_.toJodaDateTime)
  )

  val g = Group.syntax("g")

  //val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[Group] = {
    withSQL {
      select.from(Group as g).where.eq(g.id, sqls.uuid(id))
    }.map(Group(g.resultName)).single.apply()
  }

  def findAll()(implicit session: DBSession = autoSession): List[Group] = {
    withSQL(select.from(Group as g)).map(Group(g.resultName)).list.apply()
  }

  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(Group as g)).map(rs => rs.long(1)).single.apply().get
  }

  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[Group] = {
    withSQL {
      select.from(Group as g).where.append(sqls"${where}")
    }.map(Group(g.resultName)).list.apply()
  }

  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL {
      select(sqls"count(1)").from(Group as g).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }

  def create(
    id: String,
    name: String,
    description: String,
    groupType: Int,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None)(implicit session: DBSession = autoSession): Group = {
    withSQL {
      insert.into(Group).columns(
        column.id,
        column.name,
        column.description,
        column.groupType,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
          sqls.uuid(id),
          name,
          description,
          groupType,
          sqls.uuid(createdBy),
          createdAt,
          sqls.uuid(updatedBy),
          updatedAt,
          deletedBy.map(x => sqls.uuid(x)),
          deletedAt
        )
    }.update.apply()

    Group(
      id = id,
      name = name,
      description = description,
      groupType = groupType,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt)
  }

  def save(entity: Group)(implicit session: DBSession = autoSession): Group = {
    withSQL {
      update(Group as g).set(
        g.id -> entity.id,
        g.name -> entity.name,
        g.description -> entity.description,
        g.groupType -> entity.groupType,
        g.createdBy -> entity.createdBy,
        g.createdAt -> entity.createdAt,
        g.updatedBy -> entity.updatedBy,
        g.updatedAt -> entity.updatedAt,
        g.deletedBy -> entity.deletedBy,
        g.deletedAt -> entity.deletedAt
      ).where.eq(g.id, entity.id)
    }.update.apply()
    entity
  }

  def destroy(entity: Group)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(Group).where.eq(column.id, entity.id) }.update.apply()
  }

}
