package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc._

class TaskLogSpec extends Specification {

  "TaskLog" should {

    val tl = TaskLog.syntax("tl")

    "find by primary keys" in new AutoRollback {
      val maybeFound = TaskLog.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = TaskLog.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = TaskLog.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = TaskLog.findAllBy(sqls.eq(tl.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = TaskLog.countBy(sqls.eq(tl.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = TaskLog.create(id = null, taskId = null, logType = 123, message = "MyString", createdBy = null, createdAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = TaskLog.findAll().head
      // TODO modify something
      val modified = entity
      val updated = TaskLog.save(modified)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = TaskLog.findAll().head
      TaskLog.destroy(entity)
      val shouldBeNone = TaskLog.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        