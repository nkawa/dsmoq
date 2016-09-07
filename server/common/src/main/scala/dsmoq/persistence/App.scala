package dsmoq.persistence

import org.joda.time.DateTime

import PostgresqlHelper.PgSQLSyntaxType
import scalikejdbc.DBSession
import scalikejdbc.ResultName
import scalikejdbc.ResultName
import scalikejdbc.SQLSyntax
import scalikejdbc.SQLSyntaxSupport
import scalikejdbc.SQLSyntaxSupport
import scalikejdbc.SyntaxProvider
import scalikejdbc.SyntaxProvider
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

case class App(
  id: String,
  name: String,
  createdBy: String,
  createdAt: DateTime,
  updatedBy: String,
  updatedAt: DateTime,
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None
) {

  def save()(implicit session: DBSession = App.autoSession): App = App.save(this)(session)

  def destroy()(implicit session: DBSession = App.autoSession): Unit = App.destroy(this)(session)

}

object App extends SQLSyntaxSupport[App] {

  override val tableName = "apps"

  override val columns = Seq(
    "id", "name",
    "created_by", "created_at",
    "updated_by", "updated_at",
    "deleted_by", "deleted_at"
  )

  def apply(a: SyntaxProvider[App])(rs: WrappedResultSet): App = apply(a.resultName)(rs)

  def apply(a: ResultName[App])(rs: WrappedResultSet): App = App(
    id = rs.string(a.id),
    name = rs.string(a.name),
    createdBy = rs.string(a.createdBy),
    createdAt = rs.timestamp(a.createdAt).toJodaDateTime,
    updatedBy = rs.string(a.updatedBy),
    updatedAt = rs.timestamp(a.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(a.deletedBy),
    deletedAt = rs.timestampOpt(a.deletedAt).map(_.toJodaDateTime)
  )

  val a = App.syntax("a")

  //val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[App] = {
    withSQL {
      select.from(App as a).where.eq(a.id, sqls.uuid(id))
    }.map(App(a.resultName)).single.apply()
  }

  def findAll()(implicit session: DBSession = autoSession): List[App] = {
    withSQL(select.from(App as a)).map(App(a.resultName)).list.apply()
  }

  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(App as a)).map(rs => rs.long(1)).single.apply().get
  }

  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[App] = {
    withSQL {
      select.from(App as a).where.append(sqls"${where}")
    }.map(App(a.resultName)).list.apply()
  }

  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL {
      select(sqls"count(1)").from(App as a).where.append(sqls"${where}")
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
  )(implicit session: DBSession = autoSession): App = {
    withSQL {
      insert.into(App).columns(
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

    App(
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

  def save(entity: App)(implicit session: DBSession = autoSession): App = {
    withSQL {
      update(App as a).set(
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

  def destroy(entity: App)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(App).where.eq(column.id, entity.id) }.update.apply()
  }

}
