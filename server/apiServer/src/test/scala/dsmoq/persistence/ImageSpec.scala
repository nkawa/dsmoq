package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc._

class ImageSpec extends Specification {
  val i = Image.syntax("i")

  "Image" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = Image.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = Image.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = Image.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = Image.findAllBy(sqls.eq(i.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = Image.countBy(sqls.eq(i.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = Image.create(id = null, name = "MyString", width = 123, height = 123, filePath = "MyString", presetType = 123, createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = Image.findAll().head
      val updated = Image.save(entity)
      updated should not equalTo (entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = Image.findAll().head
      Image.destroy(entity)
      val shouldBeNone = Image.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
