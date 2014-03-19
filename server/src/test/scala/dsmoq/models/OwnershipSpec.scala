package dsmoq.models

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc.SQLInterpolation._

class OwnershipSpec extends Specification {
  val o = Ownership.syntax("o")

  "Ownership" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = Ownership.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = Ownership.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = Ownership.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = Ownership.findAllBy(sqls.eq(o.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = Ownership.countBy(sqls.eq(o.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = Ownership.create(id = null, datasetId = null, ownerType = 123, ownerId = null, accessLevel = 123, createdAt = DateTime.now, updatedAt = DateTime.now, createdBy = null, updatedBy = null)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = Ownership.findAll().head
      val updated = Ownership.save(entity)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = Ownership.findAll().head
      Ownership.destroy(entity)
      val shouldBeNone = Ownership.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        