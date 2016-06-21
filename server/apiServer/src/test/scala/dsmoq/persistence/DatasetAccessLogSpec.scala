package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc._

class DatasetAccessLogSpec extends Specification {

  "DatasetAccessLog" should {

    val dal = DatasetAccessLog.syntax("dal")

    "find by primary keys" in new AutoRollback {
      val maybeFound = DatasetAccessLog.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = DatasetAccessLog.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = DatasetAccessLog.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = DatasetAccessLog.findAllBy(sqls.eq(dal.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = DatasetAccessLog.countBy(sqls.eq(dal.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = DatasetAccessLog.create(id = null, datasetId = null, createdBy = null, createdAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = DatasetAccessLog.findAll().head
      // TODO modify something
      val modified = entity
      val updated = DatasetAccessLog.save(modified)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = DatasetAccessLog.findAll().head
      DatasetAccessLog.destroy(entity)
      val shouldBeNone = DatasetAccessLog.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        