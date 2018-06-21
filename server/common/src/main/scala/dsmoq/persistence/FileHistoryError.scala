package dsmoq.persistence

import dsmoq.persistence.PostgresqlHelper.PgSQLSyntaxType
import org.joda.time.DateTime
import scalikejdbc.{ DBSession, ResultName, SQLSyntax, SQLSyntaxSupport, WrappedResultSet, delete, insert, scalikejdbcSQLInterpolationImplicitDef, scalikejdbcSQLSyntaxToStringImplicitDef, select, sqls, update, withSQL }

case class FileHistoryError(
  id: String,
  historyId: String,
  createdBy: String,
  createdAt: DateTime,
  updatedBy: String,
  updatedAt: DateTime,
  deletedBy: Option[String] = None,
  deletedAt: Option[DateTime] = None
) {

  def save()(implicit session: DBSession = FileHistoryError.autoSession): FileHistoryError = FileHistoryError.save(this)(session)

  def destroy()(implicit session: DBSession = FileHistoryError.autoSession): Unit = FileHistoryError.destroy(this)(session)

}

object FileHistoryError extends SQLSyntaxSupport[FileHistoryError] {

  override val tableName = "file_history_errors"

  override val columns = Seq(
    "id", "history_id",
    "created_by", "created_at",
    "updated_by", "updated_at",
    "deleted_by", "deleted_at"
  )

  def apply(fhe: ResultName[FileHistoryError])(rs: WrappedResultSet): FileHistoryError = new FileHistoryError(
    id = rs.get(fhe.id),
    historyId = rs.get(fhe.historyId),
    createdBy = rs.get(fhe.createdBy),
    createdAt = rs.get(fhe.createdAt),
    updatedBy = rs.get(fhe.updatedBy),
    updatedAt = rs.get(fhe.updatedAt),
    deletedBy = rs.get(fhe.deletedBy),
    deletedAt = rs.get(fhe.deletedAt)
  )

  val fhe = FileHistoryError.syntax("fhe")

  def find(id: String)(implicit session: DBSession = autoSession): Option[FileHistoryError] = {
    withSQL {
      select.from(FileHistoryError as fhe).where.eq(fhe.id, sqls.uuid(id))
    }.map(FileHistoryError(fhe.resultName)).single.apply()
  }

  def findAll()(implicit session: DBSession = autoSession): List[FileHistoryError] = {
    withSQL(select.from(FileHistoryError as fhe)).map(FileHistoryError(fhe.resultName)).list.apply()
  }

  def countAll()(implicit session: DBSession = autoSession): Long = {
    withSQL(select(sqls"count(1)").from(FileHistoryError as fhe)).map(rs => rs.long(1)).single.apply().get
  }

  def findAllBy(where: SQLSyntax)(implicit session: DBSession = autoSession): List[FileHistoryError] = {
    withSQL {
      select.from(FileHistoryError as fhe).where.append(sqls"${where}")
    }.map(FileHistoryError(fhe.resultName)).list.apply()
  }

  def countBy(where: SQLSyntax)(implicit session: DBSession = autoSession): Long = {
    withSQL {
      select(sqls"count(1)").from(FileHistoryError as fhe).where.append(sqls"${where}")
    }.map(_.long(1)).single.apply().get
  }

  def create(
    id: String,
    historyId: String,
    createdBy: String,
    createdAt: DateTime,
    updatedBy: String,
    updatedAt: DateTime,
    deletedBy: Option[String] = None,
    deletedAt: Option[DateTime] = None
  )(implicit session: DBSession = autoSession): FileHistoryError = {
    withSQL {
      insert.into(FileHistoryError).columns(
        column.id,
        column.historyId,
        column.createdBy,
        column.createdAt,
        column.updatedBy,
        column.updatedAt,
        column.deletedBy,
        column.deletedAt
      ).values(
        sqls.uuid(id),
        sqls.uuid(historyId),
        sqls.uuid(createdBy),
        createdAt,
        sqls.uuid(updatedBy),
        updatedAt,
        deletedBy.map(x => sqls.uuid(x)),
        deletedAt
      )
    }.update.apply()

    FileHistoryError(
      id = id,
      historyId = historyId,
      createdBy = createdBy,
      createdAt = createdAt,
      updatedBy = updatedBy,
      updatedAt = updatedAt,
      deletedBy = deletedBy,
      deletedAt = deletedAt
    )
  }

  def save(entity: FileHistoryError)(implicit session: DBSession = autoSession): FileHistoryError = {
    withSQL {
      update(FileHistoryError).set(
        column.id -> sqls.uuid(entity.id),
        column.historyId -> sqls.uuid(entity.historyId),
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

  def destroy(entity: FileHistoryError)(implicit session: DBSession = autoSession): Unit = {
    withSQL { delete.from(FileHistoryError).where.eq(column.id, sqls.uuid(entity.id)) }.update.apply()
  }

}
