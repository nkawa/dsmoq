package api.status

import java.io.File

import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.{ compact, parse, render }

import api.common.DsmoqSpec
import dsmoq.AppConf
import dsmoq.controllers.AjaxResponse
import scalikejdbc.config.DBsWithEnv

class ProfileStatusCheckSpec extends DsmoqSpec {
  private val dummyImage = new File("../testdata/image/1byteover.png")
  private val dummyFile = new File("../testdata/test1.csv")
  private val dummyZipFile = new File("../testdata/test1.zip")

  private val testUserName = "dummy1"
  private val dummyUserName = "dummy4"
  private val testUserId = "023bfa40-e897-4dad-96db-9fd3cf001e79" // dummy1
  private val dummyUserId = "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04" // dummy 2
  private val dummyUserLoginParams = Map("d" -> compact(render(("id" -> "dummy4") ~ ("password" -> "password"))))

  private val OK = "OK"
  private val ILLEGAL_ARGUMENT = "Illegal Argument"
  private val UNAUTHORIZED = "Unauthorized"
  private val NOT_FOUND = "NotFound"
  private val ACCESS_DENIED = "AccessDenied"
  private val BAD_REQUEST = "BadRequest"
  private val NG = "NG"
  private val invalidApiKeyHeader = Map("Authorization" -> "api_key=hoge,signature=fuga")

