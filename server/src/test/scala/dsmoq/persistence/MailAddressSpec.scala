package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc._

class MailAddressSpec extends Specification {
  val ma = MailAddress.syntax("ma")

  "MailAddress" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = MailAddress.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = MailAddress.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = MailAddress.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = MailAddress.findAllBy(sqls.eq(ma.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = MailAddress.countBy(sqls.eq(ma.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = MailAddress.create(id = null, userId = null, address = "MyString", status = 123, createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = MailAddress.findAll().head
      val updated = MailAddress.save(entity)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = MailAddress.findAll().head
      MailAddress.destroy(entity)
      val shouldBeNone = MailAddress.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        