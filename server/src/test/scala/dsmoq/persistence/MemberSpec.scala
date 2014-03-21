package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc.SQLInterpolation._

class MemberSpec extends Specification {
  val m = Member.syntax("m")

  "Member" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = Member.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = Member.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = Member.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = Member.findAllBy(sqls.eq(m.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = Member.countBy(sqls.eq(m.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = Member.create(id = null, groupId = null, userId = null, role = 123, status = 123, createdAt = DateTime.now, updatedAt = DateTime.now, createdBy = null, updatedBy = null)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = Member.findAll().head
      val updated = Member.save(entity)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = Member.findAll().head
      Member.destroy(entity)
      val shouldBeNone = Member.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        