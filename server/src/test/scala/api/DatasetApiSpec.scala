package api

import org.scalatest.{BeforeAndAfter, FreeSpec}
import org.scalatra.test.scalatest.ScalatraSuite
import org.json4s.{DefaultFormats, Formats}
import dsmoq.controllers.{ImageController, FileController, ApiController}
import scalikejdbc.config.DBs
import org.json4s.jackson.JsonMethods._
import java.io.File
import dsmoq.services.data.DatasetData._
import dsmoq.AppConf
import org.scalatra.servlet.MultipartConfig
import dsmoq.services.data.DatasetData.DatasetDeleteImage
import dsmoq.services.data.DatasetData.DatasetAddFiles
import dsmoq.services.data.DatasetData.Dataset
import scala.Some
import dsmoq.controllers.AjaxResponse
import dsmoq.services.data.DatasetData.DatasetAddImages
import dsmoq.services.data.RangeSlice
import dsmoq.services.data.GroupData.Group
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.{HttpStatus, NameValuePair}
import org.apache.http.client.entity.UrlEncodedFormEntity
import java.util
import org.apache.http.protocol.HTTP
import org.apache.http.util.EntityUtils
import com.sun.jndi.toolkit.url.Uri
import org.apache.http.impl.client.DefaultHttpClient

class DatasetApiSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormats: Formats = DefaultFormats

  private val dummyFile = new File("README.md")
  private val dummyImage = new File("../client/www/dummy/images/nagoya.jpg")
  private val dummyUserLoginParams = Map("id" -> "kawaguti", "password" -> "password")

  private val host = "http://localhost:8080"

  // multi-part file upload config
  val holder = addServlet(classOf[ApiController], "/api/*")
  holder.getRegistration.setMultipartConfig(
    MultipartConfig(
      maxFileSize = Some(3 * 1024 * 1024),
      fileSizeThreshold = Some(1 * 1024 * 1024)
    ).toMultipartConfigElement
  )
  addServlet(classOf[FileController], "/files/*")
  addServlet(classOf[ImageController], "/images/*")

  before {
    DBs.setup()
  }
  after {
    DBs.close()
  }

  "API test" - {
    "dataset" - {
      "データセットの一覧が取得できるか" in {
        session {
          signIn()
          val params = Map("limit" -> "10", "offset" -> "5")
          get("/api/datasets", params) {
            status should be(200)
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            println(result.data.summary)
            result.data.summary.count should be(10)
            result.data.summary.offset should be(5)
          }
        }
      }

      "データセットが作成できるか" in {
        session {
          signIn()
          val datasetId = createDataset()
          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.id should be (datasetId)
          }
        }
      }

      "データセットが作成できるか(複数ファイル)" in {
        session {
          signIn()
          val files = List(("file[]", dummyFile), ("file[]", dummyFile))
          val datasetId = post("/api/datasets", Map.empty, files) {
            checkStatus()
            parse(body).extract[AjaxResponse[Dataset]].data.id
          }

          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.id should be (datasetId)
            result.data.filesCount should be(2)
          }
        }
      }

      "作成したデータセットが削除できるか" in {
        session {
          signIn()
          val datasetId = createDataset()
          delete("/api/datasets/" + datasetId) { checkStatus() }
          get("/api/datasets/" + datasetId) {
            status should be(200)
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.status should be("NotFound")
          }
        }
      }

      // TODO attributesつきでの検証
      "データセットの情報が編集できるか" in {
        session {
          signIn()
          val datasetId = createDataset()
          val params = Map(
            "name" -> "変更後データセット",
            "description" -> "change description",
            "license" -> AppConf.defaultLicenseId
          )
          put("/api/datasets/" + datasetId + "/metadata", params) { checkStatus() }
          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.meta.name should be ("変更後データセット")
          }
        }
      }

      "データセットにファイルが追加できるか" in {
        session {
          signIn()
          val datasetId = createDataset()
          val files = Map("files[]" -> dummyFile)
          val fileId = post("/api/datasets/" + datasetId + "/files", Map.empty, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetAddFiles]]
            result.data.files(0).id
          }
          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.filesCount should be(2)
            assert(result.data.files.map(_.id).contains(fileId))
          }
        }
      }

      "データセットに複数ファイルが追加できるか" in {
        session {
          signIn()
          val datasetId = createDataset()
          val files = List(("files[]", dummyFile), ("files[]", dummyFile))
          val fileIds = post("/api/datasets/" + datasetId + "/files", Map.empty, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetAddFiles]]
            result.data.files.map(_.id)
          }

          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.filesCount should be(3)
            fileIds.map {x =>
              assert(result.data.files.map(_.id).contains(x))
            }
          }
        }
      }

      "データセットに追加したファイルが変更できるか" in {
        session {
          signIn()
          val datasetId = createDataset()
          val files = Map("files[]" -> dummyFile)
          val fileId = post("/api/datasets/" + datasetId + "/files", Map.empty, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetAddFiles]]
            result.data.files(0).id
          }

          val anotherFile = new File("build.sbt")
          val anotherFileParam = Map("file" -> anotherFile)
          post("/api/datasets/" + datasetId + "/files/" + fileId, Map.empty, anotherFileParam) {
            println(body)
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetAddFiles]]
            result.data.files(0).id should be(fileId)
          }

          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.filesCount should be(2)
            assert(result.data.files.map(_.id).contains(fileId))
            result.data.files.map {x =>
              if (x.id == fileId) {
                x.size should be (anotherFile.length)
              }
            }
          }
        }
      }

      "データセットのファイルメタデータが変更できるか" in {
        session {
          signIn()
          val datasetId = createDataset()

          // add files
          val files = Map("files[]" -> dummyFile)
          post("/api/datasets/" + datasetId + "/files", Map.empty, files) { checkStatus() }
          val fileId = post("/api/datasets/" + datasetId + "/files", Map.empty, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetAddFiles]]
            result.data.files(0).id
          }
          post("/api/datasets/" + datasetId + "/files", Map.empty, files) { checkStatus() }

          val params = Map("name" -> "testtest.txt", "description" -> "description")
          put("/api/datasets/" + datasetId + "/files/" + fileId + "/metadata", params) { checkStatus() }

          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            assert(result.data.files.map(_.id).contains(fileId))
            result.data.files.map {x =>
              if (x.id == fileId) {
                x.name should be ("testtest.txt")
                x.description should be("description")
              }
            }
          }
        }
      }

      "データセットに追加したファイルが削除できるか" in {
        session {
          signIn()
          val datasetId = createDataset()

          // add files
          val files = Map("files[]" -> dummyFile)
          post("/api/datasets/" + datasetId + "/files", Map.empty, files) { checkStatus() }
          val fileId = post("/api/datasets/" + datasetId + "/files", Map.empty, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetAddFiles]]
            result.data.files(0).id
          }
          post("/api/datasets/" + datasetId + "/files", Map.empty, files) { checkStatus() }

          delete("/api/datasets/" + datasetId + "/files/" + fileId) { checkStatus() }
          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.filesCount should be(3)
            assert(!result.data.files.map(_.id).contains(fileId))
          }
        }
      }

      "データセットに画像が追加できるか" in {
        session {
          signIn()
          val datasetId = createDataset()
          val images = Map("images" -> dummyImage)
          val imageId = post("/api/datasets/" + datasetId + "/images", Map.empty, images) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetAddImages]]
            result.data.images(0).id
          }
          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.images.size should be(2)
            assert(result.data.images.map(_.id).contains(imageId))
          }
        }
      }

      "データセットに追加した画像が削除できるか" in {
        session {
          signIn()
          val datasetId = createDataset()

          // add images
          val images = Map("images" -> dummyImage)
          post("/api/datasets/" + datasetId + "/images", Map.empty, images) { checkStatus() }
          val imageId = post("/api/datasets/" + datasetId + "/images", Map.empty, images) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetAddImages]]
            result.data.images(0).id
          }
          post("/api/datasets/" + datasetId + "/images", Map.empty, images) { checkStatus() }

          delete("/api/datasets/" + datasetId + "/images/" + imageId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetDeleteImage]]
            result.data.primaryImage should not be(imageId)
          }
          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.images.size should be(3)
            assert(!result.data.images.map(_.id).contains(imageId))
          }
        }
      }

      "データセットのメイン画像を変更できるか" in {
        session {
          signIn()
          val datasetId = createDataset()

          // add images
          val images = Map("images" -> dummyImage)
          post("/api/datasets/" + datasetId + "/images", Map.empty, images) { checkStatus() }
          val imageId = post("/api/datasets/" + datasetId + "/images", Map.empty, images) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetAddImages]]
            result.data.images(0).id
          }
          post("/api/datasets/" + datasetId + "/images", Map.empty, images) { checkStatus() }

          // change primary image
          val params = Map("id" -> imageId)
          put("/api/datasets/" + datasetId + "/images/primary", params) { checkStatus() }

          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.primaryImage should be(imageId)
          }
        }
      }

      "データセットのファイルがダウンロードできるか" in {
        session {
          signIn()
          val files = Map("file[]" -> dummyFile)
          val url = post("/api/datasets", Map.empty, files) {
            checkStatus()
            parse(body).extract[AjaxResponse[Dataset]].data.files(0).url
          }
          post("/api/signout") { checkStatus() }
          println(url)

          // 別途clientを使用してファイルDL検証
          val client = createClient
          try {
            signInWithHttpClient(client)

            val fileGet = new HttpGet(url)
            val fileResponse = client.execute(fileGet)
            val byte = EntityUtils.toByteArray(fileResponse.getEntity)
            fileResponse.getStatusLine.getStatusCode should be(HttpStatus.SC_OK)

            // バイトサイズのみでチェック ファイルは書き込まない
            byte.size should be(dummyFile.length())
          } catch {
            case e: Exception =>
              println(e.getMessage)
              println(e.getStackTraceString)
              fail()
          } finally {
            client.getConnectionManager.shutdown()
          }
        }
      }

      "データセットの画像がダウンロードできるか" in {
        session {
          signIn()
          val datasetId = createDataset()
          val images = Map("images" -> dummyImage)
          val url = post("/api/datasets/" + datasetId + "/images", Map.empty, images) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetAddImages]]
            result.data.images(0).url
          }

          println(url)
          // 別途clientを使用して画像DL検証
          val client = createClient
          try {
            signInWithHttpClient(client)

            val fileGet = new HttpGet(url)
            val fileResponse = client.execute(fileGet)
            val byte = EntityUtils.toByteArray(fileResponse.getEntity)
            fileResponse.getStatusLine.getStatusCode should be(HttpStatus.SC_OK)

            // バイトサイズのみでチェック ファイルは書き込まない
            byte.size should be(dummyImage.length())
          } catch {
            case e: Exception =>
              println(e.getMessage)
              println(e.getStackTraceString)
              fail()
          } finally {
            client.getConnectionManager.shutdown()
          }
        }
      }

      "データセットACLアイテム アクセスレベル設定したデータが閲覧できるか" in {
        session {
          // データセット作成
          signIn()
          val datasetId = createDataset()
          post("/api/signout") { checkStatus() }

          // アクセスレベル設定対象のグループを作成
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          val createGroupParams = Map("name" -> "group name", "description" -> "group description")
          val groupId = post("/api/groups", createGroupParams) {
            checkStatus()
            parse(body).extract[AjaxResponse[Group]].data.id
          }
          post("/api/signout") { checkStatus() }

          // アクセスレベル設定
          signIn()
          val params = Map("accessLevel" -> "2")
          put("/api/datasets/" + datasetId + "/acl/" + groupId, params) { checkStatus() }
          post("/api/signout") { checkStatus() }

          // アクセスレベルを設定したdatasetはそのユーザーから参照できるはず
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get("/api/datasets") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should not be(0)
          }
          // TODO 権限のないデータセットアクセスでNG？
          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.id should be(datasetId)
          }
        }
      }

      "データセットACLアイテムが削除できるか" in {
        session {
          signIn()
          // データセット作成
          signIn()
          val datasetId = createDataset()
          post("/api/signout") { checkStatus() }

          // アクセスレベル設定対象のグループを作成
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          val createGroupParams = Map("name" -> "group name", "description" -> "group description")
          val groupId = post("/api/groups", createGroupParams) {
            checkStatus()
            parse(body).extract[AjaxResponse[Group]].data.id
          }
          post("/api/signout") { checkStatus() }

          // アクセスレベル設定
          signIn()
          val params = Map("accessLevel" -> "2")
          put("/api/datasets/" + datasetId + "/acl/" + groupId, params) { checkStatus() }
          post("/api/signout") { checkStatus() }

          // アクセスレベルを設定したdatasetはグループから参照できるはず
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.id should be(datasetId)
          }
          post("/api/signout") { checkStatus() }

          // アクセスレベル解除
          signIn()
          delete("/api/datasets/" + datasetId + "/acl/" + groupId) { checkStatus() }
          post("/api/signout") { checkStatus() }

          // アクセスレベルを解除したdatasetはグループから見えなくなるはず
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get("/api/datasets/" + datasetId) {
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.status should be("Unauthorized")
          }
        }
      }

      "データセットACLアイテム ゲストアクセスレベル設定したデータがゲストから閲覧できるか" in {
        session {
          signIn()
          val datasetId = createDataset()

          // アクセスレベル設定
          val params = Map("accessLevel" -> "2")
          put("/api/datasets/" + datasetId + "/acl/guest", params) { checkStatus() }

          // アクセスレベルを設定したdatasetはゲストから参照できるはず
          post("/api/signout") { checkStatus() }
          get("/api/datasets") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should not be(0)
          }

          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.id should be(datasetId)
          }
        }
      }

      "データセットACLアイテム ゲストアクセスレベルが解除できるか" in {
        session {
          signIn()
          val datasetId = createDataset()

          // アクセスレベル設定
          val params = Map("accessLevel" -> "2")
          put("/api/datasets/" + datasetId + "/acl/guest", params) { checkStatus() }

          // アクセスレベルを設定したdatasetはゲストから参照できるはず
          post("/api/signout") { checkStatus() }
          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.id should be(datasetId)
          }

          // アクセスレベルを解除したdatasetはゲストから見えなくなるはず
          signIn()
          delete("/api/datasets/" + datasetId + "/acl/guest") { checkStatus() }
          post("/api/signout") { checkStatus() }
          get("/api/datasets/" + datasetId) {
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.status should be("Unauthorized")
          }
        }
      }
    }
  }

  private def signIn() {
    val params = Map("id" -> "t_okada", "password" -> "password")
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
    post("/api/datasets", Map.empty, files) {
      checkStatus()
      parse(body).extract[AjaxResponse[Dataset]].data.id
    }
  }

  private def signInWithHttpClient(client: DefaultHttpClient) {
    val signInPost = new HttpPost(host + "/api/signin")
    signInPost.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
    val params = new util.ArrayList[NameValuePair]()
    params.add(new BasicNameValuePair("id", "t_okada"))
    params.add(new BasicNameValuePair("password", "password"))
    signInPost.setEntity(new UrlEncodedFormEntity(params))
    val signInResponse = client.execute(signInPost)
    signInResponse.getStatusLine.getStatusCode should be(HttpStatus.SC_OK)
    signInResponse.getEntity.getContent.close()
  }
}
