package dsmoq.persistence

import scalikejdbc._
import scalikejdbc.SQLInterpolation._
import org.joda.time.{DateTime}
import PostgresqlHelper._

case class Member(
  id: String,
  groupId: String,
  userId: String,
  role: Int, 
  status: Int, 
  createdBy: String,
  createdAt: DateTime, 
  updatedBy: String,
  updatedAt: DateTime, 
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None) {

  def save()(implicit session: DBSession = Member.autoSession): Member = Member.save(this)(session)

  def destroy()(implicit session: DBSession = Member.autoSession): Unit = Member.destroy(this)(session)

}
      

object Member extends SQLSyntaxSupport[Member] {

  override val tableName = "members"

  override val columns = Seq("id", "group_id", "user_id", "role", "status", "created_by", "created_at", "updated_by", "updated_at", "deleted_by", "deleted_at")

  def apply(m: ResultName[Member])(rs: WrappedResultSet): Member = new Member(
    id = rs.string(m.id),
    groupId = rs.string(m.groupId),
    userId = rs.string(m.userId),
    role = rs.int(m.role),
    status = rs.int(m.status),
    createdBy = rs.string(m.createdBy),
    createdAt = rs.timestamp(m.createdAt).toJodaDateTime,
    updatedBy = rs.string(m.updatedBy),
    updatedAt = rs.timestamp(m.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(m.deletedBy),
    deletedAt = rs.timestampOpt(m.deletedAt).map(_.toJodaDateTime)
  )
      
  val m = Member.syntax("m")

  //val autoSession = AutoSession

  def find(id: Any)(implicit session: DBSession = autoSession): Option[Member] = {
    withSQL { 
      select.from(Member as m).where.eq(m.id, id)
    }.map(Member(m.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[Member] = {
    withSQL(select.from(Member as m)).map(Member(m.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(Member as m)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[Member] = {
    withSQL { 
      select.from(Member as m).where.append(sqls"${where}")
    }.map(Member(m.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL { 
      select(sqls"count(1)").from(Member as m).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    id: String,
    groupId: String,
    userId: String,
    role: Int,
    status: Int,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None)(implicit session: DBSession = autoSession): Member = {
    withSQL {
      insert.into(Member).columns(
        column.id,
        column.groupId,
        column.userId,
        column.role,
        column.status,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
        sqls.uuid(id),
        sqls.uuid(groupId),
        sqls.uuid(userId),
        role,
        status,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(sqls.uuid),
        deletedAt
      )
    }.update.apply()

    Member(
      id = id,
      groupId = groupId,
      userId = userId,
      role = role,
      status = status,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt)
  }

  def save(entity: Member)(implicit session: DBSession = autoSession): Member = {
    withSQL { 
      update(Member as m).set(
        m.id -> entity.id,
        m.groupId -> entity.groupId,
        m.userId -> entity.userId,
        m.role -> entity.role,
        m.status -> entity.status,
        m.createdBy -> entity.createdBy,
        m.createdAt -> entity.createdAt,
        m.updatedBy -> entity.updatedBy,
        m.updatedAt -> entity.updatedAt,
        m.deletedBy -> entity.deletedBy,
        m.deletedAt -> entity.deletedAt
      ).where.eq(m.id, entity.id)
    }.update.apply()
    entity 
  }
        
  def destroy(entity: Member)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(Member).where.eq(column.id, entity.id) }.update.apply()
  }
        
}
