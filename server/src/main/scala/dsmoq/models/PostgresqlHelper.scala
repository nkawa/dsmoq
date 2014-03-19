package dsmoq.models

import scalikejdbc.SQLInterpolation._

/**
 * Created by terurou on 14/03/19.
 */
object PostgresqlHelper {

  implicit class UuidConditionSQLBuilder[A](val self: ConditionSQLBuilder[A]) extends AnyVal {
    def inByUuid(column: SQLSyntax, values: Seq[String]): ConditionSQLBuilder[A] = self.append(sqls.inByUuid(column, values))
    def notInByUuid(column: SQLSyntax, values: Seq[String]): ConditionSQLBuilder[A] = self.append(sqls.notInByUuid(column, values))
  }

  implicit class UuidSQLSyntax(val self: sqls.type) extends AnyVal {
    def uuid(value: String): SQLSyntax = sqls"UUID(${value})"

    def coalesce(column: SQLSyntax, value: Any): SQLSyntax = sqls"COALESCE(${column}, ${value})"

    def inByUuid(column: SQLSyntax, values: Seq[String]) = {
      if (values.nonEmpty) {
        sqls"${column} in ( ${sqls.join(values.map(x => sqls.uuid(x)), sqls",")} )"
      } else {
        sqls""
      }
    }

    def notInByUuid(column: SQLSyntax, values: Seq[String]) = {
      if (values.nonEmpty) {
        sqls"${column} not in ( ${sqls.join(values.map(x => sqls.uuid(x)), sqls",")} )"
      } else {
        sqls""
      }
    }
  }

}
