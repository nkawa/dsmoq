package dsmoq.persistence

import scalikejdbc._

object PostgresqlHelper {

  implicit class UuidConditionSQLBuilder[A](val self: ConditionSQLBuilder[A]) extends AnyVal {
    def inByUuid(column: SQLSyntax, values: Seq[String]): ConditionSQLBuilder[A] = self.append(sqls.inByUuid(column, values))
    def notInByUuid(column: SQLSyntax, values: Seq[String]): ConditionSQLBuilder[A] = self.append(sqls.notInByUuid(column, values))
    def lowerEq(column: SQLSyntax, value: String): ConditionSQLBuilder[A] = self.append(sqls.lowerEq(column, value))
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

    def lowerEq(column: SQLSyntax, value: String) = sqls"LOWER(${column}) = LOWER(${value})"
  }

}
