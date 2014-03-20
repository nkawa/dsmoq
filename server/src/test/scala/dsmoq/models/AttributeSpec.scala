package dsmoq.models

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc.SQLInterpolation._

class AttributeSpec extends Specification {
  val a = Attribute.syntax("a")

  "Attribute" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = Attribute.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = Attribute.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = Attribute.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = Attribute.findAllBy(sqls.eq(a.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = Attribute.countBy(sqls.eq(a.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = Attribute.create(id = null, name = "MyString", createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = Attribute.findAll().head
      val updated = Attribute.save(entity)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = Attribute.findAll().head
      Attribute.destroy(entity)
      val shouldBeNone = Attribute.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        