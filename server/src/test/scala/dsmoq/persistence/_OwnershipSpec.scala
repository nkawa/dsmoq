package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc.SQLInterpolation._

class _OwnershipSpec extends Specification {
  val o = _Ownership.syntax("o")

  "_Ownership" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = _Ownership.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = _Ownership.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = _Ownership.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = _Ownership.findAllBy(sqls.eq(o.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = _Ownership.countBy(sqls.eq(o.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = _Ownership.create(id = null, datasetId = null, groupId = null, accessLevel = 123, createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = _Ownership.findAll().head
      val updated = _Ownership.save(entity)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = _Ownership.findAll().head
      _Ownership.destroy(entity)
      val shouldBeNone = _Ownership.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        