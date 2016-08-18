package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc._

class TagSpec extends Specification {

  "Tag" should {

    val t = Tag.syntax("t")

    "find by primary keys" in new AutoRollback {
      val maybeFound = Tag.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = Tag.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = Tag.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = Tag.findAllBy(sqls.eq(t.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = Tag.countBy(sqls.eq(t.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = Tag.create(id = null, tag = "MyString", color = "MyString", createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = Tag.findAll().head
      // TODO modify something
      val modified = entity
      val updated = Tag.save(modified)
      updated should not equalTo (entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = Tag.findAll().head
      Tag.destroy(entity)
      val shouldBeNone = Tag.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
