package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc._

class GroupImageSpec extends Specification {
  val gi = GroupImage.syntax("gi")

  "GroupImage" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = GroupImage.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = GroupImage.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = GroupImage.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = GroupImage.findAllBy(sqls.eq(gi.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = GroupImage.countBy(sqls.eq(gi.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = GroupImage.create(id = null, groupId = null, imageId = null, isPrimary = false, createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = GroupImage.findAll().head
      val updated = GroupImage.save(entity)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = GroupImage.findAll().head
      GroupImage.destroy(entity)
      val shouldBeNone = GroupImage.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        