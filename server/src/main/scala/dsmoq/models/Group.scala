package dsmoq.models

import scalikejdbc._
import scalikejdbc.SQLInterpolation._
import org.joda.time.{DateTime}

case class Group(
  id: String,
  name: String, 
  description: String, 
  dataType: Int, 
  createdAt: DateTime, 
  updatedAt: DateTime, 
  deletedAt: Option[DateTime] = None, 
  createdBy: Any, 
  updatedBy: Any, 
  deletedBy: Option[Any] = None) {

  def save()(implicit session: DBSession = Group.autoSession): Group = Group.save(this)(session)

  def destroy()(implicit session: DBSession = Group.autoSession): Unit = Group.destroy(this)(session)

}
      

object Group extends SQLSyntaxSupport[Group] {

  override val tableName = "groups"

  override val columns = Seq("id", "name", "description", "data_type", "created_at", "updated_at", "deleted_at", "created_by", "updated_by", "deleted_by")

  def apply(g: ResultName[Group])(rs: WrappedResultSet): Group = new Group(
    id = rs.string(g.id),
    name = rs.string(g.name),
    description = rs.string(g.description),
    dataType = rs.int(g.dataType),
    createdAt = rs.timestamp(g.createdAt).toDateTime,
    updatedAt = rs.timestamp(g.updatedAt).toDateTime,
    deletedAt = rs.timestampOpt(g.deletedAt).map(_.toDateTime),
    createdBy = rs.any(g.createdBy),
    updatedBy = rs.any(g.updatedBy),
    deletedBy = rs.anyOpt(g.deletedBy)
  )
      
  val g = Group.syntax("g")

  //val autoSession = AutoSession

  def find(id: Any)(implicit session: DBSession = autoSession): Option[Group] = {
    withSQL { 
      select.from(Group as g).where.eq(g.id, id)
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
    dataType: Int,
    createdAt: DateTime,
    updatedAt: DateTime,
    deletedAt: Option[DateTime] = None,
    createdBy: Any,
    updatedBy: Any,
    deletedBy: Option[Any] = None)(implicit session: DBSession = autoSession): Group = {
    withSQL {
      insert.into(Group).columns(
        column.id,
        column.name,
        column.description,
        column.dataType,
        column.createdAt,
        column.updatedAt,
        column.deletedAt,
        column.createdBy,
        column.updatedBy,
        column.deletedBy
      ).values(
        id,
        name,
        description,
        dataType,
        createdAt,
        updatedAt,
        deletedAt,
        createdBy,
        updatedBy,
        deletedBy
      )
    }.update.apply()

    Group(
      id = id,
      name = name,
      description = description,
      dataType = dataType,
      createdAt = createdAt,
      updatedAt = updatedAt,
      deletedAt = deletedAt,
      createdBy = createdBy,
      updatedBy = updatedBy,
      deletedBy = deletedBy)
  }

  def save(entity: Group)(implicit session: DBSession = autoSession): Group = {
    withSQL { 
      update(Group as g).set(
        g.id -> entity.id,
        g.name -> entity.name,
        g.description -> entity.description,
        g.dataType -> entity.dataType,
        g.createdAt -> entity.createdAt,
        g.updatedAt -> entity.updatedAt,
        g.deletedAt -> entity.deletedAt,
        g.createdBy -> entity.createdBy,
        g.updatedBy -> entity.updatedBy,
        g.deletedBy -> entity.deletedBy
      ).where.eq(g.id, entity.id)
    }.update.apply()
    entity 
  }
        
  def destroy(entity: Group)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(Group).where.eq(column.id, entity.id) }.update.apply()
  }
        
}
