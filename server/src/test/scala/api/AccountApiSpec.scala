package api

import api.logic.SpecCommonLogic
import org.scalatest.{BeforeAndAfter, FreeSpec}
import org.scalatra.test.scalatest.ScalatraSuite
import org.json4s.{DefaultFormats, Formats}
import scalikejdbc.config.DBs
import dsmoq.controllers.{AjaxResponse, ApiController}
import org.json4s.jackson.JsonMethods._
import dsmoq.services.data.{MailValidationResult, License, User}
import java.io.File
import org.scalatra.servlet.MultipartConfig
import dsmoq.persistence.SuggestType
import dsmoq.services.data.GroupData.Group
import java.util.UUID
import dsmoq.AppConf
import dsmoq.services.data.DatasetData.Dataset

class AccountApiSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormats: Formats = DefaultFormats

  private val dummyFile = new File("README.md")
  private val dummyImage = new File("../client/www/dummy/images/nagoya.jpg")

  // multi-part file upload config
  val holder = addServlet(classOf[ApiController], "/api/*")
  holder.getRegistration.setMultipartConfig(
    MultipartConfig(
      maxFileSize = Some(3 * 1024 * 1024),
      fileSizeThreshold = Some(1 * 1024 * 1024)
    ).toMultipartConfigElement
  )

  before {
    DBs.setup()

    // FIXME
    System.setProperty(org.scalatra.EnvironmentKey, "development")
    SpecCommonLogic.insertDummyData()
  }

  after {
    SpecCommonLogic.deleteAllCreateData()
    DBs.close()
  }

  "API test" - {
    "プロフィール" - {
      "サインイン前はゲストユーザーか" in {
        get("/api/profile") {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[User]]
          assert(result.data.isGuest)
        }
      }
      "サインイン後はCOIユーザーか" in {
        session {
          signIn()
          get("/api/profile") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[User]]
            assert(!result.data.isGuest)
          }
        }
      }
      "サインイン→サインアウト後はゲストユーザーか" in {
        session {
          signIn()
          post("/api/signout") { checkStatus() }
          get("/api/profile") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[User]]
            assert(result.data.isGuest)
          }
        }
      }

      "基本情報が更新できるか" in {
        session {
          signIn()
          val params = Map(
            "name" -> "dummy1",
            "fullname" -> "フルネーム",
            "organization" -> "テスト所属",
            "title" -> "テストタイトル",
            "description" -> "テスト詳細"
          )
          post("/api/profile", params) { checkStatus() }
          get("/api/profile") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[User]]
            result.data.fullname should be("フルネーム")
          }
        }
      }

      "画像情報付きで基本情報が更新できるか" in {
        session {
          signIn()
          val oldImageUrl = get("/api/profile") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[User]]
            result.data.image
          }

          val params = Map(
            "name" -> "dummy1",
            "fullname" -> "fullname 2",
            "organization" -> "organization 2",
            "title" -> "title 2",
            "description" -> "description 2"
          )
          val file = Map("image" -> dummyImage)
          post("/api/profile", params, file) { checkStatus() }
          get("/api/profile") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[User]]
            result.data.fullname should be("fullname 2")
            result.data.image should not be(oldImageUrl)
          }
        }
      }

      "メールアドレスが更新できるか" in {
        session {
          signIn()
          val params = Map("email" -> "hogehoge@test.com")
          post("/api/profile/email_change_requests", params) { checkStatus() }
          get("/api/profile") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[User]]
            result.data.mailAddress should be("hogehoge@test.com")
          }
        }
      }

      "パスワードが変更後、変更したパスワードでサインインできるか" in {
        session {
          signIn()
          val params = Map(
            "current_password" -> "password",
            "new_password" -> "new_password"
          )
          put("/api/profile/password", params) { checkStatus() }
          post("/api/signout") { checkStatus() }
          val signinParams = Map("id" -> "dummy1", "password" -> "new_password")
          post("/api/signin", signinParams) { checkStatus() }

          //　戻す
          val rollbackParams = Map(
            "current_password" -> "new_password",
            "new_password" -> "password"
          )
          put("/api/profile/password", rollbackParams) { checkStatus() }
          post("/api/signout") { checkStatus() }
          signIn()
        }
      }

      "メールアドレスが重複していないか" in {
        val params = Map("value" -> "hogehoge@hoge.jp")
        get("/api/system/is_valid_email", params) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[MailValidationResult]]
          assert(result.data.isValid)
        }
      }

      "ライセンス一覧を取得できるか" in {
        get("/api/licenses") {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[Seq[License]]]
          assert(result.data.size > 0)
        }
      }

      "アカウント一覧を取得できるか" in {
        get("/api/accounts") {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[Seq[User]]]
          assert(result.data.size > 0)
        }
      }

      "ユーザー/グループの候補一覧を取得できるか" in {
        session {
          signIn()
          val groupName = "groupName" + UUID.randomUUID()
          val params = Map("name" -> groupName, "description" -> "description")
          post("/api/groups", params) {
            checkStatus()
            parse(body).extract[AjaxResponse[Group]].data.id
          }
        }

        val regex = "\\Adummy1.*\\z".r
        val query = Map("query" -> "dummy1")
        get("/api/suggests/users_and_groups", query) {
          checkStatus()
          // データパースしてチェック
          val valueMap = (parse(body) \ "data").values.asInstanceOf[List[Map[String, Any]]]
          valueMap.foreach {x =>
            val t = x("dataType")
            t match {
              case SuggestType.User =>
                x("name") match {
                  case regex() => // OK
                  case _ => x("fullname") match {
                    case regex() => // OK
                    case _ => fail()
                  }
                }
              case SuggestType.Group =>
                x("name") match {
                  case regex() => // OK
                  case _ => fail()
                }
              case _ => fail()
            }
          }
        }
      }

      "属性候補一覧を取得できるか" in {
        // データセットを作成し、attributesを設定(作成)しておく
        val attributeName = UUID.randomUUID().toString
        session {
          signIn()
          val files = Map("file[]" -> dummyFile)
          val datasetId = post("/api/datasets", Map.empty, files) {
            checkStatus()
            parse(body).extract[AjaxResponse[Dataset]].data.id
          }

          val params = List(
            "name" -> "変更後データセット",
            "description" -> "change description",
            "license" -> AppConf.defaultLicenseId,
            "attributes[][name]" -> attributeName,
            "attributes[][value]" -> "attr_value"
          )
          put("/api/datasets/" + datasetId + "/metadata", params) { checkStatus() }
        }

        // 属性候補から作成したattributesが取得できるか
        val query = Map("query" -> attributeName)
        get("/api/suggests/attributes", query) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[Seq[String]]]
          assert(result.data.contains(attributeName))
        }
      }
    }
  }
  
  def signIn() {
    val params = Map("id" -> "dummy1", "password" -> "password")
    post("/api/signin", params) {
      checkStatus()
    }
  }

  def checkStatus() {
    status should be(200)
    val result = parse(body).extract[AjaxResponse[Any]]
    result.status should be("OK")
  }
}
