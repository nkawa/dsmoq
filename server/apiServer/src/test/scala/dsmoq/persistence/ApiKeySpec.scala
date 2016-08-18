package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc._

class ApiKeySpec extends Specification {

  "ApiKey" should {

    val ak = ApiKey.syntax("ak")

    "find by primary keys" in new AutoRollback {
      val maybeFound = ApiKey.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = ApiKey.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = ApiKey.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = ApiKey.findAllBy(sqls.eq(ak.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = ApiKey.countBy(sqls.eq(ak.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = ApiKey.create(id = null, userId = null, apiKey = "MyString", secretKey = "MyString", permission = 123, createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = ApiKey.findAll().head
      // TODO modify something
      val modified = entity
      val updated = ApiKey.save(modified)
      updated should not equalTo (entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = ApiKey.findAll().head
      ApiKey.destroy(entity)
      val shouldBeNone = ApiKey.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
