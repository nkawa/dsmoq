package dsmoq.persistence

import scalikejdbc._
import scalikejdbc.SQLInterpolation._
import org.joda.time.{DateTime}
import PostgresqlHelper._

case class Image(
  id: String,
  name: String, 
  width: Int, 
  height: Int, 
  createdBy: String,
  createdAt: DateTime, 
  updatedBy: String,
  updatedAt: DateTime, 
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None) {

  def save()(implicit session: DBSession = Image.autoSession): Image = Image.save(this)(session)

  def destroy()(implicit session: DBSession = Image.autoSession): Unit = Image.destroy(this)(session)

}
      

object Image extends SQLSyntaxSupport[Image] {

  override val tableName = "images"

  override val columns = Seq("id", "name", "width", "height", "created_by", "created_at", "updated_by", "updated_at", "deleted_by", "deleted_at")

  def apply(i: ResultName[Image])(rs: WrappedResultSet): Image = new Image(
    id = rs.string(i.id),
    name = rs.string(i.name),
    width = rs.int(i.width),
    height = rs.int(i.height),
    createdBy = rs.string(i.createdBy),
    createdAt = rs.timestamp(i.createdAt).toDateTime,
    updatedBy = rs.string(i.updatedBy),
    updatedAt = rs.timestamp(i.updatedAt).toDateTime,
    deletedBy = rs.stringOpt(i.deletedBy),
    deletedAt = rs.timestampOpt(i.deletedAt).map(_.toDateTime)
  )
      
  val i = Image.syntax("i")

  override val autoSession = AutoSession

  def find(id: String)(implicit session: DBSession = autoSession): Option[Image] = {
    withSQL { 
      select.from(Image as i).where.eq(i.id, id)
    }.map(Image(i.resultName)).single.apply()
  }
          
  def findAll()(implicit session: DBSession = autoSession): List[Image] = {
    withSQL(select.from(Image as i)).map(Image(i.resultName)).list.apply()
  }
          
  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(Image as i)).map(rs => rs.long(1)).single.apply().get
  }
          
  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[Image] = {
    withSQL { 
      select.from(Image as i).where.append(sqls"${where}")
    }.map(Image(i.resultName)).list.apply()
  }
      
  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL { 
      select(sqls"count(1)").from(Image as i).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }
      
  def create(
    id: String,
    name: String,
    width: Int,
    height: Int,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None)(implicit session: DBSession = autoSession): Image = {
    withSQL {
      insert.into(Image).columns(
        column.id,
        column.name,
        column.width,
        column.height,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
        sqls.uuid(id),
        name,
        width,
        height,
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(sqls.uuid),
        deletedAt
      )
    }.update.apply()

    Image(
      id = id,
      name = name,
      width = width,
      height = height,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt)
  }

  def save(entity: Image)(implicit session: DBSession = autoSession): Image = {
    withSQL { 
      update(Image).set(
        column.id -> sqls.uuid(entity.id),
        column.name -> entity.name,
        column.width -> entity.width,
        column.height -> entity.height,
        column.createdBy -> sqls.uuid(entity.createdBy),
        column.createdAt -> entity.createdAt,
        column.updatedBy -> sqls.uuid(entity.updatedBy),
        column.updatedAt -> entity.updatedAt,
        column.deletedBy -> entity.deletedBy.map(sqls.uuid),
        column.deletedAt -> entity.deletedAt
      ).where.eq(column.id, sqls.uuid(entity.id))
    }.update.apply()
    entity 
  }
        
  def destroy(entity: Image)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(Image).where.eq(column.id, sqls.uuid(entity.id)) }.update.apply()
  }
        
}
