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

case class Annotation(
  id: String,
  name: String,
  createdBy: String,
  createdAt: DateTime,
  updatedBy: String,
  updatedAt: DateTime,
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None
) {

  def save()(implicit session: DBSession = Annotation.autoSession): Annotation = Annotation.save(this)(session)

  def destroy()(implicit session: DBSession = Annotation.autoSession): Unit = Annotation.destroy(this)(session)

}

object Annotation extends SQLSyntaxSupport[Annotation] {

  override val tableName = "annotations"

  override val columns = Seq(
    "id", "name",
    "created_by", "created_at",
    "updated_by", "updated_at",
    "deleted_by", "deleted_at"
  )

  def apply(a: ResultName[Annotation])(rs: WrappedResultSet): Annotation = Annotation(
    id = rs.string(a.id),
    name = rs.string(a.name),
    createdBy = rs.string(a.createdBy),
    createdAt = rs.timestamp(a.createdAt).toJodaDateTime,
    updatedBy = rs.string(a.updatedBy),
    updatedAt = rs.timestamp(a.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(a.deletedBy),
    deletedAt = rs.timestampOpt(a.deletedAt).map(_.toJodaDateTime)
  )

  val a = Annotation.syntax("a")

  //val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[Annotation] = {
    withSQL {
      select.from(Annotation as a).where.eq(a.id, sqls.uuid(id))
    }.map(Annotation(a.resultName)).single.apply()
  }

  def findAll()(implicit session: DBSession = autoSession): List[Annotation] = {
    withSQL(select.from(Annotation as a)).map(Annotation(a.resultName)).list.apply()
  }

  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(Annotation as a)).map(rs => rs.long(1)).single.apply().get
  }

  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[Annotation] = {
    withSQL {
      select.from(Annotation as a).where.append(sqls"${where}")
    }.map(Annotation(a.resultName)).list.apply()
  }

  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL {
      select(sqls"count(1)").from(Annotation as a).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }

  def create(
    id: String,
    name: String,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None
  )(implicit session: DBSession = autoSession): Annotation = {
    withSQL {
      insert.into(Annotation)
        .columns(
          column.id,
          column.name,
          column.createdBy,
          column.createdAt,
          column.updatedBy,
          column.updatedAt,
          column.deletedBy,
          column.deletedAt
        ).values(
          sqls.uuid(id),
          name,
          sqls.uuid(createdBy),
          createdAt,
          sqls.uuid(updatedBy),
          updatedAt,
          deletedBy.map(sqls.uuid),
          deletedAt
        )
    }.update.apply()

    Annotation(
      id = id,
      name = name,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt
    )
  }

  def save(entity: Annotation)(implicit session: DBSession = autoSession): Annotation = {
    withSQL {
      update(Annotation as a).set(
        a.id -> entity.id,
        a.name -> entity.name,
        a.createdBy -> entity.createdBy,
        a.createdAt -> entity.createdAt,
        a.updatedBy -> entity.updatedBy,
        a.updatedAt -> entity.updatedAt,
        a.deletedBy -> entity.deletedBy,
        a.deletedAt -> entity.deletedAt
      ).where.eq(a.id, entity.id)
    }.update.apply()
    entity
  }

  def destroy(entity: Annotation)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(Annotation).where.eq(column.id, entity.id) }.update.apply()
  }

}
