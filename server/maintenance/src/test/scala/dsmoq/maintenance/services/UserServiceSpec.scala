package dsmoq.maintenance.services

import java.util.UUID
import org.joda.time.DateTime

import org.scalatest.BeforeAndAfter
import org.scalatest.FreeSpec
import org.scalatest.Matchers._

import scalikejdbc.config.DBsWithEnv

import dsmoq.persistence
import dsmoq.maintenance.data.user.SearchCondition
import dsmoq.maintenance.services.SpecCommonLogic.UserDetail

class UserServiceSpec extends FreeSpec with BeforeAndAfter {
  DBsWithEnv("test").setup()
  SpecCommonLogic.deleteAllCreateData()

  before {
    SpecCommonLogic.insertDummyData()
  }

  after {
    SpecCommonLogic.deleteAllCreateData()
  }

  "search by" - {
    def prepareUsers(): Seq[persistence.User] = {
      SpecCommonLogic.deleteAllCreateData()
      val list1 = Seq(
        SpecCommonLogic.insertUser(UserDetail(
          name = "hoge1",
          ts = new DateTime(2016, 9, 12, 0, 0, 0),
          fullname = Some("fuga piyo"),
          organization = Some("denkiyagi 1"),
          disabled = false
        )),
        SpecCommonLogic.insertUser(UserDetail(
          name = "hoge2",
          ts = new DateTime(2016, 9, 12, 0, 0, 1),
          fullname = Some("piyo fuga"),
          organization = Some("denkiyagi 2"),
          disabled = false
        )),
        SpecCommonLogic.insertUser(UserDetail(
          name = "hoge3",
          ts = new DateTime(2016, 9, 12, 0, 0, 2),
          fullname = Some("fuga piyo"),
          organization = Some("denkiyagi 1"),
          disabled = true
        )),
        SpecCommonLogic.insertUser(UserDetail(
          name = "hoge4",
          ts = new DateTime(2016, 9, 12, 0, 0, 3),
          fullname = Some("piyo fuga"),
          organization = Some("denkiyagi 2"),
          disabled = true
        ))
      )
      val list2 = for (i <- 1 to 3) yield {
        val dt = new DateTime(2016, 9, 13, 0, 0, i)
        SpecCommonLogic.insertUser(UserDetail(name = s"test${i}", ts = dt, disabled = false))
      }
      val list3 = for (i <- 4 to 6) yield {
        val dt = new DateTime(2016, 9, 13, 0, 0, i)
        SpecCommonLogic.insertUser(UserDetail(name = s"test${i}", ts = dt, disabled = true))
      }
      list1 ++ list2 ++ list3
    }
    "userType=None, query=None, page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map.empty)
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=None, query=Some(not macth, not match, not match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("query" -> "aaaaaaa"))
      val results = UserService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "userType=None, query=Some(macth, not match, not match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("query" -> "hoge"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).filter(_.name.contains("hoge")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=None, query=Some(not macth, match, not match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("query" -> "fuga"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).filter(_.fullname.contains("fuga")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=None, query=Some(not macth, not match, match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("query" -> "denkiyagi"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).filter(_.organization.contains("denkiyagi")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=Some(all), query=None, page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "all"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=Some(all), query=Some(not macth, not match, not match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "all", "query" -> "aaaaaaa"))
      val results = UserService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "userType=Some(all), query=Some(macth, not match, not match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "all", "query" -> "hoge"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).filter(_.name.contains("hoge")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=Some(all), query=Some(not macth, match, not match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "all", "query" -> "fuga"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).filter(_.fullname.contains("fuga")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=Some(all), query=Some(not macth, not match, match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "all", "query" -> "denkiyagi"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).filter(_.organization.contains("denkiyagi")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=Some(enabled), query=None, page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "enabled"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).filter(!_.disabled).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=Some(enabled), query=Some(not macth, not match, not match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "enabled", "query" -> "aaaaaaa"))
      val results = UserService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "userType=Some(enabled), query=Some(macth, not match, not match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "enabled", "query" -> "hoge"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).filter(!_.disabled).filter(_.name.contains("hoge")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=Some(enabled), query=Some(not macth, match, not match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "enabled", "query" -> "fuga"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).filter(!_.disabled).filter(_.fullname.contains("fuga")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=Some(enabled), query=Some(not macth, not match, match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "enabled", "query" -> "denkiyagi"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).filter(!_.disabled).filter(_.organization.contains("denkiyagi")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=Some(disabled), query=None, page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "disabled"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).filter(_.disabled).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=Some(disabled), query=Some(not macth, not match, not match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "disabled", "query" -> "aaaaaaa"))
      val results = UserService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "userType=Some(disabled), query=Some(macth, not match, not match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "disabled", "query" -> "hoge"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).filter(_.disabled).filter(_.name.contains("hoge")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=Some(disabled), query=Some(not macth, match, not match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "disabled", "query" -> "fuga"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).filter(_.disabled).filter(_.fullname.contains("fuga")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=Some(disabled), query=Some(not macth, not match, match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "disabled", "query" -> "denkiyagi"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).filter(_.disabled).filter(_.organization.contains("denkiyagi")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=Some(aaaaa), query=None, page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "aaaaa"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=Some(aaaaa), query=Some(not macth, not match, not match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "aaaaa", "query" -> "aaaaaaa"))
      val results = UserService.search(condition)
      results.data should be(Seq.empty)
      results.total should be(0)
    }
    "userType=Some(aaaaa), query=Some(macth, not match, not match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "aaaaa", "query" -> "hoge"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).filter(_.name.contains("hoge")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=Some(aaaaa), query=Some(not macth, match, not match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "aaaaa", "query" -> "fuga"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).filter(_.fullname.contains("fuga")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=Some(aaaaa), query=Some(not macth, not match, match), page=None" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "aaaaa", "query" -> "denkiyagi"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).filter(_.organization.contains("denkiyagi")).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
    }
    "userType=None, query=None, page=Some(-1)" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("page" -> "-1"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "userType=None, query=None, page=Some(0)" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("page" -> "0"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "userType=None, query=None, page=Some(1)" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("page" -> "1"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "userType=None, query=None, page=Some(2)" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("page" -> "2"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).map(_.id).drop(5).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(6)
      results.to should be(10)
    }
    "userType=None, query=None, page=Some(3)" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("page" -> "3"))
      val results = UserService.search(condition)
      val actuals = results.data.map(_.id)
      actuals should be(Seq.empty)
      results.total should be(10)
    }
    "userType=None, query=None, page=Some(a)" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("page" -> "a"))
      val results = UserService.search(condition)
      val expects = users.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt)).map(_.id).take(5)
      val actuals = results.data.map(_.id)
      actuals should be(expects)
      results.total should be(10)
      results.from should be(1)
      results.to should be(5)
    }
    "userType=Some(enabled), query=None, page=Some(2)" in {
      val users = prepareUsers()
      val condition = SearchCondition.fromMap(Map("userType" -> "enabled", "page" -> "2"))
      val results = UserService.search(condition)
      val actuals = results.data.map(_.id)
      actuals should be(Seq.empty)
      results.total should be(5)
    }
  }

  "update to" - {
    val user1Id = "023bfa40-e897-4dad-96db-9fd3cf001e79"
    "no update" in {
      val now = DateTime.now
      UserService.updateDisabled(Seq.empty, Seq.empty)
      val condition = SearchCondition.fromMap(Map.empty)
      val users = UserService.search(condition)
      users.data.filter(u => u.updatedAt.isAfter(now)).size should be(0)
    }
    for {
      (originals, updates) <- Seq((Seq("hoge"), Seq.empty), (Seq.empty, Seq("hoge")), (Seq(user1Id, "hoge"), Seq.empty), (Seq.empty, Seq(user1Id, "hoge")))
    } {
      s"invalid uuid ${originals.size}x${updates.size}" in {
        val now = DateTime.now
        val thrown = the[ServiceException] thrownBy {
          UserService.updateDisabled(originals, updates).get
        }
        thrown.getMessage should be("指定したIDの形式が不正です。")
        val condition = SearchCondition.fromMap(Map.empty)
        val users = UserService.search(condition)
        users.data.filter(u => u.updatedAt.isAfter(now)).size should be(0)
      }
    }
    val notExistsId = UUID.randomUUID.toString
    for {
      (originals, updates) <- Seq((Seq(notExistsId), Seq.empty), (Seq.empty, Seq(notExistsId)), (Seq(user1Id, notExistsId), Seq.empty), (Seq.empty, Seq(user1Id, notExistsId)))
    } {
      s"not exists userid ${originals.size}x${updates.size}" in {
        val now = DateTime.now
        val thrown = the[ServiceException] thrownBy {
          UserService.updateDisabled(originals, updates).get
        }
        thrown.getMessage should be("存在しないユーザーが指定されました。")
        val condition = SearchCondition.fromMap(Map.empty)
        val users = UserService.search(condition)
        users.data.filter(u => u.updatedAt.isAfter(now)).size should be(0)
      }
    }
    "disable(1 user)" in {
      SpecCommonLogic.deleteAllCreateData()
      val user = SpecCommonLogic.insertUser(UserDetail(name = s"test1", ts = DateTime.now, disabled = false))
      UserService.updateDisabled(Seq.empty, Seq(user.id))
      val condition = SearchCondition.fromMap(Map.empty)
      val users = UserService.search(condition)
      users.data.filter(_.id == user.id).map(_.disabled).headOption should be(Some(true))
    }
    "enable(1 user)" in {
      SpecCommonLogic.deleteAllCreateData()
      val user = SpecCommonLogic.insertUser(UserDetail(name = s"test1", ts = DateTime.now, disabled = true))
      UserService.updateDisabled(Seq(user.id), Seq.empty)
      val condition = SearchCondition.fromMap(Map.empty)
      val users = UserService.search(condition)
      users.data.filter(_.id == user.id).map(_.disabled).headOption should be(Some(false))
    }
    "no update 2" in {
      SpecCommonLogic.deleteAllCreateData()
      val now = DateTime.now
      val user = SpecCommonLogic.insertUser(UserDetail(name = s"test1", ts = DateTime.now, disabled = false))
      UserService.updateDisabled(Seq(user.id), Seq(user.id))
      val condition = SearchCondition.fromMap(Map.empty)
      val users = UserService.search(condition)
      users.data.filter(u => u.updatedAt.isAfter(now)).size should be(0)
    }
    "enable(1 user) and disable(1 user)" in {
      SpecCommonLogic.deleteAllCreateData()
      val user1 = SpecCommonLogic.insertUser(UserDetail(name = s"test1", ts = DateTime.now, disabled = true))
      val user2 = SpecCommonLogic.insertUser(UserDetail(name = s"test2", ts = DateTime.now, disabled = false))
      UserService.updateDisabled(Seq(user1.id), Seq(user2.id))
      val condition = SearchCondition.fromMap(Map.empty)
      val users = UserService.search(condition)
      users.data.filter(_.id == user1.id).map(_.disabled).headOption should be(Some(false))
      users.data.filter(_.id == user2.id).map(_.disabled).headOption should be(Some(true))
    }
    "enable(2 user)" in {
      SpecCommonLogic.deleteAllCreateData()
      val user1 = SpecCommonLogic.insertUser(UserDetail(name = s"test1", ts = DateTime.now, disabled = true))
      val user2 = SpecCommonLogic.insertUser(UserDetail(name = s"test2", ts = DateTime.now, disabled = true))
      UserService.updateDisabled(Seq(user1.id, user2.id), Seq.empty)
      val condition = SearchCondition.fromMap(Map.empty)
      val users = UserService.search(condition)
      users.data.filter(_.id == user1.id).map(_.disabled).headOption should be(Some(false))
      users.data.filter(_.id == user2.id).map(_.disabled).headOption should be(Some(false))
    }
    "disable(2 user)" in {
      SpecCommonLogic.deleteAllCreateData()
      val user1 = SpecCommonLogic.insertUser(UserDetail(name = s"test1", ts = DateTime.now, disabled = false))
      val user2 = SpecCommonLogic.insertUser(UserDetail(name = s"test2", ts = DateTime.now, disabled = false))
      UserService.updateDisabled(Seq.empty, Seq(user1.id, user2.id))
      val condition = SearchCondition.fromMap(Map.empty)
      val users = UserService.search(condition)
      users.data.filter(_.id == user1.id).map(_.disabled).headOption should be(Some(true))
      users.data.filter(_.id == user2.id).map(_.disabled).headOption should be(Some(true))
    }
  }
}
