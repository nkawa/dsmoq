package api

import java.io.File
import java.util.UUID
import java.util.ResourceBundle

import org.eclipse.jetty.servlet.ServletHolder

import _root_.api.api.logic.SpecCommonLogic
import dsmoq.controllers.{ FileController, ApiController, AjaxResponse }
import dsmoq.persistence._
import dsmoq.services.json.DatasetData.{ Dataset, DatasetFile }
import dsmoq.services.json.GroupData.Group
import dsmoq.services.json.RangeSlice
import org.eclipse.jetty.server.Connector
import org.json4s.{ DefaultFormats, Formats }
import org.json4s.jackson.JsonMethods._
import org.scalatest.{ BeforeAndAfter, FreeSpec }
import org.scalatra.servlet.MultipartConfig
import org.scalatra.test.scalatest.ScalatraSuite
import scalikejdbc.config.{ DBsWithEnv, DBs }
import org.json4s._
import org.json4s.JsonDSL._

class FileDownloadAuthorizationSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormats: Formats = DefaultFormats

  private val dummyFile = new File("../README.md")
  private val dummyUserId = "eb7a596d-e50c-483f-bbc7-50019eea64d7" // dummy 4
  private val dummyUserLoginParams = Map("d" -> compact(render(("id" -> "dummy4") ~ ("password" -> "password"))))
  private val anotherUserLoginParams = Map("d" -> compact(render(("id" -> "dummy2") ~ ("password" -> "password"))))

  override def beforeAll() {
    super.beforeAll()
    DBsWithEnv("test").setup()
    System.setProperty(org.scalatra.EnvironmentKey, "test")

    val resource = ResourceBundle.getBundle("message")
    val servlet = new ApiController(resource)
    val holder = new ServletHolder(servlet.getClass.getName, servlet)
    // multi-part file upload config
    val multipartConfig = MultipartConfig(
      maxFileSize = Some(3 * 1024 * 1024),
      fileSizeThreshold = Some(1 * 1024 * 1024)
    ).toMultipartConfigElement
    holder.getRegistration.setMultipartConfig(multipartConfig)
    servletContextHandler.addServlet(holder, "/api/*")
    addServlet(new FileController(resource), "/files/*")
    SpecCommonLogic.deleteAllCreateData()
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

  "Authorization Test" - {
    "設定した権限にあわせてファイルをダウンロードできるか" - {
      "Deny*Deny*Deny" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Deny, GroupAccessLevel.Deny, DefaultAccessLevel.Deny)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Limited*Deny*Deny" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.LimitedRead, GroupAccessLevel.Deny, DefaultAccessLevel.Deny)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Full*Deny*Deny" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.FullPublic, GroupAccessLevel.Deny, DefaultAccessLevel.Deny)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Owner*Deny*Deny" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Owner, GroupAccessLevel.Deny, DefaultAccessLevel.Deny)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Deny*Limited*Deny" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Deny, GroupAccessLevel.LimitedPublic, DefaultAccessLevel.Deny)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Limited*Limited*Deny" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.LimitedRead, GroupAccessLevel.LimitedPublic, DefaultAccessLevel.Deny)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Full*Limited*Deny" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.FullPublic, GroupAccessLevel.LimitedPublic, DefaultAccessLevel.Deny)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Owner*Limited*Deny" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Owner, GroupAccessLevel.LimitedPublic, DefaultAccessLevel.Deny)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Deny*Full*Deny" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Deny, GroupAccessLevel.FullPublic, DefaultAccessLevel.Deny)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Limited*Full*Deny" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.LimitedRead, GroupAccessLevel.FullPublic, DefaultAccessLevel.Deny)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Full*Full*Deny" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.FullPublic, GroupAccessLevel.FullPublic, DefaultAccessLevel.Deny)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Owner*Full*Deny" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Owner, GroupAccessLevel.FullPublic, DefaultAccessLevel.Deny)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Deny*Provider*Deny" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Deny, GroupAccessLevel.Provider, DefaultAccessLevel.Deny)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Limited*Provider*Deny" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.LimitedRead, GroupAccessLevel.Provider, DefaultAccessLevel.Deny)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Full*Provider*Deny" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.FullPublic, GroupAccessLevel.Provider, DefaultAccessLevel.Deny)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Owner*Provider*Deny" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Owner, GroupAccessLevel.Provider, DefaultAccessLevel.Deny)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Deny*Deny*Limited" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Deny, GroupAccessLevel.Deny, DefaultAccessLevel.LimitedPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Limited*Deny*Limited" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.LimitedRead, GroupAccessLevel.Deny, DefaultAccessLevel.LimitedPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Full*Deny*Limited" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.FullPublic, GroupAccessLevel.Deny, DefaultAccessLevel.LimitedPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Owner*Deny*Limited" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Owner, GroupAccessLevel.Deny, DefaultAccessLevel.LimitedPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Deny*Limited*Limited" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Deny, GroupAccessLevel.LimitedPublic, DefaultAccessLevel.LimitedPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Limited*Limited*Limited" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.LimitedRead, GroupAccessLevel.LimitedPublic, DefaultAccessLevel.LimitedPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Full*Limited*Limited" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.FullPublic, GroupAccessLevel.LimitedPublic, DefaultAccessLevel.LimitedPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Owner*Limited*Limited" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Owner, GroupAccessLevel.LimitedPublic, DefaultAccessLevel.LimitedPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Deny*Full*Limited" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Deny, GroupAccessLevel.FullPublic, DefaultAccessLevel.LimitedPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Limited*Full*Limited" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.LimitedRead, GroupAccessLevel.FullPublic, DefaultAccessLevel.LimitedPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Full*Full*Limited" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.FullPublic, GroupAccessLevel.FullPublic, DefaultAccessLevel.LimitedPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Owner*Full*Limited" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Owner, GroupAccessLevel.FullPublic, DefaultAccessLevel.LimitedPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Deny*Provider*Limited" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Deny, GroupAccessLevel.Provider, DefaultAccessLevel.LimitedPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Limited*Provider*Limited" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.LimitedRead, GroupAccessLevel.Provider, DefaultAccessLevel.LimitedPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Full*Provider*Limited" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.FullPublic, GroupAccessLevel.Provider, DefaultAccessLevel.LimitedPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Owner*Provider*Limited" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Owner, GroupAccessLevel.Provider, DefaultAccessLevel.LimitedPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(403)
          }
        }
      }
      "Deny*Deny*Full" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Deny, GroupAccessLevel.Deny, DefaultAccessLevel.FullPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
        }
      }
      "Limited*Deny*Full" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.LimitedRead, GroupAccessLevel.Deny, DefaultAccessLevel.FullPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
        }
      }
      "Full*Deny*Full" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.FullPublic, GroupAccessLevel.Deny, DefaultAccessLevel.FullPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
        }
      }
      "Owner*Deny*Full" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Owner, GroupAccessLevel.Deny, DefaultAccessLevel.FullPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
        }
      }
      "Deny*Limited*Full" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Deny, GroupAccessLevel.LimitedPublic, DefaultAccessLevel.FullPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
        }
      }
      "Limited*Limited*Full" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.LimitedRead, GroupAccessLevel.LimitedPublic, DefaultAccessLevel.FullPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
        }
      }
      "Full*Limited*Full" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.FullPublic, GroupAccessLevel.LimitedPublic, DefaultAccessLevel.FullPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
        }
      }
      "Owner*Limited*Full" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Owner, GroupAccessLevel.LimitedPublic, DefaultAccessLevel.FullPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
        }
      }
      "Deny*Full*Full" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Deny, GroupAccessLevel.FullPublic, DefaultAccessLevel.FullPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
        }
      }
      "Limited*Full*Full" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.LimitedRead, GroupAccessLevel.FullPublic, DefaultAccessLevel.FullPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
        }
      }
      "Full*Full*Full" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.FullPublic, GroupAccessLevel.FullPublic, DefaultAccessLevel.FullPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
        }
      }
      "Owner*Full*Full" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Owner, GroupAccessLevel.FullPublic, DefaultAccessLevel.FullPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
        }
      }
      "Deny*Provider*Full" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Deny, GroupAccessLevel.Provider, DefaultAccessLevel.FullPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
        }
      }
      "Limited*Provider*Full" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.LimitedRead, GroupAccessLevel.Provider, DefaultAccessLevel.FullPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
        }
      }
      "Full*Provider*Full" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.FullPublic, GroupAccessLevel.Provider, DefaultAccessLevel.FullPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
        }
      }
      "Owner*Provider*Full" in {
        session {
          signIn()
          val datasetId = createPermissionedDataset(UserAccessLevel.Owner, GroupAccessLevel.Provider, DefaultAccessLevel.FullPublic)
          val uri = new java.net.URI(getFileUrl(datasetId))
          // ダミーユーザー時のダウンロードチェック AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // ゲストアクセス時のダウンロードチェック  AllowRead以上でダウンロード可
          post("/api/signout") { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
          // 何も権限を付与していないユーザーのダウンロードチェック ゲストと同じアクセス制限となる
          post("/api/signin", anotherUserLoginParams) { checkStatus() }
          get(uri.getPath) {
            status should be(200)
          }
        }
      }
    }
  }

  private def createPermissionedDataset(
    userAccessLevel: Int,
    groupAccessLevel: Int,
    guestAccessLevel: Int
  ): String = {
    // グループ作成
    val groupId = createGroup()
    val memberParams = Map("d" -> compact(render(Seq(("userId" -> dummyUserId) ~ ("role" -> GroupMemberRole.Member)))))
    post("/api/groups/" + groupId + "/members", memberParams) { checkStatus() }
    // データセット作成
    val datasetId = createDataset()
    setPermission(datasetId, groupId, userAccessLevel, groupAccessLevel, guestAccessLevel)
    datasetId
  }

  private def setPermission(
    datasetId: String,
    groupId: String,
    userAccessLevel: Int,
    groupAccessLevel: Int,
    guestAccessLevel: Int
  ): Unit = {
    // アクセスレベル設定(ユーザー/グループ)
    val accessLevelParams = Map(
      "d" -> compact(
        render(
          Seq(
            ("id" -> dummyUserId) ~ ("ownerType" -> JInt(OwnerType.User)) ~ ("accessLevel" -> JInt(userAccessLevel)),
            ("id" -> groupId) ~ ("ownerType" -> JInt(OwnerType.Group)) ~ ("accessLevel" -> JInt(groupAccessLevel))
          )
        )
      )
    )
    post("/api/datasets/" + datasetId + "/acl", accessLevelParams) { checkStatus() }
    // ゲストアクセスレベル設定
    val guestAccessLevelParams = Map("d" -> compact(render(("accessLevel" -> guestAccessLevel))))
    put("/api/datasets/" + datasetId + "/guest_access", guestAccessLevelParams) { checkStatus() }
  }

  private def getFileUrl(datasetId: String): String = {
    get(s"/api/datasets/${datasetId}/files") {
      val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
      result.data.results(0).url.get
    }
  }

  private def signIn() {
    val params = Map("d" -> compact(render(("id" -> "dummy1") ~ ("password" -> "password"))))
    post("/api/signin", params) {
      checkStatus()
    }
  }

  private def checkStatus() {
    status should be(200)
    val result = parse(body).extract[AjaxResponse[Any]]
    result.status should be("OK")
  }

  private def createDataset(): String = {
    val files = Map("file[]" -> dummyFile)
    val params = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
    post("/api/datasets", params, files) {
      checkStatus()
      parse(body).extract[AjaxResponse[Dataset]].data.id
    }
  }
  private def createGroup(): String = {
    val groupName = "groupName" + UUID.randomUUID.toString
    val params = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "groupDescription"))))
    post("/api/groups", params) {
      checkStatus()
      parse(body).extract[AjaxResponse[Group]].data.id
    }
  }
}
