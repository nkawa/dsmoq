package api

import org.scalatest.{BeforeAndAfter, FreeSpec}
import org.scalatra.test.scalatest.ScalatraSuite
import org.json4s.{DefaultFormats, Formats}
import scalikejdbc.config.DBs
import dsmoq.controllers.{AjaxResponse, ApiController}
import org.json4s.jackson.JsonMethods._
import dsmoq.services.data.User
import java.io.File
import org.scalatra.servlet.MultipartConfig

class AccountApiSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormats: Formats = DefaultFormats

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
  }
  after {
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
            "name" -> "t_okada",
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
            "name" -> "t_okada",
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

          // 戻す
          val rollbackParams = Map("email" -> "t_okada@denkiyagi.jp")
          post("/api/profile/email_change_requests", rollbackParams) { checkStatus() }
          get("/api/profile") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[User]]
            result.data.mailAddress should be("t_okada@denkiyagi.jp")
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
          val signinParams = Map("id" -> "t_okada", "password" -> "new_password")
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
    }
  }
  
  def signIn() {
    val params = Map("id" -> "t_okada", "password" -> "password")
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
