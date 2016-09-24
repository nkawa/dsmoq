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
import dsmoq.maintenance.data
import dsmoq.maintenance.data.dataset.AccessLevel
import dsmoq.maintenance.data.dataset.SearchCondition
import dsmoq.maintenance.data.dataset.SearchAclsParameter
import dsmoq.maintenance.data.dataset.SearchAclUserParameter
import dsmoq.maintenance.data.dataset.SearchAclGroupParameter
import dsmoq.maintenance.data.dataset.AddAclUserParameter
import dsmoq.maintenance.data.dataset.AddAclGroupParameter
import dsmoq.maintenance.data.dataset.UpdateAclUserParameter
import dsmoq.maintenance.data.dataset.UpdateAclGroupParameter
import dsmoq.maintenance.data.dataset.UpdateParameter

import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient
import jp.ac.nagoya_u.dsmoq.sdk.request.SetAccessLevelParam
import jp.ac.nagoya_u.dsmoq.sdk.request.CreateGroupParam

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

class DatasetServiceSpec extends FreeSpec with BeforeAndAfter {
  DBsWithEnv("test").setup()
  SpecCommonLogic.deleteAllCreateData()

  before {
    SpecCommonLogic.insertDummyData()
  }

  after {
    SpecCommonLogic.deleteAllCreateData()
  }

  val defaultUpdatedAt = new DateTime(2000, 1, 1, 0, 0, 0)
  val userId1 = "023bfa40-e897-4dad-96db-9fd3cf001e79"
  val userId2 = "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04"

