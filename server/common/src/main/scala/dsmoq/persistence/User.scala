package dsmoq.persistence

import scalikejdbc._
import scalikejdbc.SQLInterpolation._
import org.joda.time.{DateTime}
import PostgresqlHelper._

case class User(
  id: String,
  name: String, 
  fullname: String, 
  organization: String, 
  title: String, 
  description: String, 
  imageId: String,
  createdBy: String,
  createdAt: DateTime, 
  updatedBy: String,
  updatedAt: DateTime, 
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None) {

  def save()(implicit session: DBSession = User.autoSession): User = User.save(this)(session)

  def destroy()(implicit session: DBSession = User.autoSession): Unit = User.destroy(this)(session)

}
      

object User extends SQLSyntaxSupport[User] {

  override val tableName = "users"

  override val columns = Seq("id", "name", "fullname", "organization", "title", "description", "image_id", "created_by", "created_at", "updated_by", "updated_at", "deleted_by", "deleted_at")

  def apply(u: ResultName[User])(rs: WrappedResultSet): User = new User(
    id = rs.string(u.id),
    name = rs.string(u.name),
    fullname = rs.string(u.fullname),
    organization = rs.string(u.organization),
    title = rs.string(u.title),
    description = rs.string(u.description),
    imageId = rs.string(u.imageId),
    createdBy = rs.string(u.createdBy),
    createdAt = rs.timestamp(u.createdAt).toJodaDateTime,
    updatedBy = rs.string(u.updatedBy),
    updatedAt = rs.timestamp(u.updatedAt).toJodaDateTime,
    deletedBy = rs.stringOpt(u.deletedBy),
    deletedAt = rs.timestampOpt(u.deletedAt).map(_.toJodaDateTime)
  )
      
  val u = User.syntax("u")

  //val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[User] = {
    withSQL { 
      select.from(User as u).where.eq(u.id, sqls.uuid(id))
    }.map(User(u.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[User] = {
    withSQL(select.from(User as u)).map(User(u.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(User as u)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[User] = {
    withSQL { 
      select.from(User as u).where.append(sqls"${where}")
    }.map(User(u.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL { 
      select(sqls"count(1)").from(User as u).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    id: String,
    name: String,
    fullname: String,
    organization: String,
    title: String,
    description: String,
    imageId: String,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None)(implicit session: DBSession = autoSession): User = {
    withSQL {
      insert.into(User).columns(
        column.id,
        column.name,
        column.fullname,
        column.organization,
        column.title,
        column.description,
        column.imageId,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
        sqls.uuid(id),
        name,
        fullname,
        organization,
        title,
        description,
        sqls.uuid(imageId),
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(sqls.uuid),
        deletedAt
      )
    }.update.apply()

    User(
      id = id,
      name = name,
      fullname = fullname,
      organization = organization,
      title = title,
      description = description,
      imageId = imageId,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt)
  }

  def save(entity: User)(implicit session: DBSession = autoSession): User = {
    withSQL { 
      update(User).set(
        column.id -> sqls.uuid(entity.id),
        column.name -> entity.name,
        column.fullname -> entity.fullname,
        column.organization -> entity.organization,
        column.title -> entity.title,
        column.description -> entity.description,
        column.imageId -> sqls.uuid(entity.imageId),
        column.createdBy -> sqls.uuid(entity.createdBy),
        column.createdAt -> entity.createdAt,
        column.updatedBy -> sqls.uuid(entity.updatedBy),
        column.updatedAt -> entity.updatedAt,
        column.deletedBy -> entity.deletedBy.map(sqls.uuid),
        column.deletedAt -> entity.deletedAt
      ).where.eqUuid(column.id, entity.id)
    }.update.apply()
    entity 
  }
        
  def destroy(entity: User)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(User).where.eq(column.id, entity.id) }.update.apply()
  }
        
}
