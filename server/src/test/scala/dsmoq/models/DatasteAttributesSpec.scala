package dsmoq.models

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc.SQLInterpolation._

class DatasteAttributesSpec extends Specification {
  val da = DatasteAttributes.syntax("da")

  "DatasteAttributes" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = DatasteAttributes.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = DatasteAttributes.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = DatasteAttributes.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = DatasteAttributes.findAllBy(sqls.eq(da.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = DatasteAttributes.countBy(sqls.eq(da.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = DatasteAttributes.create(id = null, datasetId = null, attributeId = null, val = "MyString", createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = DatasteAttributes.findAll().head
      val updated = DatasteAttributes.save(entity)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = DatasteAttributes.findAll().head
      DatasteAttributes.destroy(entity)
      val shouldBeNone = DatasteAttributes.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        