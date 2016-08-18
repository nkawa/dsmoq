package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc._

class AnnotationSpec extends Specification {
  val a = Annotation.syntax("a")

  "Annotation" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = Annotation.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = Annotation.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = Annotation.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = Annotation.findAllBy(sqls.eq(a.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = Annotation.countBy(sqls.eq(a.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = Annotation.create(id = null, name = "MyString", createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = Annotation.findAll().head
      val updated = Annotation.save(entity)
      updated should not equalTo (entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = Annotation.findAll().head
      Annotation.destroy(entity)
      val shouldBeNone = Annotation.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