  "search by" - {
    def updateCreatedAt(datasets: Seq[String]): Unit = {
      DB.localTx { implicit s =>
        val d = persistence.Dataset.column
        for {
          (id, index) <- datasets.zipWithIndex
        } {
          withSQL {
            update(persistence.Dataset)
              .set(
                d.createdAt -> new DateTime(2016, 9, 22, 0, 0, index)
              )
              .where
              .eq(d.id, sqls.uuid(id))
          }.update.apply()
        }
      }
    }
    def prepareDatasets(): Seq[persistence.Dataset] = {
      val client1 = SpecCommonLogic.createClient()
      val client2 = SpecCommonLogic.createClient2()
      val datasets = Seq(
        createDataset(client1, "dummy1", false),
        createDataset(client1, "dummy2", false),
        createDataset(client1, "dummy3", true),
        createDataset(client1, "dummy4", true),
        createDataset(client1, "test1", false),
        createDataset(client1, "test2", true),
        createDataset(client2, "dummy5", false),
        createDataset(client2, "dummy6", true),
        createDataset(client2, "test3", false),
        createDataset(client2, "test4", true)
      )
      updateCreatedAt(datasets)
      getDatasets(datasets)
    }
    "datasetType=None, ownerId=None, datasetName=None, page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map.empty)
      val results = DatasetService.search(condition)
      val expects = datasets.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "datasetType=None, ownerId=Some(not exists), datasetName=Some(not exists), page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("ownerId" -> "hoge", "datasetName" -> "fuga"))
      val results = DatasetService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "datasetType=None, ownerId=Some(exists), datasetName=None, page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("ownerId" -> "dummy1"))
      val results = DatasetService.search(condition)
      val expects = datasets.filter(_.createdBy == userId1).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "datasetType=None, ownerId=None, datasetName=Some(exists), page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("datasetName" -> "test"))
      val results = DatasetService.search(condition)
      val expects = datasets.filter(_.name.contains("test")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "datasetType=None, ownerId=Some(exists), datasetName=Some(exists), page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("ownerId" -> "dummy1", "datasetName" -> "test"))
      val results = DatasetService.search(condition)
      val expects = datasets.filter(_.createdBy == userId1).filter(_.name.contains("test")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "datasetType=Some(all), ownerId=None, datasetName=None, page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("datasetType" -> "all"))
      val results = DatasetService.search(condition)
      val expects = datasets.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "datasetType=Some(all), ownerId=Some(not exists), datasetName=Some(not exists), page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("datasetType" -> "all", "ownerId" -> "hoge", "datasetName" -> "fuga"))
      val results = DatasetService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "datasetType=Some(all), ownerId=Some(exists), datasetName=None, page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("datasetType" -> "all", "ownerId" -> "dummy1"))
      val results = DatasetService.search(condition)
      val expects = datasets.filter(_.createdBy == userId1).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "datasetType=Some(all), ownerId=None, datasetName=Some(exists), page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("datasetType" -> "all", "datasetName" -> "test"))
      val results = DatasetService.search(condition)
      val expects = datasets.filter(_.name.contains("test")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "datasetType=Some(all), ownerId=Some(exists), datasetName=Some(exists), page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("datasetType" -> "all", "ownerId" -> "dummy1", "datasetName" -> "test"))
      val results = DatasetService.search(condition)
      val expects = datasets.filter(_.createdBy == userId1).filter(_.name.contains("test")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "datasetType=Some(not_deleted), ownerId=None, datasetName=None, page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("datasetType" -> "not_deleted"))
      val results = DatasetService.search(condition)
      val expects = datasets.filter(!_.deletedAt.isDefined).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "datasetType=Some(not_deleted), ownerId=Some(not exists), datasetName=Some(not exists), page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("datasetType" -> "not_deleted", "ownerId" -> "hoge", "datasetName" -> "fuga"))
      val results = DatasetService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "datasetType=Some(not_deleted), ownerId=Some(exists), datasetName=None, page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("datasetType" -> "not_deleted", "ownerId" -> "dummy1"))
      val results = DatasetService.search(condition)
      val expects = datasets.filter(!_.deletedAt.isDefined).filter(_.createdBy == userId1).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "datasetType=Some(not_deleted), ownerId=None, datasetName=Some(exists), page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("datasetType" -> "not_deleted", "datasetName" -> "test"))
      val results = DatasetService.search(condition)
      val expects = datasets.filter(!_.deletedAt.isDefined).filter(_.name.contains("test")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "datasetType=Some(not_deleted), ownerId=Some(exists), datasetName=Some(exists), page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("datasetType" -> "not_deleted", "ownerId" -> "dummy1", "datasetName" -> "test"))
      val results = DatasetService.search(condition)
      val expects = datasets.filter(!_.deletedAt.isDefined).filter(_.createdBy == userId1).filter(_.name.contains("test")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "datasetType=Some(deleted), ownerId=None, datasetName=None, page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("datasetType" -> "deleted"))
      val results = DatasetService.search(condition)
      val expects = datasets.filter(_.deletedAt.isDefined).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "datasetType=Some(deleted), ownerId=Some(not exists), datasetName=Some(not exists), page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("datasetType" -> "deleted", "ownerId" -> "hoge", "datasetName" -> "fuga"))
      val results = DatasetService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "datasetType=Some(deleted), ownerId=Some(exists), datasetName=None, page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("datasetType" -> "deleted", "ownerId" -> "dummy1"))
      val results = DatasetService.search(condition)
      val expects = datasets.filter(_.deletedAt.isDefined).filter(_.createdBy == userId1).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "datasetType=Some(deleted), ownerId=None, datasetName=Some(exists), page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("datasetType" -> "deleted", "datasetName" -> "test"))
      val results = DatasetService.search(condition)
      val expects = datasets.filter(_.deletedAt.isDefined).filter(_.name.contains("test")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "datasetType=Some(deleted), ownerId=Some(exists), datasetName=Some(exists), page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("datasetType" -> "deleted", "ownerId" -> "dummy1", "datasetName" -> "test"))
      val results = DatasetService.search(condition)
      val expects = datasets.filter(_.deletedAt.isDefined).filter(_.createdBy == userId1).filter(_.name.contains("test")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "datasetType=Some(aaa), ownerId=None, datasetName=None, page=None" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("datasetType" -> "aaa"))
      val results = DatasetService.search(condition)
      val expects = datasets.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "datasetType=None, ownerId=None, datasetName=None, page=Some(-1)" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("page" -> "-1"))
      val results = DatasetService.search(condition)
      val expects = datasets.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "datasetType=None, ownerId=None, datasetName=None, page=Some(0)" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("page" -> "0"))
      val results = DatasetService.search(condition)
      val expects = datasets.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "datasetType=None, ownerId=None, datasetName=None, page=Some(1)" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("page" -> "1"))
      val results = DatasetService.search(condition)
      val expects = datasets.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "datasetType=None, ownerId=None, datasetName=None, page=Some(2)" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("page" -> "2"))
      val results = DatasetService.search(condition)
      val expects = datasets.map(_.id).drop(5).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(6)
      results.to should be(10)
    }
    "datasetType=None, ownerId=None, datasetName=None, page=Some(3)" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("page" -> "3"))
      val results = DatasetService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(10)
    }
    "datasetType=None, ownerId=None, datasetName=None, page=Some(a)" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("page" -> "a"))
      val results = DatasetService.search(condition)
      val expects = datasets.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "datasetType=Some(not_deleted), ownerId=None, datasetName=None, page=Some(2)" in {
      val datasets = prepareDatasets()
      val condition = SearchCondition.fromMap(Map("datasetType" -> "not_deleted", "page" -> "2"))
      val results = DatasetService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(5)
    }
  }

  "logical delete" - {
    def prepareDatasets(): Seq[String] = {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset1 = client.createDataset("dataset1", true, false, file)
      val dataset2 = client.createDataset("dataset2", true, false, file)
      resetUpdatedAt(Seq(dataset1.getId, dataset2.getId))
      Seq(dataset1.getId, dataset2.getId)
    }
    "no select" in {
      val datasetIds = prepareDatasets()
      val thrown = the[ServiceException] thrownBy {
        DatasetService.applyLogicalDelete(toParam(Seq.empty)).get
      }
      thrown.getMessage should be("データセットが選択されていません。")
      getDatasets(datasetIds).filter(d => d.updatedAt.isAfter(defaultUpdatedAt)).size should be(0)
    }
    "invalid ID x exists ID" in {
      val datasetIds = prepareDatasets()
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        DatasetService.applyLogicalDelete(toParam(Seq("test", datasetIds(1)))).get
      }
      getDatasets(datasetIds).filter(d => d.updatedAt.isAfter(defaultUpdatedAt)).size should be(0)
    }
    "exists ID x invalid ID" in {
      val datasetIds = prepareDatasets()
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        DatasetService.applyLogicalDelete(toParam(Seq(datasetIds(0), "test"))).get
      }
      getDatasets(datasetIds).filter(d => d.updatedAt.isAfter(defaultUpdatedAt)).size should be(0)
    }
    "not exists ID x exists ID" in {
      val datasetIds = prepareDatasets()
      DatasetService.applyLogicalDelete(toParam(Seq(UUID.randomUUID.toString, datasetIds(1)))).get
      getDatasets(datasetIds).filter(d => d.updatedAt.isAfter(defaultUpdatedAt)).size should be(1)
      persistence.Dataset.find(datasetIds(1)).foreach { dataset =>
        dataset.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        dataset.deletedBy.isDefined should be(true)
        dataset.deletedAt.isDefined should be(true)
      }
    }
    "exists ID x not exists ID" in {
      val datasetIds = prepareDatasets()
      DatasetService.applyLogicalDelete(toParam(Seq(datasetIds(0), UUID.randomUUID.toString))).get
      persistence.Dataset.find(datasetIds(0)).foreach { dataset =>
        dataset.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        dataset.deletedBy.isDefined should be(true)
        dataset.deletedAt.isDefined should be(true)
      }
    }
    "not deleted" in {
      val datasetIds = prepareDatasets()
      DatasetService.applyLogicalDelete(toParam(Seq(datasetIds(0)))).get
      persistence.Dataset.find(datasetIds(0)).foreach { dataset =>
        dataset.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        dataset.deletedBy.isDefined should be(true)
        dataset.deletedAt.isDefined should be(true)
      }
    }
    "deleted" in {
      val datasetIds = prepareDatasets()
      val client = SpecCommonLogic.createClient()
      client.deleteDataset(datasetIds(0))
      resetUpdatedAt(datasetIds)
      DatasetService.applyLogicalDelete(toParam(Seq(datasetIds(0)))).get
      persistence.Dataset.find(datasetIds(0)).foreach { dataset =>
        dataset.updatedAt.isAfter(defaultUpdatedAt) should be(false)
        dataset.deletedBy.isDefined should be(true)
        dataset.deletedAt.isDefined should be(true)
      }
    }
    "deleted and not deleted" in {
      val datasetIds = prepareDatasets()
      val client = SpecCommonLogic.createClient()
      client.deleteDataset(datasetIds(0))
      resetUpdatedAt(datasetIds)
      DatasetService.applyLogicalDelete(toParam(datasetIds)).get
      persistence.Dataset.find(datasetIds(0)).foreach { dataset =>
        dataset.updatedAt.isAfter(defaultUpdatedAt) should be(false)
        dataset.deletedBy.isDefined should be(true)
        dataset.deletedAt.isDefined should be(true)
      }
      persistence.Dataset.find(datasetIds(1)).foreach { dataset =>
        dataset.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        dataset.deletedBy.isDefined should be(true)
        dataset.deletedAt.isDefined should be(true)
      }
    }
    "not deleted and not deleted" in {
      val datasetIds = prepareDatasets()
      DatasetService.applyLogicalDelete(toParam(datasetIds)).get
      persistence.Dataset.find(datasetIds(0)).foreach { dataset =>
        dataset.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        dataset.deletedBy.isDefined should be(true)
        dataset.deletedAt.isDefined should be(true)
      }
      persistence.Dataset.find(datasetIds(1)).foreach { dataset =>
        dataset.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        dataset.deletedBy.isDefined should be(true)
        dataset.deletedAt.isDefined should be(true)
      }
    }
  }

  "cancel logical delete" - {
    def prepareDatasets(): Seq[String] = {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset1 = client.createDataset("dataset1", true, false, file)
      val dataset2 = client.createDataset("dataset2", true, false, file)
      resetUpdatedAt(Seq(dataset1.getId, dataset2.getId))
      Seq(dataset1.getId, dataset2.getId)
    }
    "no select" in {
      val datasetIds = prepareDatasets()
      val thrown = the[ServiceException] thrownBy {
        DatasetService.applyCancelLogicalDelete(toParam(Seq.empty)).get
      }
      thrown.getMessage should be("データセットが選択されていません。")
      getDatasets(datasetIds).filter(d => d.updatedAt.isAfter(defaultUpdatedAt)).size should be(0)
    }
    "invalid ID x exists ID" in {
      val datasetIds = prepareDatasets()
      val client = SpecCommonLogic.createClient()
      client.deleteDataset(datasetIds(1))
      resetUpdatedAt(datasetIds)
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        DatasetService.applyCancelLogicalDelete(toParam(Seq("test", datasetIds(1)))).get
      }
      getDatasets(datasetIds).filter(d => d.updatedAt.isAfter(defaultUpdatedAt)).size should be(0)
    }
    "exists ID x invalid ID" in {
      val datasetIds = prepareDatasets()
      val client = SpecCommonLogic.createClient()
      client.deleteDataset(datasetIds(0))
      resetUpdatedAt(datasetIds)
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        DatasetService.applyCancelLogicalDelete(toParam(Seq(datasetIds(0), "test"))).get
      }
      getDatasets(datasetIds).filter(d => d.updatedAt.isAfter(defaultUpdatedAt)).size should be(0)
    }
    "not exists ID x exists ID" in {
      val datasetIds = prepareDatasets()
      val client = SpecCommonLogic.createClient()
      client.deleteDataset(datasetIds(1))
      resetUpdatedAt(datasetIds)
      DatasetService.applyCancelLogicalDelete(toParam(Seq(UUID.randomUUID.toString, datasetIds(1)))).get
      getDatasets(datasetIds).filter(d => d.updatedAt.isAfter(defaultUpdatedAt)).size should be(1)
      persistence.Dataset.find(datasetIds(1)).foreach { dataset =>
        dataset.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        dataset.deletedBy.isDefined should be(false)
        dataset.deletedAt.isDefined should be(false)
      }
    }
    "exists ID x not exists ID" in {
      val datasetIds = prepareDatasets()
      val client = SpecCommonLogic.createClient()
      client.deleteDataset(datasetIds(0))
      resetUpdatedAt(datasetIds)
      DatasetService.applyCancelLogicalDelete(toParam(Seq(datasetIds(0), UUID.randomUUID.toString))).get
      persistence.Dataset.find(datasetIds(0)).foreach { dataset =>
        dataset.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        dataset.deletedBy.isDefined should be(false)
        dataset.deletedAt.isDefined should be(false)
      }
    }
    "not deleted" in {
      val datasetIds = prepareDatasets()
      DatasetService.applyCancelLogicalDelete(toParam(Seq(datasetIds(0)))).get
      persistence.Dataset.find(datasetIds(0)).foreach { dataset =>
        dataset.updatedAt.isAfter(defaultUpdatedAt) should be(false)
        dataset.deletedBy.isDefined should be(false)
        dataset.deletedAt.isDefined should be(false)
      }
    }
    "deleted" in {
      val datasetIds = prepareDatasets()
      val client = SpecCommonLogic.createClient()
      client.deleteDataset(datasetIds(0))
      resetUpdatedAt(datasetIds)
      DatasetService.applyCancelLogicalDelete(toParam(Seq(datasetIds(0)))).get
      persistence.Dataset.find(datasetIds(0)).foreach { dataset =>
        dataset.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        dataset.deletedBy.isDefined should be(false)
        dataset.deletedAt.isDefined should be(false)
      }
    }
    "deleted and deleted" in {
      val datasetIds = prepareDatasets()
      val client = SpecCommonLogic.createClient()
      client.deleteDataset(datasetIds(0))
      client.deleteDataset(datasetIds(1))
      resetUpdatedAt(datasetIds)
      DatasetService.applyCancelLogicalDelete(toParam(datasetIds)).get
      persistence.Dataset.find(datasetIds(0)).foreach { dataset =>
        dataset.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        dataset.deletedBy.isDefined should be(false)
        dataset.deletedAt.isDefined should be(false)
      }
      persistence.Dataset.find(datasetIds(1)).foreach { dataset =>
        dataset.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        dataset.deletedBy.isDefined should be(false)
        dataset.deletedAt.isDefined should be(false)
      }
    }
    "deleted and not deleted" in {
      val datasetIds = prepareDatasets()
      val client = SpecCommonLogic.createClient()
      client.deleteDataset(datasetIds(0))
      resetUpdatedAt(datasetIds)
      DatasetService.applyCancelLogicalDelete(toParam(datasetIds)).get
      persistence.Dataset.find(datasetIds(0)).foreach { dataset =>
        dataset.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        dataset.deletedBy.isDefined should be(false)
        dataset.deletedAt.isDefined should be(false)
      }
      persistence.Dataset.find(datasetIds(1)).foreach { dataset =>
        dataset.updatedAt.isAfter(defaultUpdatedAt) should be(false)
        dataset.deletedBy.isDefined should be(false)
        dataset.deletedAt.isDefined should be(false)
      }
    }
  }
  "acl list" - {
    "no datasetId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = SearchAclsParameter.fromMap(Map.empty)
        DatasetService.getAclListData(param).get
      }
      thrown.getMessage should be("データセットIDの指定がありません。")
    }
    "invalid datasetId" in {
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = SearchAclsParameter.fromMap(Map("datasetId" -> "test"))
        DatasetService.getAclListData(param).get
      }
    }
    "not exists datasetId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = SearchAclsParameter.fromMap(Map("datasetId" -> UUID.randomUUID.toString))
        DatasetService.getAclListData(param).get
      }
      thrown.getMessage should be("存在しないデータセットが指定されました。")
    }
    "list check" in {
      import scala.collection.JavaConverters._
      val client = SpecCommonLogic.createClient()
      val ts = DateTime.now
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val users = (1 to 5).map { i => SpecCommonLogic.insertUser(SpecCommonLogic.UserDetail(s"user${i}", ts)) }
      val groups = (1 to 5).map { i => client.createGroup(CreateGroupParam(s"group${i}", "")) }
      val acls = Seq(
        new SetAccessLevelParam(users(0).id, persistence.OwnerType.User, persistence.UserAccessLevel.Deny),
        new SetAccessLevelParam(users(1).id, persistence.OwnerType.User, persistence.UserAccessLevel.LimitedRead),
        new SetAccessLevelParam(users(2).id, persistence.OwnerType.User, persistence.UserAccessLevel.FullPublic),
        new SetAccessLevelParam(users(3).id, persistence.OwnerType.User, persistence.UserAccessLevel.Owner),
        new SetAccessLevelParam(users(4).id, persistence.OwnerType.User, persistence.UserAccessLevel.Owner),
        new SetAccessLevelParam(groups(0).getId, persistence.OwnerType.Group, persistence.GroupAccessLevel.Deny),
        new SetAccessLevelParam(groups(1).getId, persistence.OwnerType.Group, persistence.GroupAccessLevel.LimitedPublic),
        new SetAccessLevelParam(groups(2).getId, persistence.OwnerType.Group, persistence.GroupAccessLevel.FullPublic),
        new SetAccessLevelParam(groups(3).getId, persistence.OwnerType.Group, persistence.GroupAccessLevel.Provider),
        new SetAccessLevelParam(groups(4).getId, persistence.OwnerType.Group, persistence.GroupAccessLevel.Provider)
      )
      client.changeAccessLevel(dataset.getId, acls.asJava)
      disableUser(users(4).id)
      client.deleteGroup(groups(4).getId)
      val param = SearchAclsParameter.fromMap(Map("datasetId" -> dataset.getId))
      val result = DatasetService.getAclListData(param).get
      val ids = result.ownerships.map(_.id)
      ids.contains(users(0).id) should be(false)
      ids.contains(users(1).id) should be(true)
      ids.contains(users(2).id) should be(true)
      ids.contains(users(3).id) should be(true)
      ids.contains(users(4).id) should be(false)
      ids.contains(groups(0).getId) should be(false)
      ids.contains(groups(1).getId) should be(true)
      ids.contains(groups(2).getId) should be(true)
      ids.contains(groups(3).getId) should be(true)
      ids.contains(groups(4).getId) should be(false)
    }
    "empty" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      disableUser("023bfa40-e897-4dad-96db-9fd3cf001e79")
      val param = SearchAclsParameter.fromMap(Map("datasetId" -> dataset.getId))
      val result = DatasetService.getAclListData(param).get
      result.ownerships should be(Seq.empty)
    }
    def updateOwnershipCreateAt(datasetId: String, userIds: Seq[String]): Unit = {
      DB.localTx { implicit s =>
        val m = persistence.Member.m
        val o = persistence.Ownership.column
        for (
          (userId, index) <- userIds.zipWithIndex
        ) {
          val groupId = withSQL {
            select(m.result.groupId)
              .from(persistence.Member as m)
              .where
              .eq(m.userId, sqls.uuid(userId))
          }.map(_.string(m.resultName.groupId)).single.apply().get
          withSQL {
            update(persistence.Ownership)
              .set(
                o.createdAt -> new DateTime(2016, 9, 24, 0, 0, index)
              )
              .where
              .eq(o.groupId, sqls.uuid(groupId))
              .and
              .eq(o.datasetId, sqls.uuid(datasetId))
          }.update.apply()
        }
      }
    }
    "sort order" in {
      import scala.collection.JavaConverters._
      val client = SpecCommonLogic.createClient()
      val ts = DateTime.now
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val users = (1 to 5).map { i => SpecCommonLogic.insertUser(SpecCommonLogic.UserDetail(s"user${i}", ts)) }
      val acls = Seq(
        new SetAccessLevelParam(users(0).id, persistence.OwnerType.User, persistence.UserAccessLevel.LimitedRead),
        new SetAccessLevelParam(users(1).id, persistence.OwnerType.User, persistence.UserAccessLevel.LimitedRead),
        new SetAccessLevelParam(users(2).id, persistence.OwnerType.User, persistence.UserAccessLevel.LimitedRead),
        new SetAccessLevelParam(users(3).id, persistence.OwnerType.User, persistence.UserAccessLevel.LimitedRead),
        new SetAccessLevelParam(users(4).id, persistence.OwnerType.User, persistence.UserAccessLevel.LimitedRead)
      )
      client.changeAccessLevel(dataset.getId, acls.asJava)
      val ids = Seq("023bfa40-e897-4dad-96db-9fd3cf001e79") ++ users.map(_.id)
      updateOwnershipCreateAt(dataset.getId, ids)
      val param = SearchAclsParameter.fromMap(Map("datasetId" -> dataset.getId))
      val result = DatasetService.getAclListData(param).get
      result.ownerships.map(_.id) should be(ids)
    }
  }

  "acl add show" - {
    "no datasetId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = SearchAclsParameter.fromMap(Map.empty)
        DatasetService.getAclAddData(param).get
      }
      thrown.getMessage should be("データセットIDの指定がありません。")
    }
    "invalid datasetId" in {
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = SearchAclsParameter.fromMap(Map("datasetId" -> "test"))
        DatasetService.getAclAddData(param).get
      }
    }
    "not exists datasetId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = SearchAclsParameter.fromMap(Map("datasetId" -> UUID.randomUUID.toString))
        DatasetService.getAclAddData(param).get
      }
      thrown.getMessage should be("存在しないデータセットが指定されました。")
    }
    "exists datasetId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val param = SearchAclsParameter.fromMap(Map("datasetId" -> dataset.getId))
      val result = DatasetService.getAclAddData(param).get
      result.datasetId should be(dataset.getId)
      result.datasetName should be(dataset.getMeta.getName)
    }
  }

  "acl add user" - {
    "no datasetId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = AddAclUserParameter.fromMap(Map.empty)
        DatasetService.applyAddAclUser(param).get
      }
      thrown.getMessage should be("データセットIDの指定がありません。")
    }
    "invalid datasetId" in {
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = AddAclUserParameter.fromMap(Map("datasetId" -> "test", "userName" -> "dummy2", "accessLevel" -> "limitedRead"))
        DatasetService.applyAddAclUser(param).get
      }
    }
    "not exists datasetId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = AddAclUserParameter.fromMap(Map("datasetId" -> UUID.randomUUID.toString, "userName" -> "dummy2", "accessLevel" -> "limitedRead"))
        DatasetService.applyAddAclUser(param).get
      }
      thrown.getMessage should be("存在しないデータセットが指定されました。")
    }
    "no userName" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = AddAclUserParameter.fromMap(Map("datasetId" -> dataset.getId, "accessLevel" -> "limitedRead"))
        DatasetService.applyAddAclUser(param).get
      }
      thrown.getMessage should be("ユーザー名の指定がありません。")
    }
    "not exists userName" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = AddAclUserParameter.fromMap(Map("datasetId" -> dataset.getId, "userName" -> "hoge", "accessLevel" -> "limitedRead"))
        DatasetService.applyAddAclUser(param).get
      }
      thrown.getMessage should be("存在しないユーザーが指定されました。")
    }
    "disabled user" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      disableUser("cc130a5e-cb93-4ec2-80f6-78fa83f9bd04")
      val thrown = the[ServiceException] thrownBy {
        val param = AddAclUserParameter.fromMap(Map("datasetId" -> dataset.getId, "userName" -> "dummy2", "accessLevel" -> "limitedRead"))
        DatasetService.applyAddAclUser(param).get
      }
      thrown.getMessage should be("無効なユーザーが指定されました。")
    }
    "yet registered user" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = AddAclUserParameter.fromMap(Map("datasetId" -> dataset.getId, "userName" -> "dummy1", "accessLevel" -> "limitedRead"))
        DatasetService.applyAddAclUser(param).get
      }
      thrown.getMessage should be("既に登録のあるユーザーが指定されました。")
    }
    val validAccessLevel = Seq(Some("limitedRead"), Some("fullRead"), Some("owner"))
    val accessLevelMap = Map(Option("limitedRead") -> AccessLevel.LimitedRead, Option("fullRead") -> AccessLevel.FullRead, Option("owner") -> AccessLevel.Owner)
    for {
      accessLevel <- Seq(None, Some("limitedRead"), Some("fullRead"), Some("owner"), Some("provider"), Some("deny"), Some("aaa"))
    } {
      s"add ${accessLevel}" in {
        val client = SpecCommonLogic.createClient()
        val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
        val dataset = client.createDataset("dataset1", true, false, file)
        val map = Map("datasetId" -> dataset.getId, "userName" -> "dummy2") ++ accessLevel.map { s: String => Map("accessLevel" -> s) }.getOrElse(Map.empty)
        val param = AddAclUserParameter.fromMap(map)
        if (validAccessLevel.contains(accessLevel)) {
          DatasetService.applyAddAclUser(param)
          val searchParam = SearchAclsParameter.fromMap(Map("datasetId" -> dataset.getId))
          val result = DatasetService.getAclListData(searchParam).get
          result.ownerships.filter(_.name == "dummy2").length should be(1)
          result.ownerships.filter(_.name == "dummy2").foreach { ownership =>
            ownership.accessLevel should be(accessLevelMap(accessLevel))
          }
        } else {
          val thrown = the[ServiceException] thrownBy {
            DatasetService.applyAddAclUser(param).get
          }
          thrown.getMessage should be("無効なアクセス権が指定されました。")
        }
      }
    }
  }

  "acl add group" - {
    "no datasetId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = AddAclGroupParameter.fromMap(Map.empty)
        DatasetService.applyAddAclGroup(param).get
      }
      thrown.getMessage should be("データセットIDの指定がありません。")
    }
    "invalid datasetId" in {
      val client = SpecCommonLogic.createClient()
      client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = AddAclGroupParameter.fromMap(Map("datasetId" -> "test", "groupName" -> "group1", "accessLevel" -> "limitedRead"))
        DatasetService.applyAddAclGroup(param).get
      }
    }
    "not exists datasetId" in {
      val client = SpecCommonLogic.createClient()
      client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[ServiceException] thrownBy {
        val param = AddAclGroupParameter.fromMap(Map("datasetId" -> UUID.randomUUID.toString, "groupName" -> "group1", "accessLevel" -> "limitedRead"))
        DatasetService.applyAddAclGroup(param).get
      }
      thrown.getMessage should be("存在しないデータセットが指定されました。")
    }
    "no groupName" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = AddAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId, "accessLevel" -> "limitedRead"))
        DatasetService.applyAddAclGroup(param).get
      }
      thrown.getMessage should be("グループ名の指定がありません。")
    }
    "not exists groupName" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = AddAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId, "groupName" -> "hoge", "accessLevel" -> "limitedRead"))
        DatasetService.applyAddAclGroup(param).get
      }
      thrown.getMessage should be("存在しないグループが指定されました。")
    }
    "deleted group" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      client.deleteGroup(group.getId)
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      disableUser("cc130a5e-cb93-4ec2-80f6-78fa83f9bd04")
      val thrown = the[ServiceException] thrownBy {
        val param = AddAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId, "groupName" -> "group1", "accessLevel" -> "limitedRead"))
        DatasetService.applyAddAclGroup(param).get
      }
      thrown.getMessage should be("削除されたグループが指定されました。")
    }
    "yet registered group" in {
      import scala.collection.JavaConverters._
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val acls = Seq(
        new SetAccessLevelParam(group.getId, persistence.OwnerType.Group, persistence.GroupAccessLevel.LimitedPublic)
      )
      client.changeAccessLevel(dataset.getId, acls.asJava)
      val thrown = the[ServiceException] thrownBy {
        val param = AddAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId, "groupName" -> "group1", "accessLevel" -> "limitedRead"))
        DatasetService.applyAddAclGroup(param).get
      }
      thrown.getMessage should be("既に登録のあるグループが指定されました。")
    }
    val validAccessLevel = Seq(Some("limitedRead"), Some("fullRead"), Some("provider"))
    val accessLevelMap = Map(Option("limitedRead") -> AccessLevel.LimitedRead, Option("fullRead") -> AccessLevel.FullRead, Option("provider") -> AccessLevel.Provider)
    for {
      accessLevel <- Seq(None, Some("limitedRead"), Some("fullRead"), Some("owner"), Some("provider"), Some("deny"), Some("aaa"))
    } {
      s"add ${accessLevel}" in {
        val client = SpecCommonLogic.createClient()
        val group = client.createGroup(CreateGroupParam("group1", ""))
        val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
        val dataset = client.createDataset("dataset1", true, false, file)
        val map = Map("datasetId" -> dataset.getId, "groupName" -> "group1") ++ accessLevel.map { s: String => Map("accessLevel" -> s) }.getOrElse(Map.empty)
        val param = AddAclGroupParameter.fromMap(map)
        if (validAccessLevel.contains(accessLevel)) {
          DatasetService.applyAddAclGroup(param)
          val searchParam = SearchAclsParameter.fromMap(Map("datasetId" -> dataset.getId))
          val result = DatasetService.getAclListData(searchParam).get
          result.ownerships.filter(_.name == "group1").length should be(1)
          result.ownerships.filter(_.name == "group1").foreach { ownership =>
            ownership.accessLevel should be(accessLevelMap(accessLevel))
          }
        } else {
          val thrown = the[ServiceException] thrownBy {
            DatasetService.applyAddAclGroup(param).get
          }
          thrown.getMessage should be("無効なアクセス権が指定されました。")
        }
      }
    }
  }
  "acl update user show" - {
    "no datasetId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = SearchAclUserParameter.fromMap(Map.empty)
        DatasetService.getAclUpdateDataForUser(param).get
      }
      thrown.getMessage should be("データセットIDの指定がありません。")
    }
    "invalid datasetId" in {
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = SearchAclUserParameter.fromMap(Map("datasetId" -> "test", "userId" -> userId1))
        DatasetService.getAclUpdateDataForUser(param).get
      }
    }
    "not exists datasetId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = SearchAclUserParameter.fromMap(Map("datasetId" -> UUID.randomUUID.toString, "userId" -> userId1))
        DatasetService.getAclUpdateDataForUser(param).get
      }
      thrown.getMessage should be("存在しないデータセットが指定されました。")
    }
    "no userId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = SearchAclUserParameter.fromMap(Map("datasetId" -> dataset.getId))
        DatasetService.getAclUpdateDataForUser(param).get
      }
      thrown.getMessage should be("ユーザーIDの指定がありません。")
    }
    "invalid userId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = SearchAclUserParameter.fromMap(Map("datasetId" -> dataset.getId, "userId" -> "test"))
        DatasetService.getAclUpdateDataForUser(param).get
      }
    }
    "not exists userId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = SearchAclUserParameter.fromMap(Map("datasetId" -> dataset.getId, "userId" -> UUID.randomUUID.toString))
        DatasetService.getAclUpdateDataForUser(param).get
      }
      thrown.getMessage should be("存在しないユーザーが指定されました。")
    }
    "disabled user" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      disableUser(userId1)
      val thrown = the[ServiceException] thrownBy {
        val param = SearchAclUserParameter.fromMap(Map("datasetId" -> dataset.getId, "userId" -> userId1))
        DatasetService.getAclUpdateDataForUser(param).get
      }
      thrown.getMessage should be("無効なユーザーが指定されました。")
    }
    "success" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val param = SearchAclUserParameter.fromMap(Map("datasetId" -> dataset.getId, "userId" -> userId1))
      val result = DatasetService.getAclUpdateDataForUser(param).get
      result.ownership.id should be(userId1)
      result.ownership.name should be("dummy1")
      result.ownership.accessLevel should be(AccessLevel.Owner)
    }
  }

  "acl update user" - {
    "no datasetId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclUserParameter.fromMap(Map.empty)
        DatasetService.applyUpdateAclUser(param).get
      }
      thrown.getMessage should be("データセットIDの指定がありません。")
    }
    "invalid datasetId" in {
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = UpdateAclUserParameter.fromMap(Map("datasetId" -> "test", "userId" -> userId1, "accessLevel" -> "limitedRead"))
        DatasetService.applyUpdateAclUser(param).get
      }
    }
    "not exists datasetId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclUserParameter.fromMap(Map("datasetId" -> UUID.randomUUID.toString, "userId" -> userId1, "accessLevel" -> "limitedRead"))
        DatasetService.applyUpdateAclUser(param).get
      }
      thrown.getMessage should be("存在しないデータセットが指定されました。")
    }
    "no userId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclUserParameter.fromMap(Map("datasetId" -> dataset.getId, "accessLevel" -> "limitedRead"))
        DatasetService.applyUpdateAclUser(param).get
      }
      thrown.getMessage should be("ユーザーIDの指定がありません。")
    }
    "invalid userId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = UpdateAclUserParameter.fromMap(Map("datasetId" -> dataset.getId, "userId" -> "test", "accessLevel" -> "limitedRead"))
        DatasetService.applyUpdateAclUser(param).get
      }
    }
    "not exists userId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclUserParameter.fromMap(Map("datasetId" -> dataset.getId, "userId" -> UUID.randomUUID.toString, "accessLevel" -> "limitedRead"))
        DatasetService.applyUpdateAclUser(param).get
      }
      thrown.getMessage should be("存在しないユーザーが指定されました。")
    }
    "disabled user" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      disableUser(userId1)
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclUserParameter.fromMap(Map("datasetId" -> dataset.getId, "userId" -> userId1, "accessLevel" -> "limitedRead"))
        DatasetService.applyUpdateAclUser(param).get
      }
      thrown.getMessage should be("無効なユーザーが指定されました。")
    }
    "not registered user" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclUserParameter.fromMap(Map("datasetId" -> dataset.getId, "userId" -> userId2, "accessLevel" -> "limitedRead"))
        DatasetService.applyUpdateAclUser(param).get
      }
      thrown.getMessage should be("まだアクセス権の登録がありません。")
    }
    val validAccessLevel = Seq(Some("limitedRead"), Some("fullRead"), Some("owner"))
    val accessLevelMap = Map(Option("limitedRead") -> AccessLevel.LimitedRead, Option("fullRead") -> AccessLevel.FullRead, Option("owner") -> AccessLevel.Owner)
    for {
      accessLevel <- Seq(None, Some("limitedRead"), Some("fullRead"), Some("owner"), Some("provider"), Some("deny"), Some("aaa"))
    } {
      s"update ${accessLevel}" in {
        val client = SpecCommonLogic.createClient()
        val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
        val dataset = client.createDataset("dataset1", true, false, file)
        import scala.collection.JavaConverters._
        val presetAccessLevel = if (accessLevel == Some("limitedRead")) {
          persistence.UserAccessLevel.Owner
        } else {
          persistence.UserAccessLevel.LimitedRead
        }
        val acls = Seq(
          new SetAccessLevelParam(userId2, persistence.OwnerType.User, presetAccessLevel)
        )
        client.changeAccessLevel(dataset.getId, acls.asJava)
        val map = Map("datasetId" -> dataset.getId, "userId" -> userId2) ++ accessLevel.map { s: String => Map("accessLevel" -> s) }.getOrElse(Map.empty)
        val param = UpdateAclUserParameter.fromMap(map)
        if (validAccessLevel.contains(accessLevel)) {
          DatasetService.applyUpdateAclUser(param)
          val searchParam = SearchAclsParameter.fromMap(Map("datasetId" -> dataset.getId))
          val result = DatasetService.getAclListData(searchParam).get
          result.ownerships.filter(_.name == "dummy2").length should be(1)
          result.ownerships.filter(_.name == "dummy2").foreach { ownership =>
            ownership.accessLevel should be(accessLevelMap(accessLevel))
          }
        } else {
          val thrown = the[ServiceException] thrownBy {
            DatasetService.applyUpdateAclUser(param).get
          }
          thrown.getMessage should be("無効なアクセス権が指定されました。")
        }
      }
    }
  }

  "acl delete user" - {
    "no datasetId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclUserParameter.fromMap(Map.empty)
        DatasetService.applyDeleteAclUser(param).get
      }
      thrown.getMessage should be("データセットIDの指定がありません。")
    }
    "invalid datasetId" in {
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = UpdateAclUserParameter.fromMap(Map("datasetId" -> "test", "userId" -> userId1))
        DatasetService.applyDeleteAclUser(param).get
      }
    }
    "not exists datasetId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclUserParameter.fromMap(Map("datasetId" -> UUID.randomUUID.toString, "userId" -> userId1))
        DatasetService.applyDeleteAclUser(param).get
      }
      thrown.getMessage should be("存在しないデータセットが指定されました。")
    }
    "no userId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclUserParameter.fromMap(Map("datasetId" -> dataset.getId))
        DatasetService.applyDeleteAclUser(param).get
      }
      thrown.getMessage should be("ユーザーIDの指定がありません。")
    }
    "invalid userId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = UpdateAclUserParameter.fromMap(Map("datasetId" -> dataset.getId, "userId" -> "test"))
        DatasetService.applyDeleteAclUser(param).get
      }
    }
    "not exists userId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclUserParameter.fromMap(Map("datasetId" -> dataset.getId, "userId" -> UUID.randomUUID.toString))
        DatasetService.applyDeleteAclUser(param).get
      }
      thrown.getMessage should be("存在しないユーザーが指定されました。")
    }
    "disabled user" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      disableUser(userId1)
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclUserParameter.fromMap(Map("datasetId" -> dataset.getId, "userId" -> userId1))
        DatasetService.applyDeleteAclUser(param).get
      }
      thrown.getMessage should be("無効なユーザーが指定されました。")
    }
    "not registered user" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclUserParameter.fromMap(Map("datasetId" -> dataset.getId, "userId" -> userId2))
        DatasetService.applyDeleteAclUser(param).get
      }
      thrown.getMessage should be("まだアクセス権の登録がありません。")
    }
    s"delete success" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val map = Map("datasetId" -> dataset.getId, "userId" -> userId1)
      val param = UpdateAclUserParameter.fromMap(map)
      DatasetService.applyDeleteAclUser(param)
      val searchParam = SearchAclsParameter.fromMap(Map("datasetId" -> dataset.getId))
      val result = DatasetService.getAclListData(searchParam).get
      result.ownerships.filter(_.name == "dummy1").length should be(0)
    }
  }

  "acl update group show" - {
    "no datasetId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = SearchAclGroupParameter.fromMap(Map.empty)
        DatasetService.getAclUpdateDataForGroup(param).get
      }
      thrown.getMessage should be("データセットIDの指定がありません。")
    }
    "invalid datasetId" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = SearchAclGroupParameter.fromMap(Map("datasetId" -> "test", "groupId" -> group.getId))
        DatasetService.getAclUpdateDataForGroup(param).get
      }
    }
    "not exists datasetId" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[ServiceException] thrownBy {
        val param = SearchAclGroupParameter.fromMap(Map("datasetId" -> UUID.randomUUID.toString, "groupId" -> group.getId))
        DatasetService.getAclUpdateDataForGroup(param).get
      }
      thrown.getMessage should be("存在しないデータセットが指定されました。")
    }
    "no groupId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = SearchAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId))
        DatasetService.getAclUpdateDataForGroup(param).get
      }
      thrown.getMessage should be("グループIDの指定がありません。")
    }
    "invalid groupId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = SearchAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId, "groupId" -> "test"))
        DatasetService.getAclUpdateDataForGroup(param).get
      }
    }
    "not exists groupId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = SearchAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId, "groupId" -> UUID.randomUUID.toString))
        DatasetService.getAclUpdateDataForGroup(param).get
      }
      thrown.getMessage should be("存在しないグループが指定されました。")
    }
    "deleted group" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val group = client.createGroup(CreateGroupParam("group1", ""))
      addAclGroup(client, dataset.getId, group.getId, persistence.GroupAccessLevel.Provider)
      client.deleteGroup(group.getId)
      val thrown = the[ServiceException] thrownBy {
        val param = SearchAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId, "groupId" -> group.getId))
        DatasetService.getAclUpdateDataForGroup(param).get
      }
      thrown.getMessage should be("削除されたグループが指定されました。")
    }
    "success" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val group = client.createGroup(CreateGroupParam("group1", ""))
      addAclGroup(client, dataset.getId, group.getId, persistence.GroupAccessLevel.Provider)
      val param = SearchAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId, "groupId" -> group.getId))
      val result = DatasetService.getAclUpdateDataForGroup(param).get
      result.ownership.id should be(group.getId)
      result.ownership.name should be("group1")
      result.ownership.accessLevel should be(AccessLevel.Provider)
    }
  }

  "acl update group" - {
    "no datasetId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclGroupParameter.fromMap(Map.empty)
        DatasetService.applyUpdateAclGroup(param).get
      }
      thrown.getMessage should be("データセットIDの指定がありません。")
    }
    "invalid datasetId" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = UpdateAclGroupParameter.fromMap(Map("datasetId" -> "test", "groupId" -> group.getId, "accessLevel" -> "limitedRead"))
        DatasetService.applyUpdateAclGroup(param).get
      }
    }
    "not exists datasetId" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclGroupParameter.fromMap(Map("datasetId" -> UUID.randomUUID.toString, "groupId" -> group.getId, "accessLevel" -> "limitedRead"))
        DatasetService.applyUpdateAclGroup(param).get
      }
      thrown.getMessage should be("存在しないデータセットが指定されました。")
    }
    "no groupId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId, "accessLevel" -> "limitedRead"))
        DatasetService.applyUpdateAclGroup(param).get
      }
      thrown.getMessage should be("グループIDの指定がありません。")
    }
    "invalid groupId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = UpdateAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId, "groupId" -> "test", "accessLevel" -> "limitedRead"))
        DatasetService.applyUpdateAclGroup(param).get
      }
    }
    "not exists groupId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId, "groupId" -> UUID.randomUUID.toString, "accessLevel" -> "limitedRead"))
        DatasetService.applyUpdateAclGroup(param).get
      }
      thrown.getMessage should be("存在しないグループが指定されました。")
    }
    "deleted group" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val group = client.createGroup(CreateGroupParam("group1", ""))
      addAclGroup(client, dataset.getId, group.getId, persistence.GroupAccessLevel.Provider)
      client.deleteGroup(group.getId)
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId, "groupId" -> group.getId, "accessLevel" -> "limitedRead"))
        DatasetService.applyUpdateAclGroup(param).get
      }
      thrown.getMessage should be("削除されたグループが指定されました。")
    }
    "not registered group" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId, "groupId" -> group.getId, "accessLevel" -> "limitedRead"))
        DatasetService.applyUpdateAclGroup(param).get
      }
      thrown.getMessage should be("まだアクセス権の登録がありません。")
    }
    val validAccessLevel = Seq(Some("limitedRead"), Some("fullRead"), Some("provider"))
    val accessLevelMap = Map(Option("limitedRead") -> AccessLevel.LimitedRead, Option("fullRead") -> AccessLevel.FullRead, Option("provider") -> AccessLevel.Provider)
    for {
      accessLevel <- Seq(None, Some("limitedRead"), Some("fullRead"), Some("owner"), Some("provider"), Some("deny"), Some("aaa"))
    } {
      s"update ${accessLevel}" in {
        val client = SpecCommonLogic.createClient()
        val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
        val dataset = client.createDataset("dataset1", true, false, file)
        val group = client.createGroup(CreateGroupParam("group1", ""))
        val presetAccessLevel = if (accessLevel == Some("limitedRead")) {
          persistence.GroupAccessLevel.Provider
        } else {
          persistence.GroupAccessLevel.LimitedPublic
        }
        addAclGroup(client, dataset.getId, group.getId, presetAccessLevel)
        val map = Map("datasetId" -> dataset.getId, "groupId" -> group.getId) ++ accessLevel.map { s: String => Map("accessLevel" -> s) }.getOrElse(Map.empty)
        val param = UpdateAclGroupParameter.fromMap(map)
        if (validAccessLevel.contains(accessLevel)) {
          DatasetService.applyUpdateAclGroup(param)
          val searchParam = SearchAclsParameter.fromMap(Map("datasetId" -> dataset.getId))
          val result = DatasetService.getAclListData(searchParam).get
          result.ownerships.filter(_.name == "group1").length should be(1)
          result.ownerships.filter(_.name == "group1").foreach { ownership =>
            ownership.accessLevel should be(accessLevelMap(accessLevel))
          }
        } else {
          val thrown = the[ServiceException] thrownBy {
            DatasetService.applyUpdateAclGroup(param).get
          }
          thrown.getMessage should be("無効なアクセス権が指定されました。")
        }
      }
    }
  }

  "acl delete group" - {
    "no datasetId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclGroupParameter.fromMap(Map.empty)
        DatasetService.applyDeleteAclGroup(param).get
      }
      thrown.getMessage should be("データセットIDの指定がありません。")
    }
    "invalid datasetId" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = UpdateAclGroupParameter.fromMap(Map("datasetId" -> "test", "groupId" -> group.getId))
        DatasetService.applyDeleteAclGroup(param).get
      }
    }
    "not exists datasetId" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclGroupParameter.fromMap(Map("datasetId" -> UUID.randomUUID.toString, "groupId" -> group.getId))
        DatasetService.applyDeleteAclGroup(param).get
      }
      thrown.getMessage should be("存在しないデータセットが指定されました。")
    }
    "no groupId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId))
        DatasetService.applyDeleteAclGroup(param).get
      }
      thrown.getMessage should be("グループIDの指定がありません。")
    }
    "invalid groupId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = UpdateAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId, "groupId" -> "test"))
        DatasetService.applyDeleteAclGroup(param).get
      }
    }
    "not exists groupId" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId, "groupId" -> UUID.randomUUID.toString))
        DatasetService.applyDeleteAclGroup(param).get
      }
      thrown.getMessage should be("存在しないグループが指定されました。")
    }
    "deleted group" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val group = client.createGroup(CreateGroupParam("group1", ""))
      addAclGroup(client, dataset.getId, group.getId, persistence.GroupAccessLevel.Provider)
      client.deleteGroup(group.getId)
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId, "groupId" -> group.getId))
        DatasetService.applyDeleteAclGroup(param).get
      }
      thrown.getMessage should be("削除されたグループが指定されました。")
    }
    "not registered group" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateAclGroupParameter.fromMap(Map("datasetId" -> dataset.getId, "groupId" -> group.getId))
        DatasetService.applyDeleteAclGroup(param).get
      }
      thrown.getMessage should be("まだアクセス権の登録がありません。")
    }
    s"delete success" in {
      val client = SpecCommonLogic.createClient()
      val file = new java.io.File("./testdata/maintenance/dataset/test.csv")
      val dataset = client.createDataset("dataset1", true, false, file)
      val group = client.createGroup(CreateGroupParam("group1", ""))
      addAclGroup(client, dataset.getId, group.getId, persistence.GroupAccessLevel.Provider)
      val map = Map("datasetId" -> dataset.getId, "groupId" -> group.getId)
      val param = UpdateAclGroupParameter.fromMap(map)
      DatasetService.applyDeleteAclGroup(param)
      val searchParam = SearchAclsParameter.fromMap(Map("datasetId" -> dataset.getId))
      val result = DatasetService.getAclListData(searchParam).get
      result.ownerships.filter(_.name == "group1").length should be(0)
    }
  }

  def addAclGroup(client: DsmoqClient, datasetId: String, groupId: String, accessLevel: Int): Unit = {
    import scala.collection.JavaConverters._
    val acls = Seq(
      new SetAccessLevelParam(groupId, persistence.OwnerType.Group, accessLevel)
    )
    client.changeAccessLevel(datasetId, acls.asJava)
  }

  def disableUser(userId: String): Unit = {
    val map = org.scalatra.util.MultiMap(Map(
      "disabled.originals" -> Seq.empty,
      "disabled.updates" -> Seq(userId)
    ))
    val param = data.user.UpdateParameter.fromMap(map)
    UserService.updateDisabled(param)
  }

  def createDataset(client: DsmoqClient, name: String, delete: Boolean): String = {
    val file = new java.io.File(s"./testdata/maintenance/dataset/test.csv")
    val dataset = client.createDataset(name, true, false, file)
    if (delete) {
      client.deleteDataset(dataset.getId)
    }
    dataset.getId
  }
  def resetUpdatedAt(datasets: Seq[String]): Unit = {
    DB.localTx { implicit s =>
      val d = persistence.Dataset.column
      withSQL {
        update(persistence.Dataset)
          .set(
            d.updatedAt -> defaultUpdatedAt
          )
          .where
          .in(d.id, datasets.map(sqls.uuid))
      }.update.apply()
    }
  }
  def getDatasets(datasetIds: Seq[String]): Seq[persistence.Dataset] = {
    val d = persistence.Dataset.d
    DB.readOnly { implicit s =>
      withSQL {
        select
          .from(persistence.Dataset as d)
          .where
          .inUuid(d.id, datasetIds)
          .orderBy(d.createdAt)
      }.map(persistence.Dataset(d.resultName)).list.apply()
    }
  }
  def toParam(targets: Seq[String]): UpdateParameter = {
    val map = org.scalatra.util.MultiMap(Map("checked" -> targets))
    UpdateParameter.fromMap(map)
  }
}
