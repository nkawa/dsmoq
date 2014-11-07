package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc._

class DatasetImageSpec extends Specification {
  val di = DatasetImage.syntax("di")

  "DatasetImage" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = DatasetImage.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = DatasetImage.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = DatasetImage.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = DatasetImage.findAllBy(sqls.eq(di.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = DatasetImage.countBy(sqls.eq(di.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = DatasetImage.create(id = null, datasetId = null, imageId = null, isPrimary = false, createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = DatasetImage.findAll().head
      val updated = DatasetImage.save(entity)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = DatasetImage.findAll().head
      DatasetImage.destroy(entity)
      val shouldBeNone = DatasetImage.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        