package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc._

class UserSpec extends Specification {
  val u = User.syntax("u")

  "User" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = User.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = User.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = User.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = User.findAllBy(sqls.eq(u.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = User.countBy(sqls.eq(u.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = User.create(id = null, name = "MyString", fullname = "MyString", organization = "MyString", title = "MyString", description = "MyString", imageId = null, createdAt = DateTime.now, updatedAt = DateTime.now, createdBy = null, updatedBy = null)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = User.findAll().head
      val updated = User.save(entity)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = User.findAll().head
      User.destroy(entity)
      val shouldBeNone = User.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        