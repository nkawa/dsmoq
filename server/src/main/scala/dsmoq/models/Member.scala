package dsmoq.models

import scalikejdbc._
import scalikejdbc.SQLInterpolation._
import org.joda.time.{DateTime}

case class Member(
  id: Any, 
  groupId: Any, 
  userId: Any, 
  role: Int, 
  status: Int, 
  createdAt: DateTime, 
  updatedAt: DateTime, 
  deletedAt: Option[DateTime] = None, 
  createdBy: Any, 
  updatedBy: Any, 
  deletedBy: Option[Any] = None) {

  def save()(implicit session: DBSession = Member.autoSession): Member = Member.save(this)(session)

  def destroy()(implicit session: DBSession = Member.autoSession): Unit = Member.destroy(this)(session)

}
      

object Member extends SQLSyntaxSupport[Member] {

  override val tableName = "members"

  override val columns = Seq("id", "group_id", "user_id", "role", "status", "created_at", "updated_at", "deleted_at", "created_by", "updated_by", "deleted_by")

  def apply(m: ResultName[Member])(rs: WrappedResultSet): Member = new Member(
    id = rs.any(m.id),
    groupId = rs.any(m.groupId),
    userId = rs.any(m.userId),
    role = rs.int(m.role),
    status = rs.int(m.status),
    createdAt = rs.timestamp(m.createdAt).toDateTime,
    updatedAt = rs.timestamp(m.updatedAt).toDateTime,
    deletedAt = rs.timestampOpt(m.deletedAt).map(_.toDateTime),
    createdBy = rs.any(m.createdBy),
    updatedBy = rs.any(m.updatedBy),
    deletedBy = rs.anyOpt(m.deletedBy)
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
    id: Any,
    groupId: Any,
    userId: Any,
    role: Int,
    status: Int,
    createdAt: DateTime,
    updatedAt: DateTime,
    deletedAt: Option[DateTime] = None,
    createdBy: Any,
    updatedBy: Any,
    deletedBy: Option[Any] = None)(implicit session: DBSession = autoSession): Member = {
    withSQL {
      insert.into(Member).columns(
        column.id,
        column.groupId,
        column.userId,
        column.role,
        column.status,
        column.createdAt,
        column.updatedAt,
        column.deletedAt,
        column.createdBy,
        column.updatedBy,
        column.deletedBy
      ).values(
        id,
        groupId,
        userId,
        role,
        status,
        createdAt,
        updatedAt,
        deletedAt,
        createdBy,
        updatedBy,
        deletedBy
      )
    }.update.apply()

    Member(
      id = id,
      groupId = groupId,
      userId = userId,
      role = role,
      status = status,
      createdAt = createdAt,
      updatedAt = updatedAt,
      deletedAt = deletedAt,
      createdBy = createdBy,
      updatedBy = updatedBy,
      deletedBy = deletedBy)
  }

  def save(entity: Member)(implicit session: DBSession = autoSession): Member = {
    withSQL { 
      update(Member as m).set(
        m.id -> entity.id,
        m.groupId -> entity.groupId,
        m.userId -> entity.userId,
        m.role -> entity.role,
        m.status -> entity.status,
        m.createdAt -> entity.createdAt,
        m.updatedAt -> entity.updatedAt,
        m.deletedAt -> entity.deletedAt,
        m.createdBy -> entity.createdBy,
        m.updatedBy -> entity.updatedBy,
        m.deletedBy -> entity.deletedBy
      ).where.eq(m.id, entity.id)
    }.update.apply()
    entity 
  }
        
  def destroy(entity: Member)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(Member).where.eq(column.id, entity.id) }.update.apply()
  }
        
}
