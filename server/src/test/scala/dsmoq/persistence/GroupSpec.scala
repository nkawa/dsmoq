package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc.SQLInterpolation._

class GroupSpec extends Specification {
  val g = Group.syntax("g")

  "Group" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = Group.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = Group.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = Group.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = Group.findAllBy(sqls.eq(g.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = Group.countBy(sqls.eq(g.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = Group.create(id = null, name = "MyString", description = "MyString", groupType = 123, createdAt = DateTime.now, updatedAt = DateTime.now, createdBy = null, updatedBy = null)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = Group.findAll().head
      val updated = Group.save(entity)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = Group.findAll().head
      Group.destroy(entity)
      val shouldBeNone = Group.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        