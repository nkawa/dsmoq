package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc._

class PasswordSpec extends Specification {
  val pwd = Password.syntax("p")

  "Password" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = Password.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = Password.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = Password.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = Password.findAllBy(sqls.eq(pwd.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = Password.countBy(sqls.eq(pwd.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = Password.create(id = null, userId = null, hash = "MyString", createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = Password.findAll().head
      val updated = Password.save(entity)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = Password.findAll().head
      Password.destroy(entity)
      val shouldBeNone = Password.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        