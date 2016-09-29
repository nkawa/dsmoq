package dsmoq.maintenance.services

import java.util.UUID
import java.nio.file.Paths
import org.joda.time.DateTime

import org.scalatest.BeforeAndAfter
import org.scalatest.FreeSpec
import org.scalatest.Matchers._

import scalikejdbc.config.DBsWithEnv

import dsmoq.persistence
import dsmoq.persistence.PostgresqlHelper.PgConditionSQLBuilder
import dsmoq.persistence.PostgresqlHelper.PgSQLSyntaxType
import dsmoq.maintenance.AppConfig
import dsmoq.maintenance.data
import dsmoq.maintenance.data.group.MemberRole
import dsmoq.maintenance.data.group.SearchCondition
import dsmoq.maintenance.data.group.SearchMemberParameter
import dsmoq.maintenance.data.group.SearchMembersParameter
import dsmoq.maintenance.data.group.UpdateParameter
import dsmoq.maintenance.data.group.AddMemberParameter
import dsmoq.maintenance.data.group.UpdateMemberParameter

import jp.ac.nagoya_u.dsmoq.sdk.client.DsmoqClient
import jp.ac.nagoya_u.dsmoq.sdk.request.CreateGroupParam
import jp.ac.nagoya_u.dsmoq.sdk.request.AddMemberParam
import jp.ac.nagoya_u.dsmoq.sdk.request.SetAccessLevelParam

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

