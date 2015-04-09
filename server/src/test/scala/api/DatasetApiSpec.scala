package api

import java.nio.file.Paths
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.net.URLEncoder

import _root_.api.api.logic.SpecCommonLogic
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import org.eclipse.jetty.server.Connector
import org.scalatest.{BeforeAndAfter, FreeSpec}
import org.scalatra.test.scalatest.ScalatraSuite
import org.json4s.{DefaultFormats, Formats}
import dsmoq.controllers.{ImageController, FileController, ApiController}
import scalikejdbc.config.{DBsWithEnv, DBs}
import org.json4s.jackson.JsonMethods._
import java.io.File
import dsmoq.services.json.DatasetData._
import dsmoq.services.json.TaskData._
import dsmoq.AppConf
import org.scalatra.servlet.MultipartConfig
import dsmoq.services.json.DatasetData.DatasetDeleteImage
import dsmoq.services.json.DatasetData.DatasetAddFiles
import dsmoq.services.json.DatasetData.Dataset
import dsmoq.controllers.AjaxResponse
import dsmoq.services.json.DatasetData.DatasetAddImages
import dsmoq.services.json.RangeSlice
import dsmoq.services.json.GroupData.Group
import java.util.{Base64, UUID}
import dsmoq.persistence.{DefaultAccessLevel, OwnerType, UserAccessLevel, GroupAccessLevel}
import org.json4s._
import org.json4s.JsonDSL._
import scalikejdbc._

class DatasetApiSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormats: Formats = DefaultFormats

  private val dummyFile = new File("README.md")
  private val dummyImage = new File("../client/www/dummy/images/nagoya.jpg")
  private val dummyUserId = "eb7a596d-e50c-483f-bbc7-50019eea64d7"  // dummy 4
  private val dummyUserLoginParams = Map("d" -> compact(render(("id" -> "dummy4") ~ ("password" -> "password"))))

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

  //NOTE baseUrlの解決に失敗するため、まったく同じロジックでScalatraSuiteとoverrideしている
  override def baseUrl: String =
    server.getConnectors collectFirst {
      case conn: Connector =>
        val host = Option(conn.getHost) getOrElse "localhost"
        val port = conn.getLocalPort
        require(port > 0, "The detected local port is < 1, that's not allowed")
        "http://%s:%d".format(host, port)
    } getOrElse sys.error("can't calculate base URL: no connector")

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
    "dataset" - {
      "データセットの一覧が取得できるか" in {
        session {
          signIn()
          val datasetId = createDataset()

          val params = Map("d" -> compact(render(("limit" -> JInt(10)))))
          get("/api/datasets", params) {
            status should be(200)
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            println(result.data.summary)
            result.data.summary.count should be(10)
            assert(result.data.results.map(_.id).contains(datasetId))
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
          val datasetDatas = post("/api/datasets", Map.empty, files) {
            checkStatus()
//            parse(body).extract[AjaxResponse[Dataset]].data.id
            val result = parse(body).extract[AjaxResponse[Dataset]]
            val datasetId = result.data.id
            val fileIds = result.data.files.map(_.id).sorted
            (datasetId, fileIds)
          }

          get("/api/datasets/" + datasetDatas._1) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.id should be (datasetDatas._1)
            result.data.filesCount should be(2)
            result.data.files.map(_.id).sorted.sameElements(datasetDatas._2)
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

      "データセットの情報が編集できるか" in {
        session {
          signIn()
          val datasetId = createDataset()
          val params = Map("d" ->
            compact(render(
              ("name" -> "変更後データセット") ~
              ("description" -> "change description") ~
              ("license" -> AppConf.defaultLicenseId)
            ))
          )
          put("/api/datasets/" + datasetId + "/metadata", params) { checkStatus() }
          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.meta.name should be ("変更後データセット")
            result.data.meta.description should be ("change description")
            result.data.meta.license should be(AppConf.defaultLicenseId)
          }
        }
      }

      "データセットの情報が編集できるか(attribute込み)" in {
        session {
          signIn()
          session {
            signIn()
            val datasetId = createDataset()
            val params = List("d" ->
              compact(render(
                ("name" -> "変更後データセット") ~
                ("description" -> "change description") ~
                ("license" -> AppConf.defaultLicenseId) ~
                ("attributes" -> List(
                  ("name" -> "attr_name") ~ ("value" -> "attr_value"),
                  ("name" -> "attr_another_name") ~ ("value" -> "attr_another_value")
                ))
              ))
            )
            put("/api/datasets/" + datasetId + "/metadata", params) { checkStatus() }
            get("/api/datasets/" + datasetId) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.meta.name should be ("変更後データセット")
              result.data.meta.description should be ("change description")
              result.data.meta.license should be(AppConf.defaultLicenseId)
              result.data.meta.attributes.map(_.name).contains("attr_name")
              result.data.meta.attributes.map(_.name).contains("attr_another_name")
              result.data.meta.attributes.map(_.value).contains("attr_value")
              result.data.meta.attributes.map(_.value).contains("attr_another_value")
            }
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
          val url = post("/api/datasets/" + datasetId + "/files/" + fileId, Map.empty, anotherFileParam) {
            println(body)
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetFile]]
            result.data.id should be(fileId)
            result.data.url
          }

          get(new java.net.URI(url).getPath) {
            status should be(200)
          }

          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.filesCount should be(2)
            // IDの有無をチェック後、付随するデータのチェック
            assert(result.data.files.map(_.id).contains(fileId))
            result.data.files.foreach { x =>
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

          val params = Map("d" -> compact(render(("name" -> "testtest.txt") ~ ("description" -> "description"))))
          put("/api/datasets/" + datasetId + "/files/" + fileId + "/metadata", params) { checkStatus() }

          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            // IDの有無をチェック後、付随するデータのチェック
            assert(result.data.files.map(_.id).contains(fileId))
            result.data.files.foreach { x =>
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

          // add file(x3)
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

          // add image(x3)
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
          val params = Map("d" -> compact(render(("imageId" -> imageId))))
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

          // ダウンロードチェック(リダイレクトされるか)
          val uri = new java.net.URI(url)
          get(uri.getPath) {
            status should be(200)
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

          // ダウンロードチェック(バイトサイズのみチェック)
          val uri = new java.net.URI(url)
          get(uri.getPath) {
            status should be(200)
            bodyBytes.size should be(dummyImage.length())
          }
        }
      }

      "データセットACLアイテム アクセスレベル設定したデータが閲覧できるか(ユーザー)" in {
        session {
          // データセット作成
          signIn()
          val datasetId = createDataset()

          // アクセスレベル設定(ユーザー)
          val params = Map("d" ->
            compact(render(List(
              ("id" -> dummyUserId) ~
              ("ownerType" -> JInt(OwnerType.User)) ~
              ("accessLevel" -> JInt(UserAccessLevel.FullPublic))
            )))
          )
          post("/api/datasets/" + datasetId + "/acl", params) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
            assert(result.data.map(_.id) contains(dummyUserId))
          }
          post("/api/signout") { checkStatus() }

          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get("/api/datasets") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            assert(result.data.results.map(_.id).contains(datasetId))
          }
          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.id should be(datasetId)
          }
        }
      }

      "データセットACLアイテム アクセスレベル設定したデータが閲覧できるか(グループ)" in {
        session {
          // データセット作成
          signIn()
          val datasetId = createDataset()
          post("/api/signout") { checkStatus() }

          // アクセスレベル設定対象のグループを作成
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          val groupName = "groupName" + UUID.randomUUID().toString
          val createGroupParams = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "group description"))))
          val groupId = post("/api/groups", createGroupParams) {
            checkStatus()
            parse(body).extract[AjaxResponse[Group]].data.id
          }
          post("/api/signout") { checkStatus() }

          // アクセスレベル設定(グループ)
          signIn()
          val params = Map("d" ->
            compact(render(List(
              ("id" -> groupId) ~
              ("ownerType" -> JInt(OwnerType.Group)) ~
              ("accessLevel" -> JInt(UserAccessLevel.FullPublic))
            )))
          )
          post("/api/datasets/" + datasetId + "/acl", params) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
            assert(result.data.map(_.id) contains(groupId))
          }
          post("/api/signout") { checkStatus() }

          // アクセスレベルを設定したdatasetはそのユーザー(グループ)から参照できるはず
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get("/api/datasets") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            assert(result.data.results.map(_.id).contains(datasetId))
          }
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
          val groupName = "group name" + UUID.randomUUID().toString
          val createGroupParams = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "group description"))))
          val groupId = post("/api/groups", createGroupParams) {
            checkStatus()
            parse(body).extract[AjaxResponse[Group]].data.id
          }
          post("/api/signout") { checkStatus() }

          // アクセスレベル設定
          signIn()
          val params = Map("d" ->
            compact(render(List(
              ("id" -> groupId) ~
              ("ownerType" -> JInt(OwnerType.Group)) ~
              ("accessLevel" -> JInt(GroupAccessLevel.FullPublic))
            )))
          )
          post("/api/datasets/" + datasetId + "/acl", params) {
            println(body)
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
            assert(result.data.map(_.id) contains(groupId))
          }
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
          val deleteParams = Map("d" ->
            compact(render(List(
              ("id" -> groupId) ~
              ("ownerType" -> JInt(OwnerType.Group)) ~
              ("accessLevel" -> JInt(GroupAccessLevel.Deny))
            )))
          )
          post("/api/datasets/" + datasetId + "/acl", deleteParams) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
            assert(result.data.map(_.id) contains(groupId))
          }
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
          val params = Map("d" -> compact(render(("accessLevel" -> JInt(DefaultAccessLevel.FullPublic)))))
          put("/api/datasets/" + datasetId + "/guest_access", params) { checkStatus() }

          // アクセスレベルを設定したdatasetはゲストから参照できるはず
          post("/api/signout") { checkStatus() }
          get("/api/datasets") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            assert(result.data.results.map(_.id).contains(datasetId))
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
          val params = Map("d" -> compact(render(("accessLevel" -> JInt(DefaultAccessLevel.FullPublic)))))
          put("/api/datasets/" + datasetId + "/guest_access", params) { checkStatus() }

          // アクセスレベルを設定したdatasetはゲストから参照できるはず
          post("/api/signout") { checkStatus() }
          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.id should be(datasetId)
          }

          // アクセスレベルを解除したdatasetはゲストから見えなくなるはず
          signIn()
          val deleteParams = Map("d" -> compact(render(("accessLevel" -> JInt(DefaultAccessLevel.Deny)))))
          put("/api/datasets/" + datasetId + "/guest_access", deleteParams) { checkStatus() }
          post("/api/signout") { checkStatus() }
          get("/api/datasets/" + datasetId) {
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.status should be("Unauthorized")
          }
        }
      }

      "データセットのownership情報が期待通りに並ぶか" in {
        session {
          signIn()
          val datasetId = createDataset()

          // グループ作成 それぞれに権限付与
          val providerGroupName = "group provider " + UUID.randomUUID().toString
          val providerParams = Map("d" -> compact(render(("name" -> providerGroupName) ~ ("description" -> "Provider Group"))))
          val providerGroupId = post("/api/groups", providerParams) {
            checkStatus()
            parse(body).extract[AjaxResponse[Group]].data.id
          }

          val readGroupName = "group read " + UUID.randomUUID().toString
          val readParams = Map("d" -> compact(render(("name" -> readGroupName) ~ ("description" -> "Provider Read"))))
          val fullPublicGroupId = post("/api/groups", readParams) {
            checkStatus()
            parse(body).extract[AjaxResponse[Group]].data.id
          }

          val readLimitedGroupName = "group read limited " + UUID.randomUUID().toString
          val readLimitedParams = Map("d" -> compact(render(("name" -> readLimitedGroupName) ~ ("description" -> "Provider Read Limited"))))
          val limitedReadGroupId = post("/api/groups", readLimitedParams) {
            checkStatus()
            parse(body).extract[AjaxResponse[Group]].data.id
          }

          val groupAccessLevels = Map("d" -> compact(render(List(
              ("id" -> providerGroupId) ~ ("ownerType" -> JInt(OwnerType.Group)) ~ ("accessLevel" -> JInt(GroupAccessLevel.Provider)),
              ("id" -> fullPublicGroupId) ~ ("ownerType" -> JInt(OwnerType.Group)) ~ ("accessLevel" -> JInt(GroupAccessLevel.FullPublic)),
              ("id" -> limitedReadGroupId) ~ ("ownerType" -> JInt(OwnerType.Group)) ~ ("accessLevel" -> JInt(GroupAccessLevel.LimitedPublic))
            )))
          )
          post("/api/datasets/" + datasetId + "/acl", groupAccessLevels) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
            assert(result.data.map(_.id) contains(providerGroupId))
            assert(result.data.map(_.id) contains(fullPublicGroupId))
            assert(result.data.map(_.id) contains(limitedReadGroupId))
          }

          // 3ユーザーそれぞれに権限付与
          val ownerUserId = "eb7a596d-e50c-483f-bbc7-50019eea64d7"
          val fullPublicUserId = "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04"
          val limitedReadUserId = "4aaefd45-2fe5-4ce0-b156-3141613f69a6"
          val userAccessLevels = Map("d" -> compact(render(List(
              ("id" -> ownerUserId) ~ ("ownerType" -> JInt(OwnerType.User)) ~ ("accessLevel" -> JInt(UserAccessLevel.Owner)),
              ("id" -> fullPublicUserId) ~ ("ownerType" -> JInt(OwnerType.User)) ~ ("accessLevel" -> JInt(UserAccessLevel.FullPublic)),
              ("id" -> limitedReadUserId) ~ ("ownerType" -> JInt(OwnerType.User)) ~ ("accessLevel" -> JInt(UserAccessLevel.LimitedRead))
            )))
          )
          post("/api/datasets/" + datasetId + "/acl", userAccessLevels) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
            assert(result.data.map(_.id) contains(ownerUserId))
            assert(result.data.map(_.id) contains(fullPublicUserId))
            assert(result.data.map(_.id) contains(limitedReadUserId))
          }

          // データセット取得 結果のソート確認
          get("/api/datasets/" + datasetId) {
            status should be(200)
            val result = parse(body).extract[AjaxResponse[Dataset]].data.ownerships
            // debug write
            println(datasetId)
            println(body)

            val loginUserId = "023bfa40-e897-4dad-96db-9fd3cf001e79"
            // ログインユーザーのowner権限、ownerのuser、ownerのグループ、full public(read)のユーザー、
            // full public(read)のグループ、read limitedのユーザー、read limitedのグループの順に並ぶ
            result.size should be(7)
            result(0).id should be(loginUserId)
            result(1).id should be(ownerUserId)
            result(2).id should be(providerGroupId)
            result(3).id should be(fullPublicUserId)
            result(4).id should be(fullPublicGroupId)
            result(5).id should be(limitedReadUserId)
            result(6).id should be(limitedReadGroupId)
          }
        }
      }

      "S3にアップロードするデータセット(同期中)のファイルがダウンロードできるか" in {
        session {
          signIn()
          val files = Map("file[]" -> dummyFile)
          val params = Map("saveLocal" -> "false", "saveS3" -> "true")
          val url = post("/api/datasets", params, files) {
            checkStatus()
            val dataset = parse(body).extract[AjaxResponse[Dataset]]
            dataset.data.s3State should be(2)
            dataset.data.localState should be(3)
            dataset.data.files(0).url
          }

          val uri = new java.net.URI(url)
          get(uri.getPath) {
            status should be(200)
          }
        }
      }

      "S3にアップロードするデータセット(同期後)のファイルがダウンロードできるか" in {
        session {
          signIn()
          val files = Map("file[]" -> dummyFile)
          val params = Map("saveLocal" -> "false", "saveS3" -> "true")
          val data = post("/api/datasets", params, files) {
            checkStatus()
            val dataset = parse(body).extract[AjaxResponse[Dataset]]
            dataset.data.s3State should be(2)
            dataset.data.localState should be(3)
            dataset.data
          }

          val localFiles = flattenFilePath(Paths.get(AppConf.fileDir, data.id).toFile)
          val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
          val client = new AmazonS3Client(cre)
          for  (file <- localFiles) {
            val separator = if (System.getProperty("file.separator") == "\\") { System.getProperty("file.separator") * 2 } else { System.getProperty("file.separator") }
            val filePath = file.getCanonicalPath.split(separator).reverse.take(4).reverse.mkString("/")
            client.putObject(AppConf.s3UploadRoot, filePath, file)
          }
          changeStorageState(data.id, 0, 1)

          val uri = new java.net.URI(data.files(0).url
          )
          get(uri.getPath) {
            // リダイレクト
            status should be(302)
          }
        }
      }

      "ローカルとS3にアップロードするデータセットのファイルがダウンロードできるか" in {
        session {
          signIn()
          val files = Map("file[]" -> dummyFile)
          val params = Map("saveLocal" -> "true", "saveS3" -> "true")
          val url = post("/api/datasets", params, files) {
            checkStatus()
            val dataset = parse(body).extract[AjaxResponse[Dataset]]
            dataset.data.s3State should be(2)
            dataset.data.localState should be(1)
            dataset.data.files(0).url
          }

          val uri = new java.net.URI(url)
          get(uri.getPath) {
            status should be(200)
          }
        }
      }

      "保存先を変更できるか（ローカルのみに保存がある場合）" in {
        session {
          signIn()
          val files = Map("file[]" -> dummyFile)
          val id = post("/api/datasets", Map.empty, files) {
            checkStatus()
            parse(body).extract[AjaxResponse[Dataset]].data.id
          }

          changeStorageState(id, 1, 0)
          // ローカルのみに保存 => どちらにも保存しない(イレギュラー)
          put("/api/datasets/" + id + "/storage", Map("d" -> compact(render(("saveLocal" -> JBool(false)) ~ ("saveS3" -> JBool(false)))))) {
            status should be(200)
            val result = parse(body).extract[AjaxResponse[Any]]
            result.status should be("BadRequest")
          }

          get("/api/datasets/" + id) {
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.localState should be(1)
            result.data.s3State should be(0)
          }

          changeStorageState(id, 1, 0)
          // ローカルのみに保存 => s3のみに保存
          put("/api/datasets/" + id + "/storage", Map("d" -> compact(render(("saveLocal" -> JBool(false)) ~ ("saveS3" -> JBool(true)))))) { checkStatus() }

          get("/api/datasets/" + id) {
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.localState should be(3)
            result.data.s3State should be(2)
          }

          changeStorageState(id, 1, 0)
          // ローカルのみに保存 => ローカルのみに保存
          put("/api/datasets/" + id + "/storage", Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> JBool(false)))))) { checkStatus() }

          get("/api/datasets/" + id) {
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.localState should be(1)
            result.data.s3State should be(0)
          }

          changeStorageState(id, 1, 0)
          // ローカルのみに保存 => ローカル、s3に保存
          put("/api/datasets/" + id + "/storage", Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> JBool(true)))))) { checkStatus() }

          get("/api/datasets/" + id) {
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.localState should be(1)
            result.data.s3State should be(2)
          }
        }
      }

      "保存先を変更できるか（S3のみに保存がある場合）" in {
        session {
          signIn()
          val files = Map("file[]" -> dummyFile)
          val id = post("/api/datasets", Map.empty, files) {
            checkStatus()
            parse(body).extract[AjaxResponse[Dataset]].data.id
          }

          changeStorageState(id, 0, 1)
          // s3のみに保存 => どちらにも保存しない(イレギュラー)
          put("/api/datasets/" + id + "/storage", Map("d" -> compact(render(("saveLocal" -> JBool(false)) ~ ("saveS3" -> JBool(false)))))) {
            status should be(200)
            val result = parse(body).extract[AjaxResponse[Any]]
            result.status should be("BadRequest")
          }

          get("/api/datasets/" + id) {
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.localState should be(0)
            result.data.s3State should be(1)
          }

          changeStorageState(id, 0, 1)
          // s3のみに保存 => s3のみに保存
          put("/api/datasets/" + id + "/storage", Map("d" -> compact(render(("saveLocal" -> JBool(false)) ~ ("saveS3" -> JBool(true)))))) { checkStatus() }

          get("/api/datasets/" + id) {
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.localState should be(0)
            result.data.s3State should be(1)
          }

          changeStorageState(id, 0, 1)
          // s3のみに保存 => ローカルのみに保存
          put("/api/datasets/" + id + "/storage", Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> JBool(false)))))) { checkStatus() }

          get("/api/datasets/" + id) {
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.localState should be(2)
            result.data.s3State should be(3)
          }

          changeStorageState(id, 0, 1)
          // s3のみに保存 => ローカル、s3に保存
          put("/api/datasets/" + id + "/storage", Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> JBool(true)))))) { checkStatus() }

          get("/api/datasets/" + id) {
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.localState should be(2)
            result.data.s3State should be(1)
          }
        }
      }

      "保存先を変更できるか（ローカル・S3両方に保存がある場合）" in {
        session {
          signIn()
          val files = Map("file[]" -> dummyFile)
          val id = post("/api/datasets", Map.empty, files) {
            checkStatus()
            parse(body).extract[AjaxResponse[Dataset]].data.id
          }

          changeStorageState(id, 1, 1)
          // ローカル・S3両方に保存 => どちらにも保存しない(イレギュラー)
          put("/api/datasets/" + id + "/storage", Map("d" -> compact(render(("saveLocal" -> JBool(false)) ~ ("saveS3" -> JBool(false)))))) {
            status should be(200)
            val result = parse(body).extract[AjaxResponse[Any]]
            result.status should be("BadRequest")
          }

          get("/api/datasets/" + id) {
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.localState should be(1)
            result.data.s3State should be(1)
          }

          changeStorageState(id, 1, 1)
          // ローカル・S3両方に保存 => s3のみに保存
          put("/api/datasets/" + id + "/storage", Map("d" -> compact(render(("saveLocal" -> JBool(false)) ~ ("saveS3" -> JBool(true)))))) { checkStatus() }

          get("/api/datasets/" + id) {
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.localState should be(3)
            result.data.s3State should be(1)
          }

          changeStorageState(id, 1, 1)
          // ローカル・S3両方に保存 => ローカルのみに保存
          put("/api/datasets/" + id + "/storage", Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> JBool(false)))))) { checkStatus() }

          get("/api/datasets/" + id) {
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.localState should be(1)
            result.data.s3State should be(3)
          }

          changeStorageState(id, 1, 1)
          // ローカル・S3両方に保存 => ローカル、s3に保存
          put("/api/datasets/" + id + "/storage", Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> JBool(true)))))) { checkStatus() }

          get("/api/datasets/" + id) {
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.localState should be(1)
            result.data.s3State should be(1)
          }
        }
      }

      "データセットのタスクのステータスを取得できるか" in {
        session {
          signIn()
          val files = Map("file[]" -> dummyFile)
          val datasetId = post("/api/datasets", Map.empty, files) {
            checkStatus()
            parse(body).extract[AjaxResponse[Dataset]].data.id
          }
          val param = Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> JBool(true)))))
          val taskId = put("/api/datasets/" + datasetId + "/storage", param) {
            checkStatus()
            parse(body).extract[AjaxResponse[DatasetTask]].data.taskId
          }

          taskId shouldNot be("0")

          get("/api/tasks/" + taskId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[TaskStatus]]
            result.data.status should be(0)
          }
        }
      }

      "APIキーによるログインで操作できるか" in {
        val files = Map("file[]" -> dummyFile)
        val signature = getSignature("5dac067a4c91de87ee04db3e3c34034e84eb4a599165bcc9741bb9a91e8212cb", "dc9765e63b2b469a7bfb611fad8a10f2394d2b98b7a7105078356ec2a74164ea")
        val datasetId = post("/api/datasets", Map.empty, files, Map("Authorization" -> ("api_key=5dac067a4c91de87ee04db3e3c34034e84eb4a599165bcc9741bb9a91e8212cb, signature=" + signature))) {
          checkStatus()
          parse(body).extract[AjaxResponse[Dataset]].data.id
        }
      }
    }
  }

  private def changeStorageState(id: String, local: Int, s3: Int): Unit = {
    DB localTx { implicit s =>
      dsmoq.persistence.Dataset.find(id).get.copy(
        localState = local,
        s3State = s3
      ).save()
    }
  }
  private def flattenFilePath(file: File): List[File] = file match {
    case f:File if f.isDirectory => {
      def flatten(files: List[File]): List[File] = files match {
        case x :: xs if x.isDirectory => flatten(x.listFiles.toList) ::: flatten(xs)
        case x :: xs if x.isFile => x :: flatten(xs)
        case Nil => List()
      }
      flatten(f.listFiles.toList)
    }
    case f: File if f.isFile => List(f)
  }

  def getSignature(apiKey: String, secretKey: String): String = {
    val sk = new SecretKeySpec(secretKey.getBytes(), "HmacSHA1");
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(sk)
    val result = mac.doFinal((apiKey + "&" + secretKey).getBytes())
    URLEncoder.encode(Base64.getEncoder.encodeToString(result), "UTF-8")
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
    post("/api/datasets", Map.empty, files) {
      checkStatus()
      parse(body).extract[AjaxResponse[Dataset]].data.id
    }
  }
}
