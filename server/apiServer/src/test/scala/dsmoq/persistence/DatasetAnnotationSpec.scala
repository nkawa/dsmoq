package dsmoq.persistence

import scalikejdbc.specs2.mutable.AutoRollback
import org.specs2.mutable._
import org.joda.time._
import scalikejdbc._

class DatasetAnnotationSpec extends Specification {
  val da = DatasetAnnotation.syntax("da")

  "DatasetAnnotation" should {
    "find by primary keys" in new AutoRollback {
      val maybeFound = DatasetAnnotation.find(null)
      maybeFound.isDefined should beTrue
    }
    "find all records" in new AutoRollback {
      val allResults = DatasetAnnotation.findAll()
      allResults.size should be_>(0)
    }
    "count all records" in new AutoRollback {
      val count = DatasetAnnotation.countAll()
      count should be_>(0L)
    }
    "find by where clauses" in new AutoRollback {
      val results = DatasetAnnotation.findAllBy(sqls.eq(da.id, null))
      results.size should be_>(0)
    }
    "count by where clauses" in new AutoRollback {
      val count = DatasetAnnotation.countBy(sqls.eq(da.id, null))
      count should be_>(0L)
    }
    "create new record" in new AutoRollback {
      val created = DatasetAnnotation.create(id = null, datasetId = null, annotationId = null, data = "MyString", createdBy = null, createdAt = DateTime.now, updatedBy = null, updatedAt = DateTime.now)
      created should not beNull
    }
    "save a record" in new AutoRollback {
      val entity = DatasetAnnotation.findAll().head
      val updated = DatasetAnnotation.save(entity)
      updated should not equalTo (entity)
    }
    "destroy a record" in new AutoRollback {
      val entity = DatasetAnnotation.findAll().head
      DatasetAnnotation.destroy(entity)
      val shouldBeNone = DatasetAnnotation.find(null)
      shouldBeNone.isDefined should beFalse
    }
  }

}