  "API Status test" - {
    "signin" - {
      "POST /api/signin" - {
        "400(Illegal Argument)" in {
          val params = Map("d" -> compact(render(("id" -> "dummy1"))))
          post("/api/signin", params) {
            checkStatus(400, ILLEGAL_ARGUMENT)
          }
        }

        "400(BadRequest)" in {
          val params = Map("d" -> compact(render(("id" -> "dummy1") ~ ("password" -> "invalid"))))
          post("/api/signin", params) {
            checkStatus(400, BAD_REQUEST)
          }
        }

        "500(OK)" in {
          val params = Map("d" -> compact(render(("id" -> "dummy1") ~ ("password" -> "password"))))
          dbDisconnectedBlock {
            post("/api/signin", params) {
              checkStatus(500, NG)
            }
          }
        }

        "All" in {
          val params = Map("d" -> compact(render(("password" -> "invalid"))))
          post("/api/signin", params) {
            checkStatus(400, ILLEGAL_ARGUMENT)
          }
        }

        "400(Illegal Argument) * 500(NG)" in {
          val params = Map("d" -> compact(render(("id" -> "dummy1"))))
          dbDisconnectedBlock {
            post("/api/signin", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }

        "400(BadRequest) * 500(NG)" in {
          val params = Map("d" -> compact(render(("id" -> "dummy1") ~ ("password" -> "invalid"))))
          dbDisconnectedBlock {
            post("/api/signin", params) {
              checkStatus(500, NG)
            }
          }
        }

      }

      "POST /api/signout" - {
        // エラーが発生しないため、スキップ
      }
    }

    "profile" - {
      "GET /api/profile" - {
        "403(Unauthorized)" in {
          get("/api/profile", Seq.empty, invalidApiKeyHeader) {
            checkStatus(403, UNAUTHORIZED)
          }
        }

        "500(OK)" in {
          dbDisconnectedBlock {
            get("/api/profile") {
              checkStatus(500, NG)
            }
          }
        }

        "403(Unauthorized) * 500(NG)" in {
          dbDisconnectedBlock {
            get("/api/profile", Seq.empty, invalidApiKeyHeader) {
              checkStatus(500, NG)
            }
          }
        }
      }

      "PUT /api/profile" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("name" -> "") ~ ("fullname" -> "fullname1"))))
            put("/api/profile", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          val params = Map("d" -> compact(render(("name" -> "dummy1") ~ ("fullname" -> "fullname1"))))
          put("/api/profile", params) {
            checkStatus(403, UNAUTHORIZED)
          }
        }
        "400(BadRequest)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("name" -> "dummy2") ~ ("fullname" -> "fullname1"))))
            put("/api/profile", params) {
              checkStatus(400, BAD_REQUEST)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("name" -> "test") ~ ("fullname" -> "fullname1"))))
            dbDisconnectedBlock {
              put("/api/profile", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          val params = Map("d" -> compact(render(("name" -> "dummy2"))))
          put("/api/profile", params) {
            checkStatus(400, ILLEGAL_ARGUMENT)
          }
        }
        "403(Unauthorized) * 400(BadRequest)" in {
          val params = Map("d" -> compact(render(("name" -> "dummy2") ~ ("fullname" -> "fullname1"))))
          put("/api/profile", params) {
            checkStatus(403, UNAUTHORIZED)
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("name" -> "") ~ ("fullname" -> "fullname1"))))
            dbDisconnectedBlock {
              put("/api/profile", params) {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          val params = Map("d" -> compact(render(("name" -> "dummy1") ~ ("fullname" -> "fullname1"))))
          dbDisconnectedBlock {
            put("/api/profile", params) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
      }

      "POST /api/profile/image" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            post("/api/profile/image", Map.empty) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          post("/api/profile/image", Map.empty, Map("icon" -> dummyImage)) {
            checkStatus(403, UNAUTHORIZED)
          }
        }
        "500(NG)" in {
          session {
            signIn()
            dbDisconnectedBlock {
              post("/api/profile/image", Map.empty, Map("icon" -> dummyImage)) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          post("/api/profile/image") {
            checkStatus(400, ILLEGAL_ARGUMENT)
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            dbDisconnectedBlock {
              post("/api/profile/image") {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          dbDisconnectedBlock {
            post("/api/profile/image", Map.empty, Map("icon" -> dummyImage)) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
      }

      "POST /api/profile/email_change_requests" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("email" -> ""))))
            post("/api/profile/email_change_requests", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          val params = Map("d" -> compact(render(("email" -> "test"))))
          post("/api/profile/email_change_requests", params) {
            checkStatus(403, UNAUTHORIZED)
          }
        }
        "400(BadRequest)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("email" -> "dummy2@example.jp"))))
            post("/api/profile/email_change_requests", params) {
              checkStatus(400, BAD_REQUEST)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("email" -> "test"))))
            dbDisconnectedBlock {
              post("/api/profile/email_change_requests", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          val params = Map("d" -> compact(render(("email" -> ""))))
          post("/api/profile/email_change_requests", params) {
            checkStatus(400, ILLEGAL_ARGUMENT)
          }
        }
        "403(Unauthorized) * 400(BadRequest)" in {
          val params = Map("d" -> compact(render(("email" -> "dummy2@example.jp"))))
          post("/api/profile/email_change_requests", params) {
            checkStatus(403, UNAUTHORIZED)
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("email" -> ""))))
            dbDisconnectedBlock {
              post("/api/profile/email_change_requests", params) {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          val params = Map("d" -> compact(render(("email" -> "test"))))
          post("/api/profile/email_change_requests", params) {
            checkStatus(403, UNAUTHORIZED)
          }
        }
      }

      "PUT /api/profile/password" - {
        "400(Illegal Argument)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("currentPassword" -> "") ~ ("newPassword" -> "password2"))))
            put("/api/profile/password", params) {
              checkStatus(400, ILLEGAL_ARGUMENT)
            }
          }
        }
        "403(Unauthorized)" in {
          val params = Map("d" -> compact(render(("currentPassword" -> "password") ~ ("newPassword" -> "password2"))))
          put("/api/profile/password", params) {
            checkStatus(403, UNAUTHORIZED)
          }
        }
        "400(BadRequest)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("currentPassword" -> "hoge") ~ ("newPassword" -> "password2"))))
            put("/api/profile/password", params) {
              checkStatus(400, BAD_REQUEST)
            }
          }
        }
        "500(NG)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("currentPassword" -> "password") ~ ("newPassword" -> "password2"))))
            dbDisconnectedBlock {
              put("/api/profile/password", params) {
                checkStatus(500, NG)
              }
            }
          }
        }
        "All" in {
          val params = Map("d" -> compact(render(("currentPassword" -> "password") ~ ("newPassword" -> ""))))
          put("/api/profile/password", params) {
            checkStatus(400, ILLEGAL_ARGUMENT)
          }
        }
        "403(Unauthorized) * 400(BadRequest)" in {
          val params = Map("d" -> compact(render(("currentPassword" -> "hoge") ~ ("newPassword" -> "password2"))))
          put("/api/profile/password", params) {
            checkStatus(403, UNAUTHORIZED)
          }
        }
        "400(Illegal Argument) * 500(NG)" in {
          session {
            signIn()
            val params = Map("d" -> compact(render(("currentPassword" -> "") ~ ("newPassword" -> "password2"))))
            dbDisconnectedBlock {
              put("/api/profile/password", params) {
                checkStatus(400, ILLEGAL_ARGUMENT)
              }
            }
          }
        }
        "403(Unauthorized) * 500(NG)" in {
          val params = Map("d" -> compact(render(("currentPassword" -> "password") ~ ("newPassword" -> "password2"))))
          dbDisconnectedBlock {
            put("/api/profile/password", params) {
              checkStatus(403, UNAUTHORIZED)
            }
          }
        }
      }
    }
  }

  /**
   * DBが切断される独自スコープを持つブロックを作成するためのメソッドです。
   *
   * @param procedure ブロックで行う処理
   * @return ブロックでの処理結果
   */
  private def dbDisconnectedBlock[T](procedure: => T): T = {
    DBsWithEnv("test").close()
    try {
      procedure
    } finally {
      DBsWithEnv("test").setup()
    }
  }

  /**
   * サインアウトします。
   */
  private def signOut() {
    post("/api/signout") {
      checkStatus(200, "OK")
    }
  }

  /**
   * ダミーユーザでサインインします。
   */
  private def dummySignIn(): Unit = {
    signIn("dummy4")
  }

  private def checkStatus(code: Int, str: String): Unit = {
    checkStatus(code, Some(str))
  }
}
