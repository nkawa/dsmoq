package dsmoq.models

import scalikejdbc._
import scalikejdbc.SQLInterpolation._
import org.joda.time.{DateTime}

case class User(
  id: Any, 
  name: String, 
  fullname: String, 
  organization: String, 
  title: String, 
  description: String, 
  passwordId: Option[Any] = None, 
  imageId: Any, 
  createdAt: DateTime, 
  updatedAt: DateTime, 
  deletedAt: Option[DateTime] = None, 
  createdBy: Any, 
  updatedBy: Any, 
  deletedBy: Option[Any] = None) {

  def save()(implicit session: DBSession = User.autoSession): User = User.save(this)(session)

  def destroy()(implicit session: DBSession = User.autoSession): Unit = User.destroy(this)(session)

}
      

object User extends SQLSyntaxSupport[User] {

  override val tableName = "users"

  override val columns = Seq("id", "name", "fullname", "organization", "title", "description", "password_id", "image_id", "created_at", "updated_at", "deleted_at", "created_by", "updated_by", "deleted_by")

  def apply(u: ResultName[User])(rs: WrappedResultSet): User = new User(
    id = rs.any(u.id),
    name = rs.string(u.name),
    fullname = rs.string(u.fullname),
    organization = rs.string(u.organization),
    title = rs.string(u.title),
    description = rs.string(u.description),
    passwordId = rs.anyOpt(u.passwordId),
    imageId = rs.any(u.imageId),
    createdAt = rs.timestamp(u.createdAt).toDateTime,
    updatedAt = rs.timestamp(u.updatedAt).toDateTime,
    deletedAt = rs.timestampOpt(u.deletedAt).map(_.toDateTime),
    createdBy = rs.any(u.createdBy),
    updatedBy = rs.any(u.updatedBy),
    deletedBy = rs.anyOpt(u.deletedBy)
  )
      
  val u = User.syntax("u")

  //val autoSession = AutoSession

  def find(id: Any)(implicit session: DBSession = autoSession): Option[User] = {
    withSQL { 
      select.from(User as u).where.eq(u.id, id)
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
    id: Any,
    name: String,
    fullname: String,
    organization: String,
    title: String,
    description: String,
    passwordId: Option[Any] = None,
    imageId: Any,
    createdAt: DateTime,
    updatedAt: DateTime,
    deletedAt: Option[DateTime] = None,
    createdBy: Any,
    updatedBy: Any,
    deletedBy: Option[Any] = None)(implicit session: DBSession = autoSession): User = {
    withSQL {
      insert.into(User).columns(
        column.id,
        column.name,
        column.fullname,
        column.organization,
        column.title,
        column.description,
        column.passwordId,
        column.imageId,
        column.createdAt,
        column.updatedAt,
        column.deletedAt,
        column.createdBy,
        column.updatedBy,
        column.deletedBy
      ).values(
        id,
        name,
        fullname,
        organization,
        title,
        description,
        passwordId,
        imageId,
        createdAt,
        updatedAt,
        deletedAt,
        createdBy,
        updatedBy,
        deletedBy
      )
    }.update.apply()

    User(
      id = id,
      name = name,
      fullname = fullname,
      organization = organization,
      title = title,
      description = description,
      passwordId = passwordId,
      imageId = imageId,
      createdAt = createdAt,
      updatedAt = updatedAt,
      deletedAt = deletedAt,
      createdBy = createdBy,
      updatedBy = updatedBy,
      deletedBy = deletedBy)
  }

  def save(entity: User)(implicit session: DBSession = autoSession): User = {
    withSQL { 
      update(User as u).set(
        u.id -> entity.id,
        u.name -> entity.name,
        u.fullname -> entity.fullname,
        u.organization -> entity.organization,
        u.title -> entity.title,
        u.description -> entity.description,
        u.passwordId -> entity.passwordId,
        u.imageId -> entity.imageId,
        u.createdAt -> entity.createdAt,
        u.updatedAt -> entity.updatedAt,
        u.deletedAt -> entity.deletedAt,
        u.createdBy -> entity.createdBy,
        u.updatedBy -> entity.updatedBy,
        u.deletedBy -> entity.deletedBy
      ).where.eq(u.id, entity.id)
    }.update.apply()
    entity 
  }
        
  def destroy(entity: User)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(User).where.eq(column.id, entity.id) }.update.apply()
  }
        
}
