package dsmoq.maintenance.services

import java.util.UUID

import org.scalatest.BeforeAndAfter
import org.scalatest.FreeSpec
import org.scalatest.Matchers._

import scalikejdbc.config.DBsWithEnv

class ApiKeyServiceSpec extends FreeSpec with BeforeAndAfter {
  DBsWithEnv("test").setup()

  before {
    SpecCommonLogic.deleteAllCreateData()
    SpecCommonLogic.insertDummyData()
  }

  "create for" - {
    "invalid name" in {
      val orgs = ApiKeyService.list()
      val thrown = the[ServiceException] thrownBy {
        ApiKeyService.add(Some("hoge")).get
      }
      thrown.getMessage should be("無効なユーザーが指定されました。")
      ApiKeyService.list() should be(orgs)
    }
    "disabled user" in {
      UserService.updateDisabled(Seq.empty, Seq("023bfa40-e897-4dad-96db-9fd3cf001e79"))
      val orgs = ApiKeyService.list()
      val thrown = the[ServiceException] thrownBy {
        ApiKeyService.add(Some("dummy1")).get
      }
      thrown.getMessage should be("無効なユーザーが指定されました。")
      ApiKeyService.list() should be(orgs)
    }
    "valid user with" - {
      for {
        n <- 0 to 2
      } {
        s"${n} api key" in {
          ApiKeyService.disable(Some("0cebc943-a0b9-4aa5-927d-65fa374bf0ec"))
          ApiKeyService.list().size should be(0)
          for {
            _ <- 1 to n
          } {
            ApiKeyService.add(Some("dummy1")).get
          }
          val orgs = ApiKeyService.list()
          orgs.size should be(n)
          ApiKeyService.add(Some("dummy1")).get
          ApiKeyService.list().size should be(orgs.size + 1)
        }
      }
    }
  }
  "disable to" - {
    "invalid api key id" in {
      ApiKeyService.list().size should be(1)
      val thrown = the[ServiceException] thrownBy {
        ApiKeyService.disable(Some(UUID.randomUUID.toString)).get
      }
      thrown.getMessage should be("無効なAPIキーが指定されました。")
      ApiKeyService.list().size should be(1)
    }
    "disabled api key" in {
      ApiKeyService.list().size should be(1)
      val id = ApiKeyService.add(Some("dummy1")).get
      ApiKeyService.list().size should be(2)
      ApiKeyService.disable(Some(id)).get
      ApiKeyService.list().size should be(1)
      val thrown = the[ServiceException] thrownBy {
        ApiKeyService.disable(Some(id)).get
      }
      thrown.getMessage should be("無効なAPIキーが指定されました。")
      ApiKeyService.list().size should be(1)
    }
    "disabled user's" in {
      ApiKeyService.list().size should be(1)
      val id = ApiKeyService.add(Some("dummy1")).get
      ApiKeyService.list().size should be(2)
      UserService.updateDisabled(Seq.empty, Seq("023bfa40-e897-4dad-96db-9fd3cf001e79"))
      val thrown = the[ServiceException] thrownBy {
        ApiKeyService.disable(Some(id)).get
      }
      thrown.getMessage should be("無効なAPIキーが指定されました。")
      ApiKeyService.list().size should be(0)
      UserService.updateDisabled(Seq("023bfa40-e897-4dad-96db-9fd3cf001e79"), Seq.empty)
      ApiKeyService.list().size should be(2)
    }
    "valid api key" in {
      ApiKeyService.list().size should be(1)
      ApiKeyService.disable(Some("0cebc943-a0b9-4aa5-927d-65fa374bf0ec"))
      ApiKeyService.list().size should be(0)
    }
  }
}
