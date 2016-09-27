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

case class CustomQuery(
  id: String,
  userId: String,
  name: String,
  query: String,
  createdBy: String,
  createdAt: DateTime
) {

  def save()(implicit session: DBSession = CustomQuery.autoSession): CustomQuery = CustomQuery.save(this)(session)

  def destroy()(implicit session: DBSession = CustomQuery.autoSession): Unit = CustomQuery.destroy(this)(session)

}

object CustomQuery extends SQLSyntaxSupport[CustomQuery] {

  override val tableName = "custom_queries"

  override val columns = Seq(
    "id", "user_id", "name", "query",
    "created_by", "created_at"
  )

  def apply(q: ResultName[CustomQuery])(rs: WrappedResultSet): CustomQuery = CustomQuery(
    id = rs.string(q.id),
    userId = rs.string(q.userId),
    name = rs.string(q.name),
    query = rs.string(q.query),
    createdBy = rs.string(q.createdBy),
    createdAt = rs.timestamp(q.createdAt).toJodaDateTime
  )

  val q = CustomQuery.syntax("q")

  //val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[CustomQuery] = {
    withSQL {
      select.from(CustomQuery as q).where.eq(q.id, sqls.uuid(id))
    }.map(CustomQuery(q.resultName)).single.apply()
  }

  def findAll()(implicit session: DBSession = autoSession): List[CustomQuery] = {
    withSQL(select.from(CustomQuery as q)).map(CustomQuery(q.resultName)).list.apply()
  }

  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(CustomQuery as q)).map(rs => rs.long(1)).single.apply().get
  }

  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[CustomQuery] = {
    withSQL {
      select.from(CustomQuery as q).where.append(sqls"${where}")
    }.map(CustomQuery(q.resultName)).list.apply()
  }

  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL {
      select(sqls"count(1)").from(CustomQuery as q).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }

  def create(
    id: String,
    userId: String,
    name: String,
    query: String,
    createdBy: String,
    createdAt: DateTime
  )(implicit session: DBSession = autoSession): CustomQuery = {
    withSQL {
      insert.into(CustomQuery).columns(
        column.id,
        column.userId,
        column.name,
        column.query,
        column.createdBy,
        column.createdAt
      ).values(
        sqls.uuid(id),
        sqls.uuid(userId),
        name,
        query,
        sqls.uuid(createdBy),
        createdAt
      )
    }.update.apply()

    CustomQuery(
      id = id,
      userId = userId,
      name = name,
      query = query,
      createdBy = createdBy,
      createdAt = createdAt
    )
  }

  def save(entity: CustomQuery)(implicit session: DBSession = autoSession): CustomQuery = {
    withSQL {
      update(CustomQuery).set(
        column.id -> sqls.uuid(entity.id),
        column.userId -> sqls.uuid(entity.userId),
        column.name -> entity.name,
        column.query -> entity.query,
        column.createdBy -> sqls.uuid(entity.createdBy),
        column.createdAt -> entity.createdAt
      ).where.eq(column.id, sqls.uuid(entity.id))
    }.update.apply()
    entity
  }

  def destroy(entity: CustomQuery)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(CustomQuery).where.eq(column.id, entity.id) }.update.apply()
  }

}
