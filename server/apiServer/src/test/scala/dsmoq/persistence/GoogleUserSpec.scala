package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc._

class GoogleUserSpec extends Specification {
  val gu = GoogleUser.syntax("gu")

  "GoogleUsers" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = GoogleUser.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = GoogleUser.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = GoogleUser.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = GoogleUser.findAllBy(sqls.eq(gu.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = GoogleUser.countBy(sqls.eq(gu.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = GoogleUser.create(id = null, userId = null, googleId = "MyString", createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = GoogleUser.findAll().head
      val updated = GoogleUser.save(entity)
      updated should not equalTo (entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = GoogleUser.findAll().head
      GoogleUser.destroy(entity)
      val shouldBeNone = GoogleUser.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
