package dsmoq.maintenance.services

import java.util.UUID
import org.joda.time.DateTime

import org.scalatest.BeforeAndAfter
import org.scalatest.FreeSpec
import org.scalatest.Matchers._

import scalikejdbc.config.DBsWithEnv

import dsmoq.persistence
import dsmoq.persistence.PostgresqlHelper.PgConditionSQLBuilder
import dsmoq.persistence.PostgresqlHelper.PgSQLSyntaxType
import dsmoq.maintenance.data.file.SearchCondition
import dsmoq.maintenance.data.file.UpdateParameter
import dsmoq.maintenance.services.SpecCommonLogic.UserDetail

import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SelectSQLBuilder
import scalikejdbc.SQLSyntax
import scalikejdbc.interpolation.Implicits.scalikejdbcSQLInterpolationImplicitDef
import scalikejdbc.interpolation.Implicits.scalikejdbcSQLSyntaxToStringImplicitDef
import scalikejdbc.select
import scalikejdbc.sqls
import scalikejdbc.update
import scalikejdbc.withSQL

class FileServiceSpec extends FreeSpec with BeforeAndAfter {
  DBsWithEnv("test").setup()
  SpecCommonLogic.deleteAllCreateData()

  before {
    SpecCommonLogic.insertDummyData()
  }

  after {
    SpecCommonLogic.deleteAllCreateData()
  }

  val defaultUpdatedAt = new DateTime(2000, 1, 1, 0, 0, 0)

