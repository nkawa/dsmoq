package dsmoq.persistence

import scalikejdbc._

object PostgresqlHelper {
  implicit class PgConditionSQLBuilder[A](val self: ConditionSQLBuilder[A]) extends AnyVal {
    def inUuid(column: SQLSyntax, values: Seq[String]): ConditionSQLBuilder[A] = self.append(sqls.inUuid(column, values))
    def notInUuid(column: SQLSyntax, values: Seq[String]): ConditionSQLBuilder[A] = self.append(sqls.notInUuid(column, values))
    def eqUuid(column: SQLSyntax, value: String): ConditionSQLBuilder[A] = self.append(sqls.eqUuid(column, value))
    def lowerEq(column: SQLSyntax, value: String): ConditionSQLBuilder[A] = self.append(sqls.lowerEq(column, value))
  }

  implicit class PgSQLSyntax(val self: sqls.type) extends AnyVal {
    def coalesce(column: SQLSyntax, value: Any): SQLSyntax = sqls"COALESCE(${column}, ${value})"

    def countDistinct(column: SQLSyntax) = sqls.count(sqls.distinct(column))

    def uuid(value: String): SQLSyntax = sqls"UUID(${value})"

    def eqUuid(column: SQLSyntax, value: String) = sqls.eq(column, sqls.uuid(value))

    def inUuid(column: SQLSyntax, values: Seq[String]) = {
      if (values.nonEmpty) {
        sqls"${column} in ( ${sqls.join(values.map(x => sqls.uuid(x)), sqls",")} )"
      } else {
        sqls""
      }
    }

    def notInUuid(column: SQLSyntax, values: Seq[String]) = {
      if (values.nonEmpty) {
        sqls"${column} not in ( ${sqls.join(values.map(x => sqls.uuid(x)), sqls",")} )"
      } else {
        sqls""
      }
    }

    def lowerEq(column: SQLSyntax, value: String) = sqls"LOWER(${column}) = LOWER(${value})"
  }

}
