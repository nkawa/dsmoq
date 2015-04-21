package dsmoq.persistence

import scalikejdbc._

object PostgresqlHelper {
  implicit class PgSelectSQLBuilder[A](val self: SelectSQLBuilder[A]) extends AnyVal {
    def hint(hint: String): SelectSQLBuilder[A] = {
      new SelectSQLBuilder[A](sqls.hint(hint).append(self.toSQLSyntax))
    }
  }

  implicit class PgConditionSQLBuilder[A](val self: ConditionSQLBuilder[A]) extends AnyVal {
    def inUuid(column: SQLSyntax, values: Seq[String]): ConditionSQLBuilder[A] = self.append(sqls.inUuid(column, values))
    def notInUuid(column: SQLSyntax, values: Seq[String]): ConditionSQLBuilder[A] = self.append(sqls.notInUuid(column, values))
    def eqUuid(column: SQLSyntax, value: String): ConditionSQLBuilder[A] = self.append(sqls.eqUuid(column, value))
    def lowerEq(column: SQLSyntax, value: String): ConditionSQLBuilder[A] = self.append(sqls.lowerEq(column, value))
    def likeQuery(column: SQLSyntax, value: String):ConditionSQLBuilder[A] = self.append(sqls.likeQuery(column, value))
    def upperLikeQuery(column: SQLSyntax, value: String): ConditionSQLBuilder[A] = self.append(sqls.upperLikeQuery(column, value))
  }

  implicit class PgSQLSyntax(val self: SQLSyntax) extends  AnyVal {
    def inUuid(column: SQLSyntax, values: Seq[String]):SQLSyntax = self.append(sqls.inUuid(column, values))
    def notInUuid(column: SQLSyntax, values: Seq[String]): SQLSyntax = self.append(sqls.notInUuid(column, values))
    def eqUuid(column: SQLSyntax, value: String): SQLSyntax = self.append(sqls.eqUuid(column, value))
    def lowerEq(column: SQLSyntax, value: String):SQLSyntax = self.append(sqls.lowerEq(column, value))
  }

  implicit class PgSQLSyntaxType(val self: sqls.type) extends AnyVal {
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

    def upperLikeQuery(column:SQLSyntax, value: String) = sqls"UPPER(${column}) like LIKEQUERY(UPPER(${value}))"

    def likeQuery(column: SQLSyntax, value: String): SQLSyntax = sqls"${column} like LIKEQUERY(${value})"

    protected[PostgresqlHelper] def hint(hint: String): SQLSyntax = sqls.createUnsafely("/*+ " + hint + " */")
  }

}
