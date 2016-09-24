package dsmoq.maintenance.services

import java.util.UUID

import org.joda.time.DateTime

import org.scalatest.BeforeAndAfter
import org.scalatest.FreeSpec
import org.scalatest.Matchers._

import scalikejdbc.config.DBsWithEnv

import dsmoq.maintenance.data.user
import dsmoq.maintenance.data.apikey.AddParameter
import dsmoq.maintenance.data.apikey.DisableParameter

import dsmoq.maintenance.data.apikey.SearchResultApiKey

class ApiKeyServiceSpec extends FreeSpec with BeforeAndAfter {
  DBsWithEnv("test").setup()
  SpecCommonLogic.deleteAllCreateData()

  before {
    SpecCommonLogic.deleteAllCreateData()
    SpecCommonLogic.insertDummyData()
  }

  after {
    SpecCommonLogic.deleteAllCreateData()
  }

  "order by" - {
    "create datetime" in {
      SpecCommonLogic.deleteAllCreateData()
      for (i <- 1 to 5) {
        val dt = new DateTime(2016, 9, 13, 0, 0, i)
        SpecCommonLogic.insertUser(SpecCommonLogic.UserDetail(name = s"test${i}", ts = dt))
      }
      val raw = ApiKeyService.list()
      val sorted = raw.sortWith((x1, x2) => x1.createdAt.isBefore(x2.createdAt))
      raw should be(sorted)
    }
  }

  "create for" - {
    "invalid name" in {
      val orgs = ApiKeyService.list()
      val thrown = the[ServiceException] thrownBy {
        val param = AddParameter.fromMap(Map("name" -> "hoge"))
        ApiKeyService.add(param).get
      }
      thrown.getMessage should be("無効なユーザーが指定されました。")
      ApiKeyService.list() should be(orgs)
    }
    "disabled user" in {
      UserService.updateDisabled(user.UpdateParameter(Seq.empty, Seq("023bfa40-e897-4dad-96db-9fd3cf001e79")))
      val orgs = ApiKeyService.list()
      val thrown = the[ServiceException] thrownBy {
        val param = AddParameter.fromMap(Map("name" -> "dummy"))
        ApiKeyService.add(param).get
      }
      thrown.getMessage should be("無効なユーザーが指定されました。")
      ApiKeyService.list() should be(orgs)
    }
    "valid user with" - {
      for {
        n <- 0 to 2
      } {
        s"${n} key" in {
          ApiKeyService.disable(DisableParameter.fromMap(Map("id" -> "0cebc943-a0b9-4aa5-927d-65fa374bf0ec")))
          ApiKeyService.disable(DisableParameter.fromMap(Map("id" -> "0cebc943-a0b9-4aa5-927d-65fa374bf0ed")))
          ApiKeyService.list().size should be(0)
          val addParam = AddParameter.fromMap(Map("name" -> "dummy1"))
          for {
            _ <- 1 to n
          } {
            ApiKeyService.add(addParam).get
          }
          val orgs = ApiKeyService.list()
          orgs.size should be(n)
          ApiKeyService.add(addParam).get
          ApiKeyService.list().size should be(orgs.size + 1)
        }
      }
    }
  }
  "disable to" - {
    "none key" in {
      ApiKeyService.list().size should be(2)
      val thrown = the[ServiceException] thrownBy {
        val param = DisableParameter.fromMap(Map.empty)
        ApiKeyService.disable(param).get
      }
      thrown.getMessage should be("キーが未選択です。")
      ApiKeyService.list().size should be(2)
    }
    "invalid key id" in {
      ApiKeyService.list().size should be(2)
      val param = DisableParameter.fromMap(Map("id" -> UUID.randomUUID.toString))
      ApiKeyService.disable(param).get
      ApiKeyService.list().size should be(2)
    }
    "disabled key" in {
      ApiKeyService.list().size should be(2)
      val id = ApiKeyService.add(AddParameter.fromMap(Map("name" -> "dummy1"))).get
      ApiKeyService.list().size should be(3)
      val param = DisableParameter.fromMap(Map("id" -> id))
      ApiKeyService.disable(param).get
      ApiKeyService.list().size should be(2)
      ApiKeyService.disable(param).get
      ApiKeyService.list().size should be(2)
    }
    "disabled user's" in {
      ApiKeyService.list().size should be(2)
      val id = ApiKeyService.add(AddParameter.fromMap(Map("name" -> "dummy1"))).get
      ApiKeyService.list().size should be(3)
      UserService.updateDisabled(user.UpdateParameter(Seq.empty, Seq("023bfa40-e897-4dad-96db-9fd3cf001e79")))
      ApiKeyService.list().size should be(1)
      val param = DisableParameter.fromMap(Map("id" -> id))
      ApiKeyService.disable(param).get
      ApiKeyService.list().size should be(1)
      UserService.updateDisabled(user.UpdateParameter(Seq("023bfa40-e897-4dad-96db-9fd3cf001e79"), Seq.empty))
      ApiKeyService.list().size should be(2)
    }
    "disabled user's disabled key" in {
      ApiKeyService.list().size should be(2)
      val id = ApiKeyService.add(AddParameter.fromMap(Map("name" -> "dummy1"))).get
      ApiKeyService.list().size should be(3)
      val param = DisableParameter.fromMap(Map("id" -> id))
      ApiKeyService.disable(param).get
      ApiKeyService.list().size should be(2)
      UserService.updateDisabled(user.UpdateParameter(Seq.empty, Seq("023bfa40-e897-4dad-96db-9fd3cf001e79")))
      ApiKeyService.list().size should be(1)
      ApiKeyService.disable(param).get
      ApiKeyService.list().size should be(1)
      UserService.updateDisabled(user.UpdateParameter(Seq("023bfa40-e897-4dad-96db-9fd3cf001e79"), Seq.empty))
      ApiKeyService.list().size should be(2)
    }
    "valid key" in {
      ApiKeyService.list().size should be(2)
      val param = DisableParameter.fromMap(Map("id" -> "0cebc943-a0b9-4aa5-927d-65fa374bf0ec"))
      ApiKeyService.disable(param)
      ApiKeyService.list().size should be(1)
    }
  }
}
