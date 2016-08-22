package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc._

class LicenseSpec extends Specification {
  val l = License.syntax("l")

  "License" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = License.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = License.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = License.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = License.findAllBy(sqls.eq(l.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = License.countBy(sqls.eq(l.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = License.create(id = null, name = "MyString", displayOrder = 123, createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = License.findAll().head
      val updated = License.save(entity)
      updated should not equalTo (entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = License.findAll().head
      License.destroy(entity)
      val shouldBeNone = License.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
