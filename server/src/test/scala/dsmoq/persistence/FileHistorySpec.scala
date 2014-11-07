package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc._

class FileHistorySpec extends Specification {
  val fh = FileHistory.syntax("fh")

  "FileHistory" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = FileHistory.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = FileHistory.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = FileHistory.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = FileHistory.findAllBy(sqls.eq(fh.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = FileHistory.countBy(sqls.eq(fh.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = FileHistory.create(id = null, fileId = null, fileType = 123, fileMime = "MyString", filePath = "MyString", fileSize = 1L, createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = FileHistory.findAll().head
      val updated = FileHistory.save(entity)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = FileHistory.findAll().head
      FileHistory.destroy(entity)
      val shouldBeNone = FileHistory.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        