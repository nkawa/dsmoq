package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc._

class TaskSpec extends Specification {

  "Task" should {

    val t = Task.syntax("t")

    "find by primary keys" in new AutoRollback {
      val maybeFound = Task.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = Task.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = Task.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = Task.findAllBy(sqls.eq(t.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = Task.countBy(sqls.eq(t.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = Task.create(id = null, taskType = 123, parameter = "MyString", status = 123, createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = Task.findAll().head
      // TODO modify something
      val modified = entity
      val updated = Task.save(modified)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = Task.findAll().head
      Task.destroy(entity)
      val shouldBeNone = Task.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        