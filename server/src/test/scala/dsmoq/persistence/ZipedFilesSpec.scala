package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc._

class ZipedFilesSpec extends Specification {

  "ZipedFiles" should {

    val zf = ZipedFiles.syntax("zf")

    "find by primary keys" in new AutoRollback {
      val maybeFound = ZipedFiles.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = ZipedFiles.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = ZipedFiles.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = ZipedFiles.findAllBy(sqls.eq(zf.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = ZipedFiles.countBy(sqls.eq(zf.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = ZipedFiles.create(id = null, historyId = null, name = "MyString", description = "MyString", fileSize = 1L, createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = ZipedFiles.findAll().head
      // TODO modify something
      val modified = entity
      val updated = ZipedFiles.save(modified)
      updated should not equalTo(entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = ZipedFiles.findAll().head
      ZipedFiles.destroy(entity)
      val shouldBeNone = ZipedFiles.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
        