class GroupServiceSpec extends FreeSpec with BeforeAndAfter {
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
    def updateCreatedAt(groups: Seq[String]): Unit = {
      DB.localTx { implicit s =>
        val g = persistence.Group.column
        for {
          (id, index) <- groups.zipWithIndex
        } {
          withSQL {
            update(persistence.Group)
              .set(
                g.createdAt -> new DateTime(2016, 9, 22, 0, 0, index)
              )
              .where
              .eq(g.id, sqls.uuid(id))
          }.update.apply()
        }
      }
    }
    def prepareGroups(): Seq[persistence.Group] = {
      val client1 = SpecCommonLogic.createClient()
      val client2 = SpecCommonLogic.createClient2()
      val groups = Seq(
        createGroup(client1, "dummy1", false),
        createGroup(client1, "dummy2", false),
        createGroup(client1, "dummy3", true),
        createGroup(client1, "dummy4", true),
        createGroup(client1, "test1", false),
        createGroup(client1, "test2", true),
        createGroup(client2, "dummy5", false),
        createGroup(client2, "dummy6", true),
        createGroup(client2, "test3", false),
        createGroup(client2, "test4", true)
      )
      updateCreatedAt(groups)
      getGroups(groups)
    }
    "groupType=None, managerId=None, groupName=None, page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map.empty)
      val results = GroupService.search(condition)
      val expects = groups.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "groupType=None, managerId=Some(not exists), groupName=Some(not exists), page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("managerId" -> "hoge", "groupName" -> "fuga"))
      val results = GroupService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "groupType=None, managerId=Some(exists), groupName=None, page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("managerId" -> "dummy1"))
      val results = GroupService.search(condition)
      val expects = groups.filter(_.createdBy == userId1).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "groupType=None, managerId=None, groupName=Some(exists), page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("groupName" -> "test"))
      val results = GroupService.search(condition)
      val expects = groups.filter(_.name.contains("test")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "groupType=None, managerId=Some(exists), groupName=Some(exists), page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("managerId" -> "dummy1", "groupName" -> "test"))
      val results = GroupService.search(condition)
      val expects = groups.filter(_.createdBy == userId1).filter(_.name.contains("test")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "groupType=Some(all), managerId=None, groupName=None, page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("groupType" -> "all"))
      val results = GroupService.search(condition)
      val expects = groups.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "groupType=Some(all), managerId=Some(not exists), groupName=Some(not exists), page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("groupType" -> "all", "managerId" -> "hoge", "groupName" -> "fuga"))
      val results = GroupService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "groupType=Some(all), managerId=Some(exists), groupName=None, page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("groupType" -> "all", "managerId" -> "dummy1"))
      val results = GroupService.search(condition)
      val expects = groups.filter(_.createdBy == userId1).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "groupType=Some(all), managerId=None, groupName=Some(exists), page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("groupType" -> "all", "groupName" -> "test"))
      val results = GroupService.search(condition)
      val expects = groups.filter(_.name.contains("test")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "groupType=Some(all), managerId=Some(exists), groupName=Some(exists), page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("groupType" -> "all", "managerId" -> "dummy1", "groupName" -> "test"))
      val results = GroupService.search(condition)
      val expects = groups.filter(_.createdBy == userId1).filter(_.name.contains("test")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "groupType=Some(not_deleted), managerId=None, groupName=None, page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("groupType" -> "not_deleted"))
      val results = GroupService.search(condition)
      val expects = groups.filter(!_.deletedAt.isDefined).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "groupType=Some(not_deleted), managerId=Some(not exists), groupName=Some(not exists), page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("groupType" -> "not_deleted", "managerId" -> "hoge", "groupName" -> "fuga"))
      val results = GroupService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "groupType=Some(not_deleted), managerId=Some(exists), groupName=None, page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("groupType" -> "not_deleted", "managerId" -> "dummy1"))
      val results = GroupService.search(condition)
      val expects = groups.filter(!_.deletedAt.isDefined).filter(_.createdBy == userId1).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "groupType=Some(not_deleted), managerId=None, groupName=Some(exists), page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("groupType" -> "not_deleted", "groupName" -> "test"))
      val results = GroupService.search(condition)
      val expects = groups.filter(!_.deletedAt.isDefined).filter(_.name.contains("test")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "groupType=Some(not_deleted), managerId=Some(exists), groupName=Some(exists), page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("groupType" -> "not_deleted", "managerId" -> "dummy1", "groupName" -> "test"))
      val results = GroupService.search(condition)
      val expects = groups.filter(!_.deletedAt.isDefined).filter(_.createdBy == userId1).filter(_.name.contains("test")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "groupType=Some(deleted), managerId=None, groupName=None, page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("groupType" -> "deleted"))
      val results = GroupService.search(condition)
      val expects = groups.filter(_.deletedAt.isDefined).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "groupType=Some(deleted), managerId=Some(not exists), groupName=Some(not exists), page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("groupType" -> "deleted", "managerId" -> "hoge", "groupName" -> "fuga"))
      val results = GroupService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "groupType=Some(deleted), managerId=Some(exists), groupName=None, page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("groupType" -> "deleted", "managerId" -> "dummy1"))
      val results = GroupService.search(condition)
      val expects = groups.filter(_.deletedAt.isDefined).filter(_.createdBy == userId1).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "groupType=Some(deleted), managerId=None, groupName=Some(exists), page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("groupType" -> "deleted", "groupName" -> "test"))
      val results = GroupService.search(condition)
      val expects = groups.filter(_.deletedAt.isDefined).filter(_.name.contains("test")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "groupType=Some(deleted), managerId=Some(exists), groupName=Some(exists), page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("groupType" -> "deleted", "managerId" -> "dummy1", "groupName" -> "test"))
      val results = GroupService.search(condition)
      val expects = groups.filter(_.deletedAt.isDefined).filter(_.createdBy == userId1).filter(_.name.contains("test")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "groupType=Some(aaa), managerId=None, groupName=None, page=None" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("groupType" -> "aaa"))
      val results = GroupService.search(condition)
      val expects = groups.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "groupType=None, managerId=None, groupName=None, page=Some(-1)" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("page" -> "-1"))
      val results = GroupService.search(condition)
      val expects = groups.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "groupType=None, managerId=None, groupName=None, page=Some(0)" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("page" -> "0"))
      val results = GroupService.search(condition)
      val expects = groups.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "groupType=None, managerId=None, groupName=None, page=Some(1)" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("page" -> "1"))
      val results = GroupService.search(condition)
      val expects = groups.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "groupType=None, managerId=None, groupName=None, page=Some(2)" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("page" -> "2"))
      val results = GroupService.search(condition)
      val expects = groups.map(_.id).drop(5).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(6)
      results.to should be(10)
    }
    "groupType=None, managerId=None, groupName=None, page=Some(3)" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("page" -> "3"))
      val results = GroupService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(10)
    }
    "groupType=None, managerId=None, groupName=None, page=Some(a)" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("page" -> "a"))
      val results = GroupService.search(condition)
      val expects = groups.map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "groupType=Some(not_deleted), managerId=None, groupName=None, page=Some(2)" in {
      val groups = prepareGroups()
      val condition = SearchCondition.fromMap(Map("groupType" -> "not_deleted", "page" -> "2"))
      val results = GroupService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(5)
    }
  }

  "logical delete" - {
    def prepareGroups(): Seq[String] = {
      val client = SpecCommonLogic.createClient()
      val group1 = client.createGroup(CreateGroupParam("group1", ""))
      val group2 = client.createGroup(CreateGroupParam("group2", ""))
      resetUpdatedAt(Seq(group1.getId, group2.getId))
      Seq(group1.getId, group2.getId)
    }
    "no select" in {
      val groupIds = prepareGroups()
      val thrown = the[ServiceException] thrownBy {
        GroupService.applyLogicalDelete(toParam(Seq.empty)).get
      }
      thrown.getMessage should be("グループが選択されていません。")
      getGroups(groupIds).filter(g => g.updatedAt.isAfter(defaultUpdatedAt)).size should be(0)
    }
    "invalid ID x exists ID" in {
      val groupIds = prepareGroups()
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        GroupService.applyLogicalDelete(toParam(Seq("test", groupIds(1)))).get
      }
      getGroups(groupIds).filter(g => g.updatedAt.isAfter(defaultUpdatedAt)).size should be(0)
    }
    "exists ID x invalid ID" in {
      val groupIds = prepareGroups()
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        GroupService.applyLogicalDelete(toParam(Seq(groupIds(0), "test"))).get
      }
      getGroups(groupIds).filter(g => g.updatedAt.isAfter(defaultUpdatedAt)).size should be(0)
    }
    "not exists ID x exists ID" in {
      val groupIds = prepareGroups()
      GroupService.applyLogicalDelete(toParam(Seq(UUID.randomUUID.toString, groupIds(1)))).get
      getGroups(groupIds).filter(g => g.updatedAt.isAfter(defaultUpdatedAt)).size should be(1)
      persistence.Group.find(groupIds(1)).foreach { group =>
        group.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        group.deletedBy.isDefined should be(true)
        group.deletedAt.isDefined should be(true)
      }
    }
    "exists ID x not exists ID" in {
      val groupIds = prepareGroups()
      GroupService.applyLogicalDelete(toParam(Seq(groupIds(0), UUID.randomUUID.toString))).get
      persistence.Group.find(groupIds(0)).foreach { group =>
        group.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        group.deletedBy.isDefined should be(true)
        group.deletedAt.isDefined should be(true)
      }
    }
    "not deleted" in {
      val groupIds = prepareGroups()
      GroupService.applyLogicalDelete(toParam(Seq(groupIds(0)))).get
      persistence.Group.find(groupIds(0)).foreach { group =>
        group.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        group.deletedBy.isDefined should be(true)
        group.deletedAt.isDefined should be(true)
      }
    }
    "deleted" in {
      val groupIds = prepareGroups()
      val client = SpecCommonLogic.createClient()
      client.deleteGroup(groupIds(0))
      resetUpdatedAt(groupIds)
      GroupService.applyLogicalDelete(toParam(Seq(groupIds(0)))).get
      persistence.Group.find(groupIds(0)).foreach { group =>
        group.updatedAt.isAfter(defaultUpdatedAt) should be(false)
        group.deletedBy.isDefined should be(true)
        group.deletedAt.isDefined should be(true)
      }
    }
    "deleted and not deleted" in {
      val groupIds = prepareGroups()
      val client = SpecCommonLogic.createClient()
      client.deleteGroup(groupIds(0))
      resetUpdatedAt(groupIds)
      GroupService.applyLogicalDelete(toParam(groupIds)).get
      persistence.Group.find(groupIds(0)).foreach { group =>
        group.updatedAt.isAfter(defaultUpdatedAt) should be(false)
        group.deletedBy.isDefined should be(true)
        group.deletedAt.isDefined should be(true)
      }
      persistence.Group.find(groupIds(1)).foreach { group =>
        group.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        group.deletedBy.isDefined should be(true)
        group.deletedAt.isDefined should be(true)
      }
    }
    "not deleted and not deleted" in {
      val groupIds = prepareGroups()
      GroupService.applyLogicalDelete(toParam(groupIds)).get
      persistence.Group.find(groupIds(0)).foreach { group =>
        group.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        group.deletedBy.isDefined should be(true)
        group.deletedAt.isDefined should be(true)
      }
      persistence.Group.find(groupIds(1)).foreach { group =>
        group.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        group.deletedBy.isDefined should be(true)
        group.deletedAt.isDefined should be(true)
      }
    }
  }

  "cancel logical delete" - {
    def prepareGroups(): Seq[String] = {
      val client = SpecCommonLogic.createClient()
      val group1 = client.createGroup(CreateGroupParam("group1", ""))
      val group2 = client.createGroup(CreateGroupParam("group2", ""))
      resetUpdatedAt(Seq(group1.getId, group2.getId))
      Seq(group1.getId, group2.getId)
    }
    "no select" in {
      val groupIds = prepareGroups()
      val thrown = the[ServiceException] thrownBy {
        GroupService.applyCancelLogicalDelete(toParam(Seq.empty)).get
      }
      thrown.getMessage should be("グループが選択されていません。")
      getGroups(groupIds).filter(g => g.updatedAt.isAfter(defaultUpdatedAt)).size should be(0)
    }
    "invalid ID x exists ID" in {
      val groupIds = prepareGroups()
      val client = SpecCommonLogic.createClient()
      client.deleteGroup(groupIds(1))
      resetUpdatedAt(groupIds)
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        GroupService.applyCancelLogicalDelete(toParam(Seq("test", groupIds(1)))).get
      }
      getGroups(groupIds).filter(g => g.updatedAt.isAfter(defaultUpdatedAt)).size should be(0)
    }
    "exists ID x invalid ID" in {
      val groupIds = prepareGroups()
      val client = SpecCommonLogic.createClient()
      client.deleteGroup(groupIds(0))
      resetUpdatedAt(groupIds)
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        GroupService.applyCancelLogicalDelete(toParam(Seq(groupIds(0), "test"))).get
      }
      getGroups(groupIds).filter(g => g.updatedAt.isAfter(defaultUpdatedAt)).size should be(0)
    }
    "not exists ID x exists ID" in {
      val groupIds = prepareGroups()
      val client = SpecCommonLogic.createClient()
      client.deleteGroup(groupIds(1))
      resetUpdatedAt(groupIds)
      GroupService.applyCancelLogicalDelete(toParam(Seq(UUID.randomUUID.toString, groupIds(1)))).get
      getGroups(groupIds).filter(g => g.updatedAt.isAfter(defaultUpdatedAt)).size should be(1)
      persistence.Group.find(groupIds(1)).foreach { group =>
        group.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        group.deletedBy.isDefined should be(false)
        group.deletedAt.isDefined should be(false)
      }
    }
    "exists ID x not exists ID" in {
      val groupIds = prepareGroups()
      val client = SpecCommonLogic.createClient()
      client.deleteGroup(groupIds(0))
      resetUpdatedAt(groupIds)
      GroupService.applyCancelLogicalDelete(toParam(Seq(groupIds(0), UUID.randomUUID.toString))).get
      persistence.Group.find(groupIds(0)).foreach { group =>
        group.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        group.deletedBy.isDefined should be(false)
        group.deletedAt.isDefined should be(false)
      }
    }
    "not deleted" in {
      val groupIds = prepareGroups()
      GroupService.applyCancelLogicalDelete(toParam(Seq(groupIds(0)))).get
      persistence.Group.find(groupIds(0)).foreach { group =>
        group.updatedAt.isAfter(defaultUpdatedAt) should be(false)
        group.deletedBy.isDefined should be(false)
        group.deletedAt.isDefined should be(false)
      }
    }
    "deleted" in {
      val groupIds = prepareGroups()
      val client = SpecCommonLogic.createClient()
      client.deleteGroup(groupIds(0))
      resetUpdatedAt(groupIds)
      GroupService.applyCancelLogicalDelete(toParam(Seq(groupIds(0)))).get
      persistence.Group.find(groupIds(0)).foreach { group =>
        group.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        group.deletedBy.isDefined should be(false)
        group.deletedAt.isDefined should be(false)
      }
    }
    "deleted and deleted" in {
      val groupIds = prepareGroups()
      val client = SpecCommonLogic.createClient()
      client.deleteGroup(groupIds(0))
      client.deleteGroup(groupIds(1))
      resetUpdatedAt(groupIds)
      GroupService.applyCancelLogicalDelete(toParam(groupIds)).get
      persistence.Group.find(groupIds(0)).foreach { group =>
        group.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        group.deletedBy.isDefined should be(false)
        group.deletedAt.isDefined should be(false)
      }
      persistence.Group.find(groupIds(1)).foreach { group =>
        group.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        group.deletedBy.isDefined should be(false)
        group.deletedAt.isDefined should be(false)
      }
    }
    "deleted and not deleted" in {
      val groupIds = prepareGroups()
      val client = SpecCommonLogic.createClient()
      client.deleteGroup(groupIds(0))
      resetUpdatedAt(groupIds)
      GroupService.applyCancelLogicalDelete(toParam(groupIds)).get
      persistence.Group.find(groupIds(0)).foreach { group =>
        group.updatedAt.isAfter(defaultUpdatedAt) should be(true)
        group.deletedBy.isDefined should be(false)
        group.deletedAt.isDefined should be(false)
      }
      persistence.Group.find(groupIds(1)).foreach { group =>
        group.updatedAt.isAfter(defaultUpdatedAt) should be(false)
        group.deletedBy.isDefined should be(false)
        group.deletedAt.isDefined should be(false)
      }
    }
  }

  "member list" - {
    "no groupId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = SearchMembersParameter.fromMap(Map.empty)
        GroupService.getMemberListData(param).get
      }
      thrown.getMessage should be("グループIDの指定がありません。")
    }
    "invalid groupId" in {
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = SearchMembersParameter.fromMap(Map("groupId" -> "test"))
        GroupService.getMemberListData(param).get
      }
    }
    "not exists groupId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = SearchMembersParameter.fromMap(Map("groupId" -> UUID.randomUUID.toString))
        GroupService.getMemberListData(param).get
      }
      thrown.getMessage should be("存在しないグループが指定されました。")
    }
    "list check" in {
      import scala.collection.JavaConverters._
      val client = SpecCommonLogic.createClient()
      val ts = DateTime.now
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val users = (1 to 4).map { i => SpecCommonLogic.insertUser(SpecCommonLogic.UserDetail(s"user${i}", ts)) }
      val acls = Seq(
        new AddMemberParam(users(0).id, persistence.GroupMemberRole.Deny),
        new AddMemberParam(users(1).id, persistence.GroupMemberRole.Member),
        new AddMemberParam(users(2).id, persistence.GroupMemberRole.Manager),
        new AddMemberParam(users(3).id, persistence.GroupMemberRole.Member)
      )
      client.addMember(group.getId, acls.asJava)
      disableUser(users(3).id)
      val param = SearchMembersParameter.fromMap(Map("groupId" -> group.getId))
      val result = GroupService.getMemberListData(param).get
      val ids = result.members.map(_.id)
      ids.contains(users(0).id) should be(false)
      ids.contains(users(1).id) should be(true)
      ids.contains(users(2).id) should be(true)
      ids.contains(users(3).id) should be(false)
    }
    "empty" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      disableUser(userId1)
      val param = SearchMembersParameter.fromMap(Map("groupId" -> group.getId))
      val result = GroupService.getMemberListData(param).get
      result.members should be(Seq.empty)
    }
    def updateMemberCreateAt(groupId: String, userIds: Seq[String]): Unit = {
      DB.localTx { implicit s =>
        val m = persistence.Member.column
        val o = persistence.Ownership.column
        for (
          (userId, index) <- userIds.zipWithIndex
        ) {
          withSQL {
            update(persistence.Member)
              .set(
                m.createdAt -> new DateTime(2016, 9, 24, 0, 0, index)
              )
              .where
              .eq(m.groupId, sqls.uuid(groupId))
              .and
              .eq(m.userId, sqls.uuid(userId))
          }.update.apply()
        }
      }
    }
    "sort order" in {
      import scala.collection.JavaConverters._
      val client = SpecCommonLogic.createClient()
      val ts = DateTime.now
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val users = (1 to 4).map { i => SpecCommonLogic.insertUser(SpecCommonLogic.UserDetail(s"user${i}", ts)) }
      val acls = Seq(
        new AddMemberParam(users(0).id, persistence.GroupMemberRole.Member),
        new AddMemberParam(users(1).id, persistence.GroupMemberRole.Member),
        new AddMemberParam(users(2).id, persistence.GroupMemberRole.Member),
        new AddMemberParam(users(3).id, persistence.GroupMemberRole.Member)
      )
      client.addMember(group.getId, acls.asJava)
      val ids = Seq(userId1) ++ users.map(_.id)
      updateMemberCreateAt(group.getId, ids)
      val param = SearchMembersParameter.fromMap(Map("groupId" -> group.getId))
      val result = GroupService.getMemberListData(param).get
      result.members.map(_.id) should be(ids)
    }
  }

  "member add show" - {
    "no groupId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = SearchMembersParameter.fromMap(Map.empty)
        GroupService.getMemberAddData(param).get
      }
      thrown.getMessage should be("グループIDの指定がありません。")
    }
    "invalid groupId" in {
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = SearchMembersParameter.fromMap(Map("groupId" -> "test"))
        GroupService.getMemberAddData(param).get
      }
    }
    "not exists groupId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = SearchMembersParameter.fromMap(Map("groupId" -> UUID.randomUUID.toString))
        GroupService.getMemberAddData(param).get
      }
      thrown.getMessage should be("存在しないグループが指定されました。")
    }
    "exists groupId" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val param = SearchMembersParameter.fromMap(Map("groupId" -> group.getId))
      val result = GroupService.getMemberAddData(param).get
      result.groupId should be(group.getId)
      result.groupName should be("group1")
    }
  }

  "member add" - {
    "no groupId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = AddMemberParameter.fromMap(Map.empty)
        GroupService.applyAddMember(param).get
      }
      thrown.getMessage should be("グループIDの指定がありません。")
    }
    "invalid groupId" in {
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = AddMemberParameter.fromMap(Map("groupId" -> "test", "userName" -> "dummy2", "role" -> "member"))
        GroupService.applyAddMember(param).get
      }
    }
    "not exists groupId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = AddMemberParameter.fromMap(Map("groupId" -> UUID.randomUUID.toString, "userName" -> "dummy2", "role" -> "member"))
        GroupService.applyAddMember(param).get
      }
      thrown.getMessage should be("存在しないグループが指定されました。")
    }
    "no userName" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[ServiceException] thrownBy {
        val param = AddMemberParameter.fromMap(Map("groupId" -> group.getId, "role" -> "member"))
        GroupService.applyAddMember(param).get
      }
      thrown.getMessage should be("ユーザー名の指定がありません。")
    }
    "not exists userName" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[ServiceException] thrownBy {
        val param = AddMemberParameter.fromMap(Map("groupId" -> group.getId, "userName" -> "hoge", "role" -> "member"))
        GroupService.applyAddMember(param).get
      }
      thrown.getMessage should be("存在しないユーザーが指定されました。")
    }
    "disabled user" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      disableUser(userId2)
      val thrown = the[ServiceException] thrownBy {
        val param = AddMemberParameter.fromMap(Map("groupId" -> group.getId, "userName" -> "dummy2", "role" -> "member"))
        GroupService.applyAddMember(param).get
      }
      thrown.getMessage should be("無効なユーザーが指定されました。")
    }
    "yet registered user" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[ServiceException] thrownBy {
        val param = AddMemberParameter.fromMap(Map("groupId" -> group.getId, "userName" -> "dummy1", "role" -> "member"))
        GroupService.applyAddMember(param).get
      }
      thrown.getMessage should be("既に登録のあるユーザーが指定されました。")
    }
    val validRole = Seq(Some("member"), Some("manager"))
    val roleMap = Map(Option("member") -> MemberRole.Member, Option("manager") -> MemberRole.Manager)
    for {
      role <- Seq(None, Some("member"), Some("manager"), Some("deny"), Some("aaa"))
    } {
      s"add ${role}" in {
        val client = SpecCommonLogic.createClient()
        val group = client.createGroup(CreateGroupParam("group1", ""))
        val map = Map("groupId" -> group.getId, "userName" -> "dummy2") ++ role.map { s: String => Map("role" -> s) }.getOrElse(Map.empty)
        val param = AddMemberParameter.fromMap(map)
        if (validRole.contains(role)) {
          GroupService.applyAddMember(param)
          val searchParam = SearchMembersParameter.fromMap(Map("groupId" -> group.getId))
          val result = GroupService.getMemberListData(searchParam).get
          result.members.filter(_.name == "dummy2").length should be(1)
          result.members.filter(_.name == "dummy2").foreach { member =>
            member.role should be(roleMap(role))
          }
        } else {
          val thrown = the[ServiceException] thrownBy {
            GroupService.applyAddMember(param).get
          }
          thrown.getMessage should be("無効なロールが指定されました。")
        }
      }
    }
  }

  "member update show" - {
    "no groupId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = SearchMemberParameter.fromMap(Map.empty)
        GroupService.getMemberUpdateData(param).get
      }
      thrown.getMessage should be("グループIDの指定がありません。")
    }
    "invalid groupId" in {
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = SearchMemberParameter.fromMap(Map("groupId" -> "test", "userId" -> userId1))
        GroupService.getMemberUpdateData(param).get
      }
    }
    "not exists groupId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = SearchMemberParameter.fromMap(Map("groupId" -> UUID.randomUUID.toString, "userId" -> userId1))
        GroupService.getMemberUpdateData(param).get
      }
      thrown.getMessage should be("存在しないグループが指定されました。")
    }
    "no userId" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[ServiceException] thrownBy {
        val param = SearchMemberParameter.fromMap(Map("groupId" -> group.getId))
        GroupService.getMemberUpdateData(param).get
      }
      thrown.getMessage should be("ユーザーIDの指定がありません。")
    }
    "invalid userId" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = SearchMemberParameter.fromMap(Map("groupId" -> group.getId, "userId" -> "test"))
        GroupService.getMemberUpdateData(param).get
      }
    }
    "not exists userId" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[ServiceException] thrownBy {
        val param = SearchMemberParameter.fromMap(Map("groupId" -> group.getId, "userId" -> UUID.randomUUID.toString))
        GroupService.getMemberUpdateData(param).get
      }
      thrown.getMessage should be("存在しないユーザーが指定されました。")
    }
    "disabled user" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      disableUser(userId1)
      val thrown = the[ServiceException] thrownBy {
        val param = SearchMemberParameter.fromMap(Map("groupId" -> group.getId, "userId" -> userId1))
        GroupService.getMemberUpdateData(param).get
      }
      thrown.getMessage should be("無効なユーザーが指定されました。")
    }
    "success" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val param = SearchMemberParameter.fromMap(Map("groupId" -> group.getId, "userId" -> userId1))
      val result = GroupService.getMemberUpdateData(param).get
      result.member.id should be(userId1)
      result.member.name should be("dummy1")
      result.member.role should be(MemberRole.Manager)
    }
  }

  "member update" - {
    "no groupId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateMemberParameter.fromMap(Map.empty)
        GroupService.applyUpdateMember(param).get
      }
      thrown.getMessage should be("グループIDの指定がありません。")
    }
    "invalid groupId" in {
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = UpdateMemberParameter.fromMap(Map("groupId" -> "test", "userId" -> userId1, "role" -> "member"))
        GroupService.applyUpdateMember(param).get
      }
    }
    "not exists groupId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateMemberParameter.fromMap(Map("groupId" -> UUID.randomUUID.toString, "userId" -> userId1, "role" -> "member"))
        GroupService.applyUpdateMember(param).get
      }
      thrown.getMessage should be("存在しないグループが指定されました。")
    }
    "no userId" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateMemberParameter.fromMap(Map("groupId" -> group.getId, "role" -> "member"))
        GroupService.applyUpdateMember(param).get
      }
      thrown.getMessage should be("ユーザーIDの指定がありません。")
    }
    "invalid userId" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = UpdateMemberParameter.fromMap(Map("groupId" -> group.getId, "userId" -> "test", "role" -> "member"))
        GroupService.applyUpdateMember(param).get
      }
    }
    "not exists userId" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateMemberParameter.fromMap(Map("groupId" -> group.getId, "userId" -> UUID.randomUUID.toString, "role" -> "member"))
        GroupService.applyUpdateMember(param).get
      }
      thrown.getMessage should be("存在しないユーザーが指定されました。")
    }
    "disabled user" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      disableUser(userId1)
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateMemberParameter.fromMap(Map("groupId" -> group.getId, "userId" -> userId1, "role" -> "member"))
        GroupService.applyUpdateMember(param).get
      }
      thrown.getMessage should be("無効なユーザーが指定されました。")
    }
    "not registered user" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateMemberParameter.fromMap(Map("groupId" -> group.getId, "userId" -> userId2, "role" -> "member"))
        GroupService.applyUpdateMember(param).get
      }
      thrown.getMessage should be("まだメンバーの登録がありません。")
    }
    val validRole = Seq(Some("member"), Some("manager"))
    val roleMap = Map(Option("member") -> MemberRole.Member, Option("manager") -> MemberRole.Manager)
    for {
      role <- Seq(None, Some("member"), Some("manager"), Some("deny"), Some("aaa"))
    } {
      s"add ${role}" in {
        val client = SpecCommonLogic.createClient()
        val group = client.createGroup(CreateGroupParam("group1", ""))
        val presetRole = if (role == Some("member")) {
          persistence.GroupMemberRole.Manager
        } else {
          persistence.GroupMemberRole.Member
        }
        addMember(client, group.getId, userId2, presetRole)
        val map = Map("groupId" -> group.getId, "userId" -> userId2) ++ role.map { s: String => Map("role" -> s) }.getOrElse(Map.empty)
        val param = UpdateMemberParameter.fromMap(map)
        if (validRole.contains(role)) {
          GroupService.applyUpdateMember(param)
          val searchParam = SearchMembersParameter.fromMap(Map("groupId" -> group.getId))
          val result = GroupService.getMemberListData(searchParam).get
          result.members.filter(_.name == "dummy2").length should be(1)
          result.members.filter(_.name == "dummy2").foreach { member =>
            member.role should be(roleMap(role))
          }
        } else {
          val thrown = the[ServiceException] thrownBy {
            GroupService.applyUpdateMember(param).get
          }
          thrown.getMessage should be("無効なロールが指定されました。")
        }
      }
    }
  }

  "member delete" - {
    "no groupId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateMemberParameter.fromMap(Map.empty)
        GroupService.applyDeleteMember(param).get
      }
      thrown.getMessage should be("グループIDの指定がありません。")
    }
    "invalid groupId" in {
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = UpdateMemberParameter.fromMap(Map("groupId" -> "test", "userId" -> userId1))
        GroupService.applyDeleteMember(param).get
      }
    }
    "not exists groupId" in {
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateMemberParameter.fromMap(Map("groupId" -> UUID.randomUUID.toString, "userId" -> userId1))
        GroupService.applyDeleteMember(param).get
      }
      thrown.getMessage should be("存在しないグループが指定されました。")
    }
    "no userId" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateMemberParameter.fromMap(Map("groupId" -> group.getId))
        GroupService.applyDeleteMember(param).get
      }
      thrown.getMessage should be("ユーザーIDの指定がありません。")
    }
    "invalid userId" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[org.postgresql.util.PSQLException] thrownBy {
        val param = UpdateMemberParameter.fromMap(Map("groupId" -> group.getId, "userId" -> "test"))
        GroupService.applyDeleteMember(param).get
      }
    }
    "not exists userId" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateMemberParameter.fromMap(Map("groupId" -> group.getId, "userId" -> UUID.randomUUID.toString))
        GroupService.applyDeleteMember(param).get
      }
      thrown.getMessage should be("存在しないユーザーが指定されました。")
    }
    "disabled user" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      disableUser(userId1)
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateMemberParameter.fromMap(Map("groupId" -> group.getId, "userId" -> userId1))
        GroupService.applyDeleteMember(param).get
      }
      thrown.getMessage should be("無効なユーザーが指定されました。")
    }
    "not registered user" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateMemberParameter.fromMap(Map("groupId" -> group.getId, "userId" -> userId2))
        GroupService.applyDeleteMember(param).get
      }
      thrown.getMessage should be("まだメンバーの登録がありません。")
    }
    "delete success" in {
      val client = SpecCommonLogic.createClient()
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val param = UpdateMemberParameter.fromMap(Map("groupId" -> group.getId, "userId" -> userId1))
      GroupService.applyDeleteMember(param).get
      val searchParam = SearchMembersParameter.fromMap(Map("groupId" -> group.getId))
      val result = GroupService.getMemberListData(searchParam).get
      result.members.filter(_.name == "dummy1").length should be(0)
    }
  }

  "physical delete" - {
    "no select" in {
      val thrown = the[ServiceException] thrownBy {
        val param = UpdateParameter.fromMap(org.scalatra.util.MultiMap(Map.empty))
        GroupService.applyPhysicalDelete(param).get
      }
      thrown.getMessage should be("グループが選択されていません。")
    }
    "not exists" in {
      val result = GroupService.applyPhysicalDelete(toParam(Seq(UUID.randomUUID.toString)))
      result.isSuccess should be(true)
    }
    def prepareGroup(): GroupIds = {
      import scala.collection.JavaConverters._
      val client = SpecCommonLogic.createClient()
      val png = new java.io.File("./testdata/maintenance/group/test.png")
      val group = client.createGroup(CreateGroupParam("group1", ""))
      val dataset = client.createDataset("dataset1", true, false)
      addAclGroup(client, dataset.getId, group.getId, persistence.GroupAccessLevel.Provider)
      client.addImagesToGroup(group.getId, png)
      getGroupIds(group.getId)
    }
    "success" in {
      val ids = prepareGroup()
      val client = SpecCommonLogic.createClient()
      client.deleteGroup(ids.groupId)
      val result = GroupService.applyPhysicalDelete(toParam(Seq(ids.groupId)))
      result.isSuccess should be(true)
      persistence.Group.find(ids.groupId).isEmpty should be(true)
      ids.memberIds.forall(persistence.Member.find(_).isEmpty) should be(true)
      ids.ownershipIds.forall(persistence.Ownership.find(_).isEmpty) should be(true)
      ids.imageIds.forall(persistence.Image.find(_).isEmpty) should be(true)
      ids.groupImageIds.forall(persistence.GroupImage.find(_).isEmpty) should be(true)
      ids.imageIds.forall { id => !Paths.get(AppConfig.imageDir, "upload", id).toFile.exists } should be(true)
    }
    "shared annotation and image" in {
      val ids = prepareGroup()
      val client = SpecCommonLogic.createClient()
      client.deleteGroup(ids.groupId)
      val group = client.createGroup(CreateGroupParam("group1", ""))
      addImage(group.getId, ids.imageIds)
      val result = GroupService.applyPhysicalDelete(toParam(Seq(ids.groupId)))
      result.isSuccess should be(true)
      persistence.Group.find(ids.groupId).isEmpty should be(true)
      ids.memberIds.forall(persistence.Member.find(_).isEmpty) should be(true)
      ids.ownershipIds.forall(persistence.Ownership.find(_).isEmpty) should be(true)
      ids.imageIds.forall(persistence.Image.find(_).isDefined) should be(true)
      ids.groupImageIds.forall(persistence.GroupImage.find(_).isEmpty) should be(true)
      ids.imageIds.forall { id => Paths.get(AppConfig.imageDir, "upload", id).toFile.exists } should be(true)
    }
    "not deleted" in {
      val ids = prepareGroup()
      val thrown = the[ServiceException] thrownBy {
        GroupService.applyPhysicalDelete(toParam(Seq(ids.groupId))).get
      }
      val messages = Seq("group1, Reason: 論理削除済みではない")
      thrown.details should be(Seq(ErrorDetail("一部のグループが削除できませんでした。", messages)))
      persistence.Group.find(ids.groupId).isDefined should be(true)
      ids.memberIds.forall(persistence.Member.find(_).isDefined) should be(true)
      ids.ownershipIds.forall(persistence.Ownership.find(_).isDefined) should be(true)
      ids.imageIds.forall(persistence.Image.find(_).isDefined) should be(true)
      ids.groupImageIds.forall(persistence.GroupImage.find(_).isDefined) should be(true)
      ids.imageIds.forall { id => Paths.get(AppConfig.imageDir, "upload", id).toFile.exists } should be(true)
    }
    "delete file failed" in {
      val ids = prepareGroup()
      val client = SpecCommonLogic.createClient()
      client.deleteGroup(ids.groupId)
      val deleteFailDir = Paths.get(AppConfig.imageDir, "upload", ids.imageIds(0))
      val deleteFailFiles = deleteFailDir.toFile.list.map(deleteFailDir.resolve)
      deleteFailFiles.foreach { file =>
        file.toFile.setReadOnly
      }
      val thrown = the[ServiceException] thrownBy {
        GroupService.applyPhysicalDelete(toParam(Seq(ids.groupId))).get
      }
      deleteFailFiles.foreach { file =>
        file.toFile.setWritable(true)
      }
      val messages = (deleteFailFiles :+ deleteFailDir)
        .map(path => s"Location: Local, Path:${path.toAbsolutePath.toString}").sorted
      thrown.details.map(_.title) should be(Seq("一部のファイルが削除できませんでした。"))
      thrown.details.flatMap(_.messages).sorted should be(messages)
      persistence.Group.find(ids.groupId).isEmpty should be(true)
      ids.memberIds.forall(persistence.Member.find(_).isEmpty) should be(true)
      ids.ownershipIds.forall(persistence.Ownership.find(_).isEmpty) should be(true)
      ids.imageIds.forall(persistence.Image.find(_).isEmpty) should be(true)
      ids.groupImageIds.forall(persistence.GroupImage.find(_).isEmpty) should be(true)
      ids.imageIds.take(1).forall { id => Paths.get(AppConfig.imageDir, "upload", id).toFile.exists } should be(true)
      ids.imageIds.drop(1).forall { id => !Paths.get(AppConfig.imageDir, "upload", id).toFile.exists } should be(true)
    }
  }

  def addImage(groupId: String, imageIds: Seq[String]): Unit = {
    DB.localTx { implicit s =>
      imageIds.foreach { id =>
        persistence.GroupImage.create(
          id = UUID.randomUUID.toString,
          groupId = groupId,
          imageId = id,
          isPrimary = false,
          createdAt = DateTime.now,
          createdBy = AppConfig.systemUserId,
          updatedAt = DateTime.now,
          updatedBy = AppConfig.systemUserId
        )
      }
    }
  }

  def addAclGroup(client: DsmoqClient, datasetId: String, groupId: String, accessLevel: Int): Unit = {
    import scala.collection.JavaConverters._
    val acls = Seq(
      new SetAccessLevelParam(groupId, persistence.OwnerType.Group, accessLevel)
    )
    client.changeAccessLevel(datasetId, acls.asJava)
  }

  case class GroupIds(
    groupId: String, memberIds: Seq[String], ownershipIds: Seq[String],
    imageIds: Seq[String], groupImageIds: Seq[String]
  )

  def getGroupIds(groupId: String): GroupIds = {
    DB.readOnly { implicit s =>
      val o = persistence.Ownership.o
      val ownershipIds = withSQL {
        select(o.result.id)
          .from(persistence.Ownership as o)
          .where
          .eqUuid(o.groupId, groupId)
      }.map(_.string(o.resultName.id)).list.apply()
      val m = persistence.Member.m
      val memberIds = withSQL {
        select(m.result.id)
          .from(persistence.Member as m)
          .where
          .eqUuid(m.groupId, groupId)
      }.map(_.string(m.resultName.id)).list.apply()
      val gi = persistence.GroupImage.gi
      val groupImages = withSQL {
        select
          .from(persistence.GroupImage as gi)
          .where
          .eqUuid(gi.groupId, groupId)
      }.map(persistence.GroupImage(gi.resultName)).list.apply()
      val imageIds = groupImages.map(_.imageId).filter(!AppConfig.defaultImageIds.contains(_)).distinct
      val groupImageIds = groupImages.map(_.id)
      GroupIds(
        groupId = groupId,
        memberIds = memberIds,
        ownershipIds = ownershipIds,
        imageIds = imageIds,
        groupImageIds = groupImageIds
      )
    }
  }

  def addMember(client: DsmoqClient, groupId: String, userId: String, role: Int): Unit = {
    import scala.collection.JavaConverters._
    val members = Seq(
      new AddMemberParam(userId, role)
    )
    client.addMember(groupId, members.asJava)
  }

  def disableUser(userId: String): Unit = {
    val map = org.scalatra.util.MultiMap(Map(
      "disabled.originals" -> Seq.empty,
      "disabled.updates" -> Seq(userId)
    ))
    val param = data.user.UpdateParameter.fromMap(map)
    UserService.updateDisabled(param)
  }

  def createGroup(client: DsmoqClient, name: String, delete: Boolean): String = {
    val group = client.createGroup(CreateGroupParam(name, ""))
    if (delete) {
      client.deleteGroup(group.getId)
    }
    group.getId
  }
  def resetUpdatedAt(groups: Seq[String]): Unit = {
    DB.localTx { implicit s =>
      val g = persistence.Group.column
      withSQL {
        update(persistence.Group)
          .set(
            g.updatedAt -> defaultUpdatedAt
          )
          .where
          .in(g.id, groups.map(sqls.uuid))
      }.update.apply()
    }
  }
  def getGroups(groupIds: Seq[String]): Seq[persistence.Group] = {
    val g = persistence.Group.g
    DB.readOnly { implicit s =>
      withSQL {
        select
          .from(persistence.Group as g)
          .where
          .inUuid(g.id, groupIds)
          .orderBy(g.createdAt)
      }.map(persistence.Group(g.resultName)).list.apply()
    }
  }
  def toParam(targets: Seq[String]): UpdateParameter = {
    val map = org.scalatra.util.MultiMap(Map("checked" -> targets))
    UpdateParameter.fromMap(map)
  }
}
