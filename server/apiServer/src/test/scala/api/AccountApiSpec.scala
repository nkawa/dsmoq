package api

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import api.logic.SpecCommonLogic
import dsmoq.services.User
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfter, FreeSpec}
import org.scalatra.test.scalatest.ScalatraSuite
import org.json4s.{DefaultFormats, Formats}
import scalikejdbc.config.{DBsWithEnv, DBs}
import dsmoq.controllers.{AjaxResponse, ApiController}
import org.json4s.jackson.JsonMethods._
import dsmoq.services.json.{MailValidationResult, License}
import java.io.File
import org.scalatra.servlet.MultipartConfig
import dsmoq.persistence.SuggestType
import dsmoq.services.json.GroupData.Group
import java.util.{Base64, UUID}
import dsmoq.AppConf
import dsmoq.services.json.DatasetData.Dataset
import org.json4s._
import org.json4s.JsonDSL._
import org.eclipse.jetty.server.Connector

class AccountApiSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormats: Formats = DefaultFormats

  private val dummyFile = new File("../README.md")
  private val dummyImage = new File("../../client/www/dummy/images/nagoya.jpg")

  // multi-part file upload config
  val holder = addServlet(classOf[ApiController], "/api/*")
  holder.getRegistration.setMultipartConfig(
    MultipartConfig(
      maxFileSize = Some(3 * 1024 * 1024),
      fileSizeThreshold = Some(1 * 1024 * 1024)
    ).toMultipartConfigElement
  )

  override def beforeAll() {
    super.beforeAll()
    DBsWithEnv("test").setup()
    System.setProperty(org.scalatra.EnvironmentKey, "test")
  }

  override def afterAll() {
    DBsWithEnv("test").close()
    super.afterAll()
  }

  before {
    SpecCommonLogic.insertDummyData()
  }

  after {
    SpecCommonLogic.deleteAllCreateData()
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
          val params = Map("d" ->
              compact(render(
                ("name" -> "dummy1") ~
                ("fullname" -> "フルネーム") ~
                ("organization" -> "テスト所属") ~
                ("title" -> "テストタイトル") ~
                ("description" -> "テスト詳細")
              ))
          )
          put("/api/profile", params) { checkStatus() }
          get("/api/profile") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[User]]
            result.data.name should be("dummy1")
            result.data.fullname should be("フルネーム")
            result.data.organization should be("テスト所属")
            result.data.title should be("テストタイトル")
            result.data.description should be("テスト詳細")
          }
        }
      }

      "画像情報が更新できるか" in {
        session {
          signIn()
          val oldImageUrl = get("/api/profile") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[User]]
            result.data.image
          }

          val file = Map("icon" -> dummyImage)
          post("/api/profile/image", Map(), file) { checkStatus() }
          get("/api/profile") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[User]]
            result.data.image should not be(oldImageUrl)
          }
        }
      }

      "メールアドレスが更新できるか" in {
        session {
          signIn()
          val params = Map("d" -> compact(render(("email" -> "hogehoge@test.com"))))
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
          val params = Map("d" ->
            compact(render(
              ("currentPassword" -> "password") ~
              ("newPassword" -> "new_password")
            ))
          )
          put("/api/profile/password", params) { checkStatus() }
          post("/api/signout") { checkStatus() }
          val signinParams = Map("d" -> compact(render(("id" -> "dummy1") ~ ("password" -> "new_password"))))
          post("/api/signin", signinParams) { checkStatus() }

          //　戻す
          val rollbackParams = Map("d" ->
            compact(render(
              ("currentPassword" -> "new_password") ~
              ("newPassword" -> "password")
            ))
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
          //TODO 実装されたら書く
//          val result = parse(body).extract[AjaxResponse[MailValidationResult]]
//          assert(result.data.isValid)
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

      "ユーザーの候補一覧を取得できるか" in {
        val userName = "dummy1"
        val regex = ("\\A" + userName + ".*\\z").r
        val params = Map("d" -> compact(render(("query" -> userName))))
        get("/api/suggests/users_and_groups", params) {
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

      "グループの候補一覧を取得できるか" in {
        val groupName = "groupName" + UUID.randomUUID()
        session {
          signIn()
          val params = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "description"))))
          post("/api/groups", params) {
            checkStatus()
            parse(body).extract[AjaxResponse[Group]].data.id
          }
        }

        val regex = ("\\A" + groupName + ".*\\z").r
        val query = Map("d" -> compact(render(("query" -> groupName))))
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
          val datasetId = post("/api/datasets", Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1"), files) {
            checkStatus()
            parse(body).extract[AjaxResponse[Dataset]].data.id
          }

          val params = Map("d" ->
            compact(render(
              ("name" -> "変更後データセット") ~
              ("description" -> "change description") ~
              ("license" -> AppConf.defaultLicenseId) ~
              ("attributes" -> List(("name" -> attributeName) ~ ("value"-> "attr_value")))
            ))
          )
          put("/api/datasets/" + datasetId + "/metadata", params) { checkStatus() }
        }

        // 属性候補から作成したattributesが取得できるか
        val params = Map("d" ->
          compact(render(
            ("query" -> attributeName)
          ))
        )
        get("/api/suggests/attributes", params) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[Seq[String]]]
          assert(result.data.contains(attributeName))
        }
      }
    }
  }

  def signIn() {
//    val params = Map("id" -> "dummy1", "password" -> "password")
    val params = Map("d" -> compact(render(("id" -> "dummy1") ~ ("password" -> "password"))))
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
