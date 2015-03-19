package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc._

class StatisticsSpec extends Specification {

  "Statistics" should {

    val s = Statistics.syntax("s")

    "find by primary keys" in new AutoRollback {
      val maybeFound = Statistics.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = Statistics.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = Statistics.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = Statistics.findAllBy(sqls.eq(s.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = Statistics.countBy(sqls.eq(s.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = Statistics.create(id = null, targetMonth = DateTime.now, datasetCount = 1L, realSize = 1L, compressedSize = 1L, s3Size = 1L, localSize = 1L, createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now, statisticsType = 1)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = Statistics.findAll().head
      // TODO modify something
      val modified = entity
      val updated = Statistics.save(modified)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = Statistics.findAll().head
      Statistics.destroy(entity)
      val shouldBeNone = Statistics.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        