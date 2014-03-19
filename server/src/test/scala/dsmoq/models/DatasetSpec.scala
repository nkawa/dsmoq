package dsmoq.models

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc.SQLInterpolation._

class DatasetSpec extends Specification {
  val d = Dataset.syntax("d")

  "Dataset" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = Dataset.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = Dataset.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = Dataset.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = Dataset.findAllBy(sqls.eq(d.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = Dataset.countBy(sqls.eq(d.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = Dataset.create(id = null, name = "MyString", description = "MyString", licenseId = null, defaultAccessLevel = 123, createdAt = DateTime.now, updatedAt = DateTime.now, createdBy = null, updatedBy = null)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = Dataset.findAll().head
      val updated = Dataset.save(entity)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = Dataset.findAll().head
      Dataset.destroy(entity)
      val shouldBeNone = Dataset.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        