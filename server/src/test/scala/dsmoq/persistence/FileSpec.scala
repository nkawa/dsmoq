package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc.SQLInterpolation._

class FileSpec extends Specification {
  val f = File.syntax("f")

  "File" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = File.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = File.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = File.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = File.findAllBy(sqls.eq(f.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = File.countBy(sqls.eq(f.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = File.create(id = null, datasetId = null, name = "MyString", description = "MyString", createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = File.findAll().head
      val updated = File.save(entity)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = File.findAll().head
      File.destroy(entity)
      val shouldBeNone = File.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        