  "search by" - {
    def updateCreatedAt(files: Seq[persistence.File]): Unit = {
      DB.localTx { implicit s =>
        val f = persistence.File.column
        for {
          (file, index) <- files.zipWithIndex
        } {
          withSQL {
            update(persistence.File)
              .set(
                f.createdAt -> new DateTime(2016, 9, 22, 0, 0, index)
              )
              .where
              .eq(f.id, sqls.uuid(file.id))
          }.update.apply()
        }
      }
    }
    def prepareFiles(): (String, String, Seq[persistence.File]) = {
      val client = SpecCommonLogic.createClient()
      val files1 = (1 to 5).map(i => new java.io.File(s"./testdata/maintenance/file/test${i}.csv"))
      val files2 = (6 to 10).map(i => new java.io.File(s"./testdata/maintenance/file/test${i}.csv"))
      val dataset1 = client.createDataset("dataset1", true, false, files1: _*)
      val dataset2 = client.createDataset("dataset2", true, false, files2: _*)
      val files = getFiles(Seq(dataset1.getId, dataset2.getId))
      val fileMap = files.groupBy(_.datasetId)
      fileMap(dataset1.getId).take(2).foreach { file =>
        client.deleteFile(file.datasetId, file.id)
      }
      fileMap(dataset2.getId).take(3).foreach { file =>
        client.deleteFile(file.datasetId, file.id)
      }
      updateCreatedAt(files)
      (dataset1.getId, dataset2.getId, getFiles(Seq(dataset1.getId, dataset2.getId)))
    }
    "fileType=None, datasetId=None, page=None" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map.empty)
      val results = FileService.search(condition)
      val expects = files.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "fileType=None, datasetId=Some(invalid ID), page=None" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("datasetId" -> "test"))
      val results = FileService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "fileType=None, datasetId=Some(not exists), page=None" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("datasetId" -> UUID.randomUUID.toString))
      val results = FileService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "fileType=None, datasetId=Some(dataset1), page=None" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("datasetId" -> datasetId1))
      val results = FileService.search(condition)
      val expects = files.filter(_.datasetId == datasetId1).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "fileType=Some(all), datasetId=None, page=None" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("fileType" -> "all"))
      val results = FileService.search(condition)
      val expects = files.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "fileType=Some(all), datasetId=Some(invalid ID), page=None" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("fileType" -> "all", "datasetId" -> "test"))
      val results = FileService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "fileType=Some(all), datasetId=Some(not exists), page=None" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("fileType" -> "all", "datasetId" -> UUID.randomUUID.toString))
      val results = FileService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "fileType=Some(all), datasetId=Some(dataset1), page=None" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("fileType" -> "all", "datasetId" -> datasetId1))
      val results = FileService.search(condition)
      val expects = files.filter(_.datasetId == datasetId1).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "fileType=Some(not_deleted), datasetId=None, page=None" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("fileType" -> "not_deleted"))
      val results = FileService.search(condition)
      val expects = files.filter(!_.deletedAt.isDefined).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "fileType=Some(not_deleted), datasetId=Some(invalid ID), page=None" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("fileType" -> "not_deleted", "datasetId" -> "test"))
      val results = FileService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "fileType=Some(not_deleted), datasetId=Some(not exists), page=None" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("fileType" -> "not_deleted", "datasetId" -> UUID.randomUUID.toString))
      val results = FileService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "fileType=Some(not_deleted), datasetId=Some(dataset1), page=None" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("fileType" -> "not_deleted", "datasetId" -> datasetId1))
      val results = FileService.search(condition)
      val expects = files.filter(f => !f.deletedAt.isDefined && f.datasetId == datasetId1).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "fileType=Some(deleted), datasetId=None, page=None" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("fileType" -> "deleted"))
      val results = FileService.search(condition)
      val expects = files.filter(_.deletedAt.isDefined).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "fileType=Some(deleted), datasetId=Some(invalid ID), page=None" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("fileType" -> "deleted", "datasetId" -> "test"))
      val results = FileService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "fileType=Some(deleted), datasetId=Some(not exists), page=None" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("fileType" -> "deleted", "datasetId" -> UUID.randomUUID.toString))
      val results = FileService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "fileType=Some(deleted), datasetId=Some(dataset1), page=None" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("fileType" -> "deleted", "datasetId" -> datasetId1))
      val results = FileService.search(condition)
      val expects = files.filter(f => f.deletedAt.isDefined && f.datasetId == datasetId1).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "fileType=None, datasetId=None, page=Some(-1)" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("page" -> "-1"))
      val results = FileService.search(condition)
      val expects = files.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "fileType=None, datasetId=None, page=Some(0)" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("page" -> "0"))
      val results = FileService.search(condition)
      val expects = files.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "fileType=None, datasetId=None, page=Some(1)" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("page" -> "1"))
      val results = FileService.search(condition)
      val expects = files.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "fileType=None, datasetId=None, page=Some(2)" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("page" -> "2"))
      val results = FileService.search(condition)
      val expects = files.map(_.id).drop(5).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(6)
      results.to should be(10)
    }
    "fileType=None, datasetId=None, page=Some(3)" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("page" -> "3"))
      val results = FileService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(10)
    }
    "fileType=None, datasetId=None, page=Some(a)" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("page" -> "a"))
      val results = FileService.search(condition)
      val expects = files.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "fileType=Some(not_deleted), datasetId=None, page=Some(2)" in {
      val (datasetId1, datasetId2, files) = prepareFiles()
      val condition = SearchCondition.fromMap(Map("fileType" -> "not_deleted", "page" -> "2"))
      val results = FileService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(5)
    }
  }

  "logical delete" - {
    def prepareFiles(): (String, Seq[persistence.File]) = {
      val client = SpecCommonLogic.createClient()
      val rawFiles = (1 to 2).map(i => new java.io.File(s"./testdata/maintenance/file/test${i}.csv"))
      val dataset = client.createDataset("dataset1", true, false, rawFiles: _*)
      val files = getFiles(Seq(dataset.getId))
      resetUpdatedAt(files)
      (dataset.getId, files)
    }
    "no select" in {
      val (datasetId, files) = prepareFiles()
      val now = DateTime.now
      val thrown = the[ServiceException] thrownBy {
        FileService.applyLogicalDelete(toParam(Seq.empty)).get
      }
      thrown.getMessage should be("ファイルが選択されていません。")
      getFiles(Seq(datasetId)).filter(u => u.updatedAt.isAfter(defaultUpdatedAt)).size should be(0)
    }
    "invalid ID x exists ID" in {
      val now = DateTime.now
      val (datasetId, files) = prepareFiles()
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        FileService.applyLogicalDelete(toParam(Seq("test", files(1).id))).get
      }
      getFiles(Seq(datasetId)).filter(u => u.updatedAt.isAfter(defaultUpdatedAt)).size should be(0)
    }
    "exists ID x invalid ID" in {
      val now = DateTime.now
      val (datasetId, files) = prepareFiles()
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        FileService.applyLogicalDelete(toParam(Seq(files(0).id, "test"))).get
      }
      getFiles(Seq(datasetId)).filter(u => u.updatedAt.isAfter(defaultUpdatedAt)).size should be(0)
    }
    "not exists ID x exists ID" in {
      val (datasetId, files) = prepareFiles()
      FileService.applyLogicalDelete(toParam(Seq(UUID.randomUUID.toString, files(1).id))).get
      getFiles(Seq(datasetId)).filter(u => u.updatedAt.isAfter(defaultUpdatedAt)).size should be(1)
      persistence.File.find(files(1).id).foreach { file =>
        file.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        file.deletedBy.isDefined should be(true)
        file.deletedAt.isDefined should be(true)
      }
    }
    "exists ID x not exists ID" in {
      val (datasetId, files) = prepareFiles()
      FileService.applyLogicalDelete(toParam(Seq(files(0).id, UUID.randomUUID.toString))).get
      persistence.File.find(files(0).id).foreach { file =>
        file.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        file.deletedBy.isDefined should be(true)
        file.deletedAt.isDefined should be(true)
      }
    }
    "not deleted" in {
      val (datasetId, files) = prepareFiles()
      FileService.applyLogicalDelete(toParam(Seq(files(0).id))).get
      persistence.File.find(files(0).id).foreach { file =>
        file.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        file.deletedBy.isDefined should be(true)
        file.deletedAt.isDefined should be(true)
      }
    }
    "deleted" in {
      val (datasetId, files) = prepareFiles()
      val client = SpecCommonLogic.createClient()
      client.deleteFile(datasetId, files(0).id)
      resetUpdatedAt(files)
      FileService.applyLogicalDelete(toParam(Seq(files(0).id))).get
      persistence.File.find(files(0).id).foreach { file =>
        file.updatedAt.isAfter(defaultUpdatedAt) should be(false)
        file.deletedBy.isDefined should be(true)
        file.deletedAt.isDefined should be(true)
      }
    }
    "deleted and not deleted" in {
      val (datasetId, files) = prepareFiles()
      val client = SpecCommonLogic.createClient()
      client.deleteFile(datasetId, files(0).id)
      resetUpdatedAt(files)
      FileService.applyLogicalDelete(toParam(Seq(files(0).id, files(1).id))).get
      persistence.File.find(files(0).id).foreach { file =>
        file.updatedAt.isAfter(defaultUpdatedAt) should be(false)
        file.deletedBy.isDefined should be(true)
        file.deletedAt.isDefined should be(true)
      }
      persistence.File.find(files(1).id).foreach { file =>
        file.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        file.deletedBy.isDefined should be(true)
        file.deletedAt.isDefined should be(true)
      }
    }
    "not deleted and not deleted" in {
      val (datasetId, files) = prepareFiles()
      FileService.applyLogicalDelete(toParam(Seq(files(0).id, files(1).id))).get
      persistence.File.find(files(0).id).foreach { file =>
        file.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        file.deletedBy.isDefined should be(true)
        file.deletedAt.isDefined should be(true)
      }
      persistence.File.find(files(1).id).foreach { file =>
        file.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        file.deletedBy.isDefined should be(true)
        file.deletedAt.isDefined should be(true)
      }
    }
  }

  "cancel logical delete" - {
    def prepareFiles(): (String, Seq[persistence.File]) = {
      val client = SpecCommonLogic.createClient()
      val rawFiles = (1 to 2).map(i => new java.io.File(s"./testdata/maintenance/file/test${i}.csv"))
      val dataset = client.createDataset("dataset1", true, false, rawFiles: _*)
      val files = getFiles(Seq(dataset.getId))
      resetUpdatedAt(files)
      (dataset.getId, files)
    }
    "no select" in {
      val (datasetId, files) = prepareFiles()
      val thrown = the[ServiceException] thrownBy {
        FileService.applyCancelLogicalDelete(toParam(Seq.empty)).get
      }
      thrown.getMessage should be("ファイルが選択されていません。")
      getFiles(Seq(datasetId)).filter(u => u.updatedAt.isAfter(defaultUpdatedAt)).size should be(0)
    }
    "invalid ID x exists ID" in {
      val (datasetId, files) = prepareFiles()
      val client = SpecCommonLogic.createClient()
      client.deleteFile(datasetId, files(1).id)
      resetUpdatedAt(files)
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        FileService.applyCancelLogicalDelete(toParam(Seq("test", files(1).id))).get
      }
      getFiles(Seq(datasetId)).filter(u => u.updatedAt.isAfter(defaultUpdatedAt)).size should be(0)
    }
    "exists ID x invalid ID" in {
      val (datasetId, files) = prepareFiles()
      val client = SpecCommonLogic.createClient()
      client.deleteFile(datasetId, files(0).id)
      resetUpdatedAt(files)
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        FileService.applyCancelLogicalDelete(toParam(Seq(files(0).id, "test"))).get
      }
      getFiles(Seq(datasetId)).filter(u => u.updatedAt.isAfter(defaultUpdatedAt)).size should be(0)
    }
    "not exists ID x exists ID" in {
      val (datasetId, files) = prepareFiles()
      val client = SpecCommonLogic.createClient()
      client.deleteFile(datasetId, files(1).id)
      resetUpdatedAt(files)
      FileService.applyCancelLogicalDelete(toParam(Seq(UUID.randomUUID.toString, files(1).id))).get
      persistence.File.find(files(1).id).foreach { file =>
        file.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        file.deletedBy.isDefined should be(false)
        file.deletedAt.isDefined should be(false)
      }
    }
    "exists ID x not exists ID" in {
      val (datasetId, files) = prepareFiles()
      val client = SpecCommonLogic.createClient()
      client.deleteFile(datasetId, files(0).id)
      resetUpdatedAt(files)
      FileService.applyCancelLogicalDelete(toParam(Seq(files(0).id, UUID.randomUUID.toString))).get
      persistence.File.find(files(0).id).foreach { file =>
        file.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        file.deletedBy.isDefined should be(false)
        file.deletedAt.isDefined should be(false)
      }
    }
    "not deleted" in {
      val (datasetId, files) = prepareFiles()
      FileService.applyCancelLogicalDelete(toParam(Seq(files(0).id))).get
      persistence.File.find(files(0).id).foreach { file =>
        file.updatedAt.isAfter(defaultUpdatedAt) should be(false)
        file.deletedBy.isDefined should be(false)
        file.deletedAt.isDefined should be(false)
      }
    }
    "deleted" in {
      val (datasetId, files) = prepareFiles()
      val client = SpecCommonLogic.createClient()
      client.deleteFile(datasetId, files(0).id)
      resetUpdatedAt(files)
      FileService.applyCancelLogicalDelete(toParam(Seq(files(0).id))).get
      persistence.File.find(files(0).id).foreach { file =>
        file.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        file.deletedBy.isDefined should be(false)
        file.deletedAt.isDefined should be(false)
      }
    }
    "deleted and deleted" in {
      val (datasetId, files) = prepareFiles()
      val client = SpecCommonLogic.createClient()
      client.deleteFile(datasetId, files(0).id)
      client.deleteFile(datasetId, files(1).id)
      resetUpdatedAt(files)
      FileService.applyCancelLogicalDelete(toParam(Seq(files(0).id, files(1).id))).get
      persistence.File.find(files(0).id).foreach { file =>
        file.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        file.deletedBy.isDefined should be(false)
        file.deletedAt.isDefined should be(false)
      }
      persistence.File.find(files(1).id).foreach { file =>
        file.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        file.deletedBy.isDefined should be(false)
        file.deletedAt.isDefined should be(false)
      }
    }
    "deleted and not deleted" in {
      val (datasetId, files) = prepareFiles()
      val client = SpecCommonLogic.createClient()
      client.deleteFile(datasetId, files(0).id)
      resetUpdatedAt(files)
      FileService.applyCancelLogicalDelete(toParam(Seq(files(0).id, files(1).id))).get
      persistence.File.find(files(0).id).foreach { file =>
        file.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        file.deletedBy.isDefined should be(false)
        file.deletedAt.isDefined should be(false)
      }
      persistence.File.find(files(1).id).foreach { file =>
        file.updatedAt.isAfter(defaultUpdatedAt) should be(false)
        file.deletedBy.isDefined should be(false)
        file.deletedAt.isDefined should be(false)
      }
    }
  }
  def resetUpdatedAt(files: Seq[persistence.File]): Unit = {
    DB.localTx { implicit s =>
      val f = persistence.File.column
      withSQL {
        update(persistence.File)
          .set(
            f.updatedAt -> defaultUpdatedAt
          )
          .where
          .in(f.id, files.map(f => sqls.uuid(f.id)))
      }.update.apply()
    }
  }
  def getFiles(datasetIds: Seq[String]): Seq[persistence.File] = {
    val f = persistence.File.f
    DB.readOnly { implicit s =>
      withSQL {
        select
          .from(persistence.File as f)
          .where
          .inUuid(f.datasetId, datasetIds)
          .orderBy(f.createdAt)
      }.map(persistence.File(f.resultName)).list.apply()
    }
  }
  def toParam(targets: Seq[String]): UpdateParameter = {
    val map = org.scalatra.util.MultiMap(Map("checked" -> targets))
    UpdateParameter.fromMap(map)
  }
}
