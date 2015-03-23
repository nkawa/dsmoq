package dsmoq.persistence

import scalikejdbc._
import scalikejdbc.SQLInterpolation._
import org.joda.time.{DateTime}
import PostgresqlHelper._

case class GoogleUser(
  id: String, 
  userId: String, 
  googleId: String, 
  createdBy: String, 
  createdAt: DateTime, 
  updatedBy: String, 
  updatedAt: DateTime, 
  deletedBy: Option[String] = None, 
  deletedAt: Option[DateTime] = None) {

  def save()(implicit session: DBSession = GoogleUser.autoSession): GoogleUser = GoogleUser.save(this)(session)

  def destroy()(implicit session: DBSession = GoogleUser.autoSession): Unit = GoogleUser.destroy(this)(session)

}
      

object GoogleUser extends SQLSyntaxSupport[GoogleUser] {

  override val tableName = "google_users"

  override val columns = Seq("id", "user_id", "google_id", "created_by", "created_at", "updated_by", "updated_at", "deleted_by", "deleted_at")

  def apply(gu: ResultName[GoogleUser])(rs: WrappedResultSet): GoogleUser = new GoogleUser(
    id = rs.string(gu.id),
    userId = rs.string(gu.userId),
    googleId = rs.string(gu.googleId),
    createdBy = rs.string(gu.createdBy),
    createdAt = rs.timestamp(gu.createdAt).toJodaDateTime,
    updatedBy = rs.string(gu.updatedBy),
    updatedAt = rs.timestamp(gu.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(gu.deletedBy),
    deletedAt = rs.timestampOpt(gu.deletedAt).map(_.toJodaDateTime)
  )
      
  val gu = GoogleUser.syntax("gu")

  override val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[GoogleUser] = {
    withSQL { 
      select.from(GoogleUser as gu).where.eq(gu.id, sqls.uuid(id))
    }.map(GoogleUser(gu.resultName)).single.apply()
  }

  def findByUserId(id: String)(implicit session: DBSession = autoSession): Option[GoogleUser] = {
    withSQL {
      select.from(GoogleUser as gu).where.eq(gu.userId, sqls.uuid(id))
    }.map(GoogleUser(gu.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[GoogleUser] = {
    withSQL(select.from(GoogleUser as gu)).map(GoogleUser(gu.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(GoogleUser as gu)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[GoogleUser] = {
    withSQL { 
      select.from(GoogleUser as gu).where.append(sqls"${where}")
    }.map(GoogleUser(gu.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL { 
      select(sqls"count(1)").from(GoogleUser as gu).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    id: String,
    userId: String,
    googleId: String,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None)(implicit session: DBSession = autoSession): GoogleUser = {
    withSQL {
      insert.into(GoogleUser).columns(
        column.id,
        column.userId,
        column.googleId,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
        sqls.uuid(id),
        sqls.uuid(userId),
        googleId,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(sqls.uuid),
        deletedAt
      )
    }.update.apply()

    GoogleUser(
      id = id,
      userId = userId,
      googleId = googleId,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt)
  }

  def save(entity: GoogleUser)(implicit session: DBSession = autoSession): GoogleUser = {
    withSQL { 
      update(GoogleUser).set(
        column.id -> entity.id,
        column.userId -> entity.userId,
        column.googleId -> entity.googleId,
        column.createdBy -> entity.createdBy,
        column.createdAt -> entity.createdAt,
        column.updatedBy -> entity.updatedBy,
        column.updatedAt -> entity.updatedAt,
        column.deletedBy -> entity.deletedBy,
        column.deletedAt -> entity.deletedAt
      ).where.eq(column.id, entity.id)
    }.update.apply()
    entity 
  }
        
  def destroy(entity: GoogleUser)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(GoogleUser).where.eq(column.id, entity.id) }.update.apply()
  }
        
}
