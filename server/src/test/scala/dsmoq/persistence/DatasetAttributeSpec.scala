package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc.SQLInterpolation._

class DatasetAttributeSpec extends Specification {
  val da = DatasetAttribute.syntax("da")

  "DatasetAttribute" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = DatasetAttribute.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = DatasetAttribute.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = DatasetAttribute.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = DatasetAttribute.findAllBy(sqls.eq(da.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = DatasetAttribute.countBy(sqls.eq(da.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = DatasetAttribute.create(id = null, datasetId = null, attributeId = null, val = "MyString", createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = DatasetAttribute.findAll().head
      val updated = DatasetAttribute.save(entity)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = DatasetAttribute.findAll().head
      DatasetAttribute.destroy(entity)
      val shouldBeNone = DatasetAttribute.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        