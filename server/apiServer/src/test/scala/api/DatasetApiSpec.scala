package api

import java.io.File
import java.net.URLEncoder
import java.nio.file.Paths
import java.util.{ Base64, UUID }

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client

import common.DsmoqSpec
import dsmoq.AppConf
import dsmoq.controllers.AjaxResponse
import dsmoq.persistence.PostgresqlHelper._
import dsmoq.persistence.{ DefaultAccessLevel, GroupAccessLevel, GroupMemberRole, OwnerType, UserAccessLevel }
import dsmoq.services.UserAndGroupAccessLevel
import dsmoq.services.json.DatasetData.Dataset
import dsmoq.services.json.DatasetData.DatasetAddFiles
import dsmoq.services.json.DatasetData.DatasetAddImages
import dsmoq.services.json.DatasetData.DatasetAttribute
import dsmoq.services.json.DatasetData.DatasetDeleteImage
import dsmoq.services.json.DatasetData._
import dsmoq.services.json.GroupData.Group
import dsmoq.services.json.RangeSlice
import dsmoq.services.json.TaskData.TaskStatus
import scalikejdbc._

class DatasetApiSpec extends DsmoqSpec {
  private val dummyFile = new File("../README.md")
  private val dummyImage = new File("../../client/www/dummy/images/nagoya.jpg")
  private val dummyZipFile = new File("../testdata/test1.zip")
  private val testUserName = "dummy1"
  private val dummyUserName = "dummy4"
  private val testUserId = "023bfa40-e897-4dad-96db-9fd3cf001e79" // dummy1
  private val dummyUserId = "eb7a596d-e50c-483f-bbc7-50019eea64d7" // dummy 4

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
            result.data.id should be(datasetId)
          }
        }
      }

      "データセットが作成できるか(複数ファイル)" in {
        session {
          signIn()
          val files = Seq(("file[]", dummyFile), ("file[]", dummyFile))
          val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
          val (datasetId, fileIds) = post("/api/datasets", createParams, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            val datasetId = result.data.id
            val fileIds = result.data.files.map(_.id).sorted
            (datasetId, fileIds)
          }

          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.id should be(datasetId)
            result.data.filesCount should be(2)
            result.data.files.size should be(0)
            get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              fileIds.map { fileId =>
                assert(result.data.results.map(_.id).contains(fileId))
              }
            }
          }
        }
      }

      "作成したデータセットが削除できるか" in {
        session {
          signIn()
          val datasetId = createDataset()
          delete("/api/datasets/" + datasetId) { checkStatus() }
          get("/api/datasets/" + datasetId) {
            status should be(404)
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.status should be("NotFound")
          }
        }
      }

      "データセットの情報が編集できるか" in {
        session {
          signIn()
          val datasetId = createDataset()
          val params = Map(
            "d" -> compact(
              render(
                ("name" -> "変更後データセット") ~
                  ("description" -> "change description") ~
                  ("license" -> AppConf.defaultLicenseId)
              )
            )
          )
          put("/api/datasets/" + datasetId + "/metadata", params) { checkStatus() }
          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.meta.name should be("変更後データセット")
            result.data.meta.description should be("change description")
            result.data.meta.license should be(AppConf.defaultLicenseId)
          }
        }
      }

      "データセットの情報が編集できるか(attribute込み)" in {
        session {
          signIn()
          val datasetId = createDataset()
          val attributes = Seq(
            ("name" -> "attr_name") ~ ("value" -> "attr_value"),
            ("name" -> "attr_another_name") ~ ("value" -> "attr_another_value")
          )
          val params = Map(
            "d" -> compact(
              render(
                ("name" -> "変更後データセット") ~
                  ("description" -> "change description") ~
                  ("license" -> AppConf.defaultLicenseId) ~
                  ("attributes" -> attributes)
              )
            )
          )
          put("/api/datasets/" + datasetId + "/metadata", params) { checkStatus() }
          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.meta.name should be("変更後データセット")
            result.data.meta.description should be("change description")
            result.data.meta.license should be(AppConf.defaultLicenseId)
            result.data.meta.attributes.map(_.name).contains("attr_name")
            result.data.meta.attributes.map(_.name).contains("attr_another_name")
            result.data.meta.attributes.map(_.value).contains("attr_value")
            result.data.meta.attributes.map(_.value).contains("attr_another_value")
          }
        }
      }

      "dataset attribute import" in {
        for {
          file <- Seq(
            "../README.md", "empty", "attr.csv", "attr_jis.csv", "attr_eucjp.csv", "attr_sjis.csv",
            "attr_utf8.csv", "attr_utf16be.csv", "attr_utf16le.csv"
          ).map(x => new File(s"../testdata/${x}"))
          permission <- Seq(true, false)
          dataset <- Seq(true, false)
        } {
          withClue(s"file: ${file}, permission: ${permission}, dataset: ${dataset}") {
            val datasetId = if (dataset) {
              session {
                signIn()
                createDataset()
              }
            } else {
              UUID.randomUUID.toString
            }
            session {
              signIn(id = if (permission) "dummy1" else "dummy2")
              post(s"/api/datasets/${datasetId}/attributes/import", params = Map.empty, files = Map("file" -> file)) {
                if (file.getName == "empty") {
                  status should be(400)
                  val result = parse(body).extract[AjaxResponse[Any]]
                  result.status should be("Illegal Argument")
                } else if (!dataset) {
                  status should be(404)
                  val result = parse(body).extract[AjaxResponse[Any]]
                  result.status should be("NotFound")
                } else if (!permission) {
                  status should be(403)
                  val result = parse(body).extract[AjaxResponse[Any]]
                  result.status should be("AccessDenied")
                } else {
                  checkStatus()
                  get("/api/datasets/" + datasetId) {
                    checkStatus()
                    val result = parse(body).extract[AjaxResponse[Dataset]]
                    if (file.getName == "attr.csv") {
                      val attrs = Seq(
                        DatasetAttribute("abc", "def"),
                        DatasetAttribute("abc", "xyz"),
                        DatasetAttribute("test", "$tag")
                      )
                      // 順番保障はしていないため、登録したattributeを含むかどうかで判断
                      // result.data.meta.attributes should be(attrs)
                      result.data.meta.attributes.foreach(f => {
                        (f.name, f.value) match {
                          case ("abc", "def") => // Success
                          case ("abc", "xyz") => // Success
                          case ("test", "$tag") => // Success
                          case _ => fail()
                        }
                      })

                    } else if (file.getName.startsWith("attr_")) {
                      val attrs = Seq(
                        DatasetAttribute("abc", "def"),
                        DatasetAttribute("abc", "xyz"),
                        DatasetAttribute("test", "$tag"),
                        DatasetAttribute("あいう", "えお"),
                        DatasetAttribute("属性", "値"),
                        DatasetAttribute("タグ", "$tag"),
                        DatasetAttribute("表予申能十ソ", "表予申能十ソ")
                      )
                      // 順番保障はしていないため、登録したattributeを含むかどうかで判断
                      // result.data.meta.attributes should be(attrs)
                      result.data.meta.attributes.foreach(f => {
                        (f.name, f.value) match {
                          case ("abc", "def") => // Success
                          case ("abc", "xyz") => // Success
                          case ("test", "$tag") => // Success
                          case ("あいう", "えお") => // Success
                          case ("属性", "値") => // Success
                          case ("タグ", "$tag") => // Success
                          case ("表予申能十ソ", "表予申能十ソ") => // Success
                          case _ => fail()
                        }
                      })
                    }
                  }
                }
              }
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
            result.data.files.size should be(0)
            get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              assert(result.data.results.map(_.id).contains(fileId))
            }
          }
        }
      }

      "データセットに複数ファイルが追加できるか" in {
        session {
          signIn()
          val datasetId = createDataset()
          val files = Seq(
            ("files[]", dummyFile),
            ("files[]", dummyFile)
          )
          val fileIds = post("/api/datasets/" + datasetId + "/files", Map.empty, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetAddFiles]]
            result.data.files.map(_.id)
          }

          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.filesCount should be(3)
            result.data.files.size should be(0)
            get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              fileIds.map { x =>
                assert(result.data.results.map(x => x.id).contains(x))
              }
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
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetFile]]
            result.data.id should be(fileId)
            result.data.url.get
          }

          get(new java.net.URI(url).getPath) {
            status should be(200)
          }

          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.filesCount should be(2)
            // IDの有無をチェック後、付随するデータのチェック
            get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              assert(result.data.results.map(_.id).contains(fileId))
              result.data.results.foreach { x =>
                if (x.id == fileId) {
                  x.size should be(Some(anotherFile.length))
                }
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
            get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              assert(result.data.results.map(_.id).contains(fileId))
            }
            result.data.files.foreach { x =>
              if (x.id == fileId) {
                x.name should be("testtest.txt")
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

          // add file (x3)
          val files = Map("files[]" -> dummyFile)
          post("/api/datasets/" + datasetId + "/files", Map.empty, files) { checkStatus() }
          val fileId = post("/api/datasets/" + datasetId + "/files", Map.empty, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetAddFiles]]
            result.data.files(0).id
          }
          post("/api/datasets/" + datasetId + "/files", Map.empty, files) { checkStatus() }

          delete("/api/datasets/" + datasetId + "/files/" + fileId) { checkStatus() }
          withClue("get datasets after delete file") {
            get("/api/datasets/" + datasetId) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.filesCount should be(3)
              get(s"/api/datasets/${datasetId}/files") {
                val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
                assert(!result.data.results.map(_.id).contains(fileId))
              }
            }
          }
          withClue("get file info after delete file") {
            get("/api/datasets/" + datasetId + "/files/" + fileId) {
              status should be(404)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("NotFound")
            }
          }
          withClue("get file after delete file") {
            get("/files/" + datasetId + "/" + fileId) {
              status should be(404)
              body should be("Not Found")
            }
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
            result.data.primaryImage should not be (imageId)
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
          val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
          val url = post("/api/datasets", createParams, files) {
            checkStatus()
            parse(body).extract[AjaxResponse[Dataset]].data.files(0).url.get
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

      "データセットACLアイテム 存在しないID" in {
        val uuid = UUID.randomUUID.toString
        session {
          signIn()
          val datasetId = createDataset()
          for {
            ownerType <- Seq(OwnerType.User, OwnerType.Group)
          } {
            val params = Map(
              "d" ->
                compact(
                  render(
                    Seq(
                      ("id" -> uuid) ~
                        ("ownerType" -> JInt(ownerType)) ~
                        ("accessLevel" -> JInt(UserAndGroupAccessLevel.OWNER_OR_PROVIDER))
                    )
                  )
                )
            )
            post("/api/datasets/" + datasetId + "/acl", params) {
              status should be(400)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("BadRequest")
            }
          }
        }
      }

      "データセットACLアイテム アクセスレベル設定したデータが閲覧できるか(ユーザー)" in {
        session {
          // データセット作成
          signIn()
          val datasetId = createDataset()

          // アクセスレベル設定(ユーザー)
          val params = Map(
            "d" ->
              compact(
                render(
                  Seq(
                    ("id" -> dummyUserId) ~
                      ("ownerType" -> JInt(OwnerType.User)) ~
                      ("accessLevel" -> JInt(UserAccessLevel.FullPublic))
                  )
                )
              )
          )
          post("/api/datasets/" + datasetId + "/acl", params) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
            assert(result.data.map(_.id) contains (dummyUserId))
          }
          post("/api/signout") { checkStatus() }

          signIn("dummy4")
          // 一覧検索
          get("/api/datasets") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(1)
            assert(result.data.results.map(_.id).contains(datasetId))
          }
          // 詳細検索
          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.id should be(datasetId)
            assert(result.data.ownerships.filter(_.ownerType == OwnerType.User).map(_.id).contains(dummyUserId))
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
          signIn("dummy4")
          val groupName = "groupName" + UUID.randomUUID().toString
          val createGroupParams = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "group description"))))
          val groupId = post("/api/groups", createGroupParams) {
            checkStatus()
            parse(body).extract[AjaxResponse[Group]].data.id
          }
          post("/api/signout") { checkStatus() }

          // アクセスレベル設定(グループ)
          signIn()
          val params = Map(
            "d" -> compact(
              render(
                Seq(
                  ("id" -> groupId) ~
                    ("ownerType" -> JInt(OwnerType.Group)) ~
                    ("accessLevel" -> JInt(GroupAccessLevel.FullPublic))
                )
              )
            )
          )
          post("/api/datasets/" + datasetId + "/acl", params) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
            assert(result.data.map(_.id) contains (groupId))
          }
          post("/api/signout") { checkStatus() }

          // アクセスレベルを設定したdatasetはそのユーザー(グループ)から参照できるはず
          signIn("dummy4")
          // 一覧検索
          get("/api/datasets") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(1)
            assert(result.data.results.map(_.id).contains(datasetId))
          }
          // 詳細検索
          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.id should be(datasetId)
            assert(result.data.ownerships.filter(_.ownerType == OwnerType.Group).map(_.name).contains(groupName))
          }
        }
      }

      "データセットACLアイテム アクセスレベル設定したデータが閲覧不可になっているか(グループから削除したメンバー)" in {
        session {
          // データセット作成
          signIn()
          val datasetId = createDataset()
          post("/api/signout") { checkStatus() }

          // アクセスレベル設定対象のグループを作成
          signIn("dummy4")
          val groupName = "groupName" + UUID.randomUUID().toString
          val createGroupParams = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "group description"))))
          val groupId = post("/api/groups", createGroupParams) {
            checkStatus()
            parse(body).extract[AjaxResponse[Group]].data.id
          }
          // メンバー追加(dummy3)
          val dummyAddUserUUID = "4aaefd45-2fe5-4ce0-b156-3141613f69a6" // dummy3
          post(
            s"/api/groups/${groupId}/members",
            Map("d" -> compact(render(Seq(("userId" -> dummyAddUserUUID) ~ ("role" -> JInt(GroupMemberRole.Member))))))
          ) { checkStatus() }

          post("/api/signout") { checkStatus() }

          // アクセスレベル設定(グループ)
          signIn()
          val params = Map(
            "d" -> compact(
              render(
                Seq(
                  ("id" -> groupId) ~
                    ("ownerType" -> JInt(OwnerType.Group)) ~
                    ("accessLevel" -> JInt(GroupAccessLevel.FullPublic))
                )
              )
            )
          )
          post("/api/datasets/" + datasetId + "/acl", params) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
            assert(result.data.map(_.id) contains (groupId))
          }
          post("/api/signout") { checkStatus() }

          // アクセスレベルを設定したdatasetはそのユーザー(グループ)から参照できるはず
          signIn("dummy3")

          // 一覧検索
          get("/api/datasets") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(1)
            assert(result.data.results.map(_.id).contains(datasetId))
          }
          // 詳細検索
          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.id should be(datasetId)
            assert(result.data.ownerships.filter(_.ownerType == OwnerType.Group).map(_.name).contains(groupName))
          }

          // サインアウト
          post("/api/signout") { checkStatus() }

          // グループ管理者でサインインして、追加ユーザーを削除する
          signIn("dummy4")
          delete(s"/api/groups/${groupId}/members/${dummyAddUserUUID}") { checkStatus() }

          // サインアウト
          post("/api/signout") { checkStatus() }

          // 削除前にグループに追加したユーザーが参照できるか
          signIn("dummy3")

          // 一覧検索
          get("/api/datasets") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(0)
          }
          // 詳細検索
          get("/api/datasets/" + datasetId) {
            checkStatus(403, Some("AccessDenied"))
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
          signIn("dummy4")
          val groupName = "group name" + UUID.randomUUID().toString
          val createGroupParams = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "group description"))))
          val groupId = post("/api/groups", createGroupParams) {
            checkStatus()
            parse(body).extract[AjaxResponse[Group]].data.id
          }
          post("/api/signout") { checkStatus() }

          // アクセスレベル設定
          signIn()
          val params = Map(
            "d" -> compact(
              render(
                Seq(
                  ("id" -> groupId) ~
                    ("ownerType" -> JInt(OwnerType.Group)) ~
                    ("accessLevel" -> JInt(GroupAccessLevel.FullPublic))
                )
              )
            )
          )
          post("/api/datasets/" + datasetId + "/acl", params) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
            assert(result.data.map(_.id) contains (groupId))
          }
          post("/api/signout") { checkStatus() }

          // アクセスレベルを設定したdatasetはグループから参照できるはず
          signIn("dummy4")
          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.id should be(datasetId)
          }
          post("/api/signout") { checkStatus() }

          // アクセスレベル解除
          signIn()
          val deleteParams = Map(
            "d" -> compact(
              render(
                Seq(
                  ("id" -> groupId) ~
                    ("ownerType" -> JInt(OwnerType.Group)) ~
                    ("accessLevel" -> JInt(GroupAccessLevel.Deny))
                )
              )
            )
          )
          post("/api/datasets/" + datasetId + "/acl", deleteParams) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
            assert(result.data.map(_.id) contains (groupId))
          }
          post("/api/signout") { checkStatus() }

          // アクセスレベルを解除したdatasetはグループから見えなくなるはず
          signIn("dummy4")
          get("/api/datasets/" + datasetId) {
            status should be(403)
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.status should be("AccessDenied")
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
            status should be(403)
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.status should be("AccessDenied")
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

          val groupAccessLevels = Map(
            "d" -> compact(
              render(
                Seq(
                  ("id" -> providerGroupId) ~ ("ownerType" -> JInt(OwnerType.Group)) ~ ("accessLevel" -> JInt(GroupAccessLevel.Provider)),
                  ("id" -> fullPublicGroupId) ~ ("ownerType" -> JInt(OwnerType.Group)) ~ ("accessLevel" -> JInt(GroupAccessLevel.FullPublic)),
                  ("id" -> limitedReadGroupId) ~ ("ownerType" -> JInt(OwnerType.Group)) ~ ("accessLevel" -> JInt(GroupAccessLevel.LimitedPublic))
                )
              )
            )
          )
          post("/api/datasets/" + datasetId + "/acl", groupAccessLevels) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
            assert(result.data.map(_.id) contains (providerGroupId))
            assert(result.data.map(_.id) contains (fullPublicGroupId))
            assert(result.data.map(_.id) contains (limitedReadGroupId))
          }

          // 3ユーザーそれぞれに権限付与
          val ownerUserId = "eb7a596d-e50c-483f-bbc7-50019eea64d7"
          val fullPublicUserId = "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04"
          val limitedReadUserId = "4aaefd45-2fe5-4ce0-b156-3141613f69a6"
          val userAccessLevels = Map(
            "d" -> compact(
              render(
                Seq(
                  ("id" -> ownerUserId) ~ ("ownerType" -> JInt(OwnerType.User)) ~ ("accessLevel" -> JInt(UserAccessLevel.Owner)),
                  ("id" -> fullPublicUserId) ~ ("ownerType" -> JInt(OwnerType.User)) ~ ("accessLevel" -> JInt(UserAccessLevel.FullPublic)),
                  ("id" -> limitedReadUserId) ~ ("ownerType" -> JInt(OwnerType.User)) ~ ("accessLevel" -> JInt(UserAccessLevel.LimitedRead))
                )
              )
            )
          )
          post("/api/datasets/" + datasetId + "/acl", userAccessLevels) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
            assert(result.data.map(_.id) contains (ownerUserId))
            assert(result.data.map(_.id) contains (fullPublicUserId))
            assert(result.data.map(_.id) contains (limitedReadUserId))
          }

          // データセット取得 結果のソート確認
          get("/api/datasets/" + datasetId) {
            status should be(200)
            val result = parse(body).extract[AjaxResponse[Dataset]].data.ownerships

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
          val params = Map("saveLocal" -> "false", "saveS3" -> "true", "name" -> "test1")
          val url = post("/api/datasets", params, files) {
            checkStatus()
            val dataset = parse(body).extract[AjaxResponse[Dataset]]
            dataset.data.s3State should be(2)
            dataset.data.localState should be(3)
            dataset.data.files(0).url.get
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
          val params = Map("saveLocal" -> "false", "saveS3" -> "true", "name" -> "test1")
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
          for (file <- localFiles) {
            val separator = if (System.getProperty("file.separator") == "\\") { System.getProperty("file.separator") * 2 } else { System.getProperty("file.separator") }
            val filePath = file.getCanonicalPath.split(separator).reverse.take(4).reverse.mkString("/")
            client.putObject(AppConf.s3UploadRoot, filePath, file)
          }
          changeStorageState(data.id, 0, 1)

          val uri = new java.net.URI(data.files(0).url.get)
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
          val params = Map("saveLocal" -> "true", "saveS3" -> "true", "name" -> "test1")
          val url = post("/api/datasets", params, files) {
            checkStatus()
            val dataset = parse(body).extract[AjaxResponse[Dataset]]
            dataset.data.s3State should be(2)
            dataset.data.localState should be(1)
            dataset.data.files(0).url.get
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
          val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
          val id = post("/api/datasets", createParams, files) {
            checkStatus()
            parse(body).extract[AjaxResponse[Dataset]].data.id
          }

          changeStorageState(id, 1, 0)
          // ローカルのみに保存 => どちらにも保存しない(イレギュラー)
          put("/api/datasets/" + id + "/storage", Map("d" -> compact(render(("saveLocal" -> JBool(false)) ~ ("saveS3" -> JBool(false)))))) {
            status should be(400)
            val result = parse(body).extract[AjaxResponse[Any]]
            result.status should be("Illegal Argument")
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
          val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
          val id = post("/api/datasets", createParams, files) {
            checkStatus()
            parse(body).extract[AjaxResponse[Dataset]].data.id
          }

          changeStorageState(id, 0, 1)
          // s3のみに保存 => どちらにも保存しない(イレギュラー)
          put("/api/datasets/" + id + "/storage", Map("d" -> compact(render(("saveLocal" -> JBool(false)) ~ ("saveS3" -> JBool(false)))))) {
            status should be(400)
            val result = parse(body).extract[AjaxResponse[Any]]
            result.status should be("Illegal Argument")
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
          val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
          val id = post("/api/datasets", createParams, files) {
            checkStatus()
            parse(body).extract[AjaxResponse[Dataset]].data.id
          }

          changeStorageState(id, 1, 1)
          // ローカル・S3両方に保存 => どちらにも保存しない(イレギュラー)
          put("/api/datasets/" + id + "/storage", Map("d" -> compact(render(("saveLocal" -> JBool(false)) ~ ("saveS3" -> JBool(false)))))) {
            status should be(400)
            val result = parse(body).extract[AjaxResponse[Any]]
            result.status should be("Illegal Argument")
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
          val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
          val datasetId = post("/api/datasets", createParams, files) {
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
        val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
        val signature = getSignature("5dac067a4c91de87ee04db3e3c34034e84eb4a599165bcc9741bb9a91e8212cb", "dc9765e63b2b469a7bfb611fad8a10f2394d2b98b7a7105078356ec2a74164ea")
        val datasetId = post("/api/datasets", createParams, files, Map("Authorization" -> ("api_key=5dac067a4c91de87ee04db3e3c34034e84eb4a599165bcc9741bb9a91e8212cb, signature=" + signature))) {
          checkStatus()
          parse(body).extract[AjaxResponse[Dataset]].data.id
        }
      }

      "ZIP内ファイル一覧はファイル名の昇順になっているか" in {
        session {
          signIn()
          val datasetId = createDataset()
          val validZipFile = new File("../testdata/valid_zip.zip")
          val dummyFile1 = new File("../testdata/test1.csv")
          val dummyFile2 = new File("../testdata/test1.zip")
          val dummyFile3 = new File("../testdata/test2.csv")
          val dummyFile4 = new File("../testdata/test2.zip")
          val dummyFile5 = new File("../testdata/test3.csv")
          val dummyFile6 = new File("../testdata/test3.zip")
          val files = Seq(
            ("files[]", validZipFile)
          )
          val fileIds = post("/api/datasets/" + datasetId + "/files", Map.empty, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetAddFiles]]
            result.data.files.map(_.id)
          }

          get(s"/api/datasets/${datasetId}/files") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
            result.data.summary.count should be(2)

            val fileId = result.data.results.filter(v => v.name.equals(validZipFile.getName)).head.id
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.data.summary.count should be(6)
              result.data.results.size should be(6)

              // ファイル名の昇順ソートになっているか確認する
              result.data.results(0).name should be(dummyFile1.getName)
              result.data.results(1).name should be(dummyFile2.getName)
              result.data.results(2).name should be(dummyFile3.getName)
              result.data.results(3).name should be(dummyFile4.getName)
              result.data.results(4).name should be(dummyFile5.getName)
              result.data.results(5).name should be(dummyFile6.getName)
            }
          }
        }
      }

      "unzipできないファイル一覧を取得できるか" in {
        session {
          signIn()
          val datasetId = createDataset()
          val validZipFile = new File("../testdata/valid_zip.zip")
          val invalidZipFile = new File("../testdata/invalid_zip.zip")
          val files = Seq(
            ("files[]", validZipFile),
            ("files[]", invalidZipFile)
          )
          val fileIds = post("/api/datasets/" + datasetId + "/files", Map.empty, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetAddFiles]]
            result.data.files.map(_.id)
          }

          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.filesCount should be(3)
            result.data.files.size should be(0)

            get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).name should be(dummyFile.getName)
              result.data.results(1).name should be(invalidZipFile.getName)
              result.data.results(2).name should be(validZipFile.getName)
            }
            get(s"/api/datasets/${datasetId}/file_errors") {
              val result = parse(body).extract[AjaxResponse[Seq[DatasetFile]]]
              result.data.size should be(1)
              result.data(0).name should be(invalidZipFile.getName)
            }
          }
        }
      }

      "ZIP拡張子を適切に判別しているか" in {
        session {
          signIn()
          val datasetId = createDataset()
          val validExtFile = new File("../testdata/valid_extension.zip")
          val invalidExtFile = new File("../testdata/invalid_extension.zzip")
          val files = Seq(
            ("files[]", validExtFile),
            ("files[]", invalidExtFile)
          )
          val fileIds = post("/api/datasets/" + datasetId + "/files", Map.empty, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetAddFiles]]
            result.data.files.map(_.id)
          }

          get(s"/api/datasets/${datasetId}/files") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
            result.data.summary.count should be(3)

            val invalidResult = result.data.results.filter(v => v.name.equals(invalidExtFile.getName)).head
            invalidResult.isZip should be(false)

            val fileId = result.data.results.filter(v => v.name.equals(validExtFile.getName)).head.id
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.data.summary.count should be(1)
              result.data.results.size should be(1)

              result.data.results(0).name should be("README1.md")
            }
          }
        }
      }
    }

    "SearchDatasetList" - {
      "データセット名が部分一致するデータセットを検索できるか" in {
        session {
          signIn()
          // データセットを3件作成、1件のみ情報変更
          createDataset()
          createDataset()
          val datasetId = createDataset()
          val modifyParams = Map(
            "d" -> compact(
              render(
                ("name" -> "変更後データセット") ~
                  ("description" -> "change description") ~
                  ("license" -> AppConf.defaultLicenseId)
              )
            )
          )
          put("/api/datasets/" + datasetId + "/metadata", modifyParams) {
            checkStatus()
          }

          // 情報変更したデータセットが検索可能か(部分一致検索)
          var searchParmas = Map("d" -> compact(render(("query" -> "変更後"))))
          get("/api/datasets", searchParmas) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(1)
            assert(result.data.results.map(_.id).contains(datasetId))
            result.data.results.find(_.id == datasetId).get.name should be("変更後データセット")
          }
        }
      }

      "データセット詳細が部分一致するデータセットを検索できるか" in {
        session {
          signIn()
          // データセットを3件作成、1件のみ情報変更
          createDataset()
          createDataset()
          val datasetId = createDataset()
          val modifyParams = Map(
            "d" -> compact(
              render(
                ("name" -> "変更後データセット") ~
                  ("description" -> "<p>詳細情報変更しました</p>") ~
                  ("license" -> AppConf.defaultLicenseId)
              )
            )
          )
          put("/api/datasets/" + datasetId + "/metadata", modifyParams) {
            checkStatus()
          }

          // 情報変更したデータセットが検索可能か(部分一致検索)
          var searchParmas = Map("d" -> compact(render(("query" -> "詳細情報"))))
          get("/api/datasets", searchParmas) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(1)
            assert(result.data.results.map(_.id).contains(datasetId))
            result.data.results.find(_.id == datasetId).get.description should be("<p>詳細情報変更しました</p>")
          }
        }
      }

      "データセット名またはデータセット詳細が部分一致するデータセットをすべて検索できるか" in {
        session {
          signIn()
          // データセットを3件作成、2件情報変更
          createDataset()
          val nameChangeDatasetId = createDataset()
          val descriptionChangeDatasetId = createDataset()
          val searchText = "検索用テキスト"

          // 変更するデータ片方のname, もう片方のdescriptionに同じ文字が入るようにする
          val name = searchText + UUID.randomUUID
          val modifyParams = Map(
            "d" -> compact(
              render(
                ("name" -> name) ~
                  ("description" -> "dummy description") ~
                  ("license" -> AppConf.defaultLicenseId)
              )
            )
          )
          put("/api/datasets/" + nameChangeDatasetId + "/metadata", modifyParams) {
            checkStatus()
          }

          val description = "<p>" + UUID.randomUUID + searchText + "</p>"
          val modifyParams2 = Map(
            "d" -> compact(
              render(
                ("name" -> "dummy name") ~
                  ("description" -> description) ~
                  ("license" -> AppConf.defaultLicenseId)
              )
            )
          )
          put("/api/datasets/" + descriptionChangeDatasetId + "/metadata", modifyParams2) {
            checkStatus()
          }

          // 情報変更したデータセットが検索可能か(部分一致検索)
          var searchParmas = Map("d" -> compact(render(("query" -> searchText))))
          get("/api/datasets", searchParmas) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(2)
            assert(result.data.results.map(_.id).contains(nameChangeDatasetId))
            assert(result.data.results.map(_.id).contains(descriptionChangeDatasetId))
            result.data.results.find(_.id == nameChangeDatasetId).get.name should be(name)
            result.data.results.find(_.id == descriptionChangeDatasetId).get.description should be(description)
          }
        }
      }

      "ファイル名の部分一致でデータセットを検索できるか" in {
        session {
          // データセットを2つ作成、片方にファイルを追加
          signIn()
          createDataset()
          val datasetId = createDataset()
          val files = Map("files[]" -> dummyImage)
          val fileId = post("/api/datasets/" + datasetId + "/files", Map.empty, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetAddFiles]]
            result.data.files(0).id
          }

          // 初期検索結果は2件
          get("/api/datasets") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(2)
            assert(result.data.results.map(_.id).contains(datasetId))
          }

          // ファイル名でデータセットが検索可能か(部分一致検索)
          var searchParmas = Map(
            "d" -> compact(render(("query" -> dummyImage.getName.substring(0, 6))))
          )
          get("/api/datasets", searchParmas) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(1)
            assert(result.data.results.map(_.id).contains(datasetId))
          }
        }
      }

      "ZIPファイル中のファイル名の部分一致でデータセットを検索できるか" in {
        session {
          // データセットを2つ作成、片方にZIPファイルを追加
          signIn()
          createDataset()
          val datasetId = createDataset()
          val files = Map("files[]" -> new File("../testdata/test2.zip"))
          val fileId = post("/api/datasets/" + datasetId + "/files", Map.empty, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[DatasetAddFiles]]
            result.data.files(0).id
          }

          // 初期検索結果は2件
          get("/api/datasets") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(2)
            assert(result.data.results.map(_.id).contains(datasetId))
          }

          // ZIPファイル中のファイル名でデータセットが検索可能か(部分一致検索)
          var searchParmas = Map("d" -> compact(render(("query" -> "test5.txt"))))
          get("/api/datasets", searchParmas) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(1)
            assert(result.data.results.map(_.id).contains(datasetId))
          }
        }
      }

      "ユーザーを指定してデータセットを検索できるか" in {
        // 2つのユーザーでデータセットを1件ずつ作成
        session {
          // testUser (dummy1)
          signIn()
          val datasetId = createDataset()
          post("/api/signout") {
            checkStatus()
          }

          // dummyUser (dummy4)
          // 片方のデータセットは2人にOnwer権限を与える
          signIn("dummy4")
          val twoOwnersDatasetId = createDataset()
          val aclParams = Map(
            "d" -> compact(
              render(Seq(
                ("id" -> testUserId) ~
                  ("ownerType" -> JInt(OwnerType.User)) ~
                  ("accessLevel" -> JInt(UserAccessLevel.Owner))
              ))
            )
          )
          post("/api/datasets/" + twoOwnersDatasetId + "/acl", aclParams) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
            assert(result.data.map(_.id) contains (testUserId))
          }
          post("/api/signout") {
            checkStatus()
          }

          signIn()
          // 初期検索結果は2件
          get("/api/datasets") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(2)
            assert(result.data.results.map(_.id).contains(datasetId))
            assert(result.data.results.map(_.id).contains(twoOwnersDatasetId))
            assert(result.data.results.find(_.id == datasetId).get.ownerships.filter(_.ownerType == OwnerType.User).map(_.name).contains(testUserName))
            val ownerships = result.data.results.find(_.id == twoOwnersDatasetId).get.ownerships.filter(_.ownerType == OwnerType.User)
            assert(ownerships.map(_.name).contains(testUserName))
            assert(ownerships.map(_.name).contains(dummyUserName))
          }

          // ユーザーを指定して検索可能か
          var searchParmas = Map("d" -> compact(render(("owners" -> Seq(testUserName)))))
          get("/api/datasets", searchParmas) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(2)
            assert(result.data.results.map(_.id).contains(datasetId))
            assert(result.data.results.map(_.id).contains(twoOwnersDatasetId))
            assert(result.data.results.find(_.id == datasetId).get.ownerships.filter(_.ownerType == OwnerType.User).map(_.name).contains(testUserName))
            val ownerships = result.data.results.find(_.id == twoOwnersDatasetId).get.ownerships.filter(_.ownerType == OwnerType.User)
            assert(ownerships.map(_.name).contains(testUserName))
            assert(ownerships.map(_.name).contains(dummyUserName))
          }

          // 複数のユーザーを指定して検索可能か(AND検索)
          var searchParmas2 = Map("d" -> compact(render(("owners" -> Seq(dummyUserName, testUserName)))))
          get("/api/datasets", searchParmas2) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(1)
            assert(result.data.results.map(_.id).contains(twoOwnersDatasetId))
            val ownerships = result.data.results.find(_.id == twoOwnersDatasetId).get.ownerships.filter(_.ownerType == OwnerType.User)
            assert(ownerships.map(_.name).contains(testUserName))
            assert(ownerships.map(_.name).contains(dummyUserName))
          }
        }
      }

      "グループアクセス権限がProviderのデータセットを検索できるか" in {
        // 2つのユーザーでグループとデータセットを1つずつ作成し、データセットはグループにProvider権限を与える
        session {
          get("/api/datasets") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(0)
          }

          // testUser (dummy1)
          signIn()
          get("/api/datasets") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(0)
          }
          val datasetId = createDataset()
          val groupName = "groupName" + UUID.randomUUID().toString
          val createGroupParams = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "group description"))))
          val groupId = post("/api/groups", createGroupParams) {
            checkStatus()
            parse(body).extract[AjaxResponse[Group]].data.id
          }
          val groupAclParams = Map(
            "d" -> compact(
              render(
                Seq(
                  ("id" -> groupId) ~
                    ("ownerType" -> JInt(OwnerType.Group)) ~
                    ("accessLevel" -> JInt(GroupAccessLevel.Provider))
                )
              )
            )
          )
          post("/api/datasets/" + datasetId + "/acl", groupAclParams) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
            assert(result.data.map(_.id) contains (groupId))
          }
          get("/api/datasets") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(1)
          }
          post("/api/signout") {
            checkStatus()
          }

          // dummyUser (dummy4)
          // 片方のデータセットは2つのGroupにProvider権限を与える
          signIn("dummy4")
          val twoGroupsDatasetId = createDataset()
          val anotherGroupName = "groupName" + UUID.randomUUID().toString
          val createGroupParams2 = Map("d" -> compact(render(("name" -> anotherGroupName) ~ ("description" -> "group description"))))
          val anotherGroupId = post("/api/groups", createGroupParams2) {
            checkStatus()
            parse(body).extract[AjaxResponse[Group]].data.id
          }
          val anotherGroupAclParams = Map(
            "d" -> compact(
              render(
                Seq(
                  ("id" -> anotherGroupId) ~
                    ("ownerType" -> JInt(OwnerType.Group)) ~
                    ("accessLevel" -> JInt(GroupAccessLevel.Provider))
                )
              )
            )
          )
          post("/api/datasets/" + twoGroupsDatasetId + "/acl", anotherGroupAclParams) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
            assert(result.data.map(_.id) contains (anotherGroupId))
          }
          post("/api/datasets/" + twoGroupsDatasetId + "/acl", groupAclParams) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
            assert(result.data.map(_.id) contains (groupId))
          }
          post("/api/signout") {
            checkStatus()
          }

          signIn()
          // 初期検索結果は2件
          get("/api/datasets") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(2)
            assert(result.data.results.map(_.id).contains(datasetId))
            assert(result.data.results.map(_.id).contains(twoGroupsDatasetId))
            assert(result.data.results.find(_.id == datasetId).get.ownerships.filter(_.ownerType == OwnerType.Group).map(_.id).contains(groupId))
            val ownerships = result.data.results.find(_.id == twoGroupsDatasetId).get.ownerships.filter(_.ownerType == OwnerType.Group)
            assert(ownerships.map(_.id).contains(groupId))
            assert(ownerships.map(_.id).contains(anotherGroupId))
          }

          // グループを指定して検索可能か
          var searchParmas = Map("d" -> compact(render(("groups" -> Seq(groupName)))))
          get("/api/datasets", searchParmas) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(2)
            assert(result.data.results.map(_.id).contains(datasetId))
            assert(result.data.results.map(_.id).contains(twoGroupsDatasetId))
            assert(result.data.results.find(_.id == datasetId).get.ownerships.filter(_.ownerType == OwnerType.Group).map(_.id).contains(groupId))
            val ownerships = result.data.results.find(_.id == twoGroupsDatasetId).get.ownerships.filter(_.ownerType == OwnerType.Group)
            assert(ownerships.map(_.id).contains(groupId))
            assert(ownerships.map(_.id).contains(anotherGroupId))
          }

          // 複数のグループを指定して検索可能か(AND検索)
          var searchParmas2 = Map("d" -> compact(render(("groups" -> Seq(groupName, anotherGroupName)))))
          get("/api/datasets", searchParmas2) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(1)
            assert(result.data.results.map(_.id).contains(twoGroupsDatasetId))
            val ownerships = result.data.results.find(_.id == twoGroupsDatasetId).get.ownerships.filter(_.ownerType == OwnerType.Group)
            assert(ownerships.map(_.id).contains(groupId))
            assert(ownerships.map(_.id).contains(anotherGroupId))
          }
        }
      }

      "attributeを指定してデータセットを検索できるか" in {
        session {
          // datasetを3つ作成、1つはattributeなし、1つはattribute1つ、1つはattribute2つ
          signIn()
          val datasetId = createDataset()
          val oneAttrDatasetId = createDataset()
          val twoAttrDatasetId = createDataset()
          val attribute1 = ("name" -> "hoge") ~ ("value" -> "piyo")
          val attribute2 = ("name" -> "foo") ~ ("value" -> "bar")
          val metadataParams1 = Map(
            "d" -> compact(
              render(
                ("name" -> "change name") ~
                  ("description" -> "change description") ~
                  ("license" -> AppConf.defaultLicenseId) ~
                  ("attributes" -> Seq(attribute1))
              )
            )
          )
          put("/api/datasets/" + oneAttrDatasetId + "/metadata", metadataParams1) {
            checkStatus()
          }
          val metadataParams2 = Map(
            "d" -> compact(
              render(
                ("name" -> "change name2") ~
                  ("description" -> "change description2") ~
                  ("license" -> AppConf.defaultLicenseId) ~
                  ("attributes" -> Seq(attribute1, attribute2))
              )
            )
          )
          put("/api/datasets/" + twoAttrDatasetId + "/metadata", metadataParams2) {
            checkStatus()
          }

          // 初期検索結果は3件
          get("/api/datasets") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(3)
            assert(result.data.results.map(_.id).contains(datasetId))
            assert(result.data.results.map(_.id).contains(oneAttrDatasetId))
            assert(result.data.results.map(_.id).contains(twoAttrDatasetId))
          }

          // attribute(nameのみ)を指定して検索可能か
          val searchParmas = Map(
            "d" -> compact(
              render(
                ("attributes" -> Seq(("name" -> "hoge") ~ ("value" -> "")))
              )
            )
          )
          get("/api/datasets", searchParmas) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(2)
            assert(result.data.results.map(_.id).contains(oneAttrDatasetId))
            assert(result.data.results.map(_.id).contains(twoAttrDatasetId))
          }

          // 複数のattribute(nameのみ)を指定して検索可能か(AND検索)
          val searchParmas2 = Map(
            "d" -> compact(
              render(
                ("attributes" -> Seq(("name" -> "hoge") ~ ("value" -> ""), ("name" -> "foo") ~ ("value" -> "")))
              )
            )
          )
          get("/api/datasets", searchParmas2) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(1)
            assert(result.data.results.map(_.id).contains(twoAttrDatasetId))
          }

          // 存在しないattribute(name)を指定した場合、データは出ないか
          val searchParmas3 = Map(
            "d" -> compact(
              render(
                ("attributes" -> Seq(("name" -> "nothing") ~ ("value" -> "")))
              )
            )
          )
          get("/api/datasets", searchParmas3) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(0)
          }

          // attribute(name, value両方)を指定して検索可能か
          val searchParmas4 = Map("d" -> compact(render(("attributes" -> Seq(attribute1)))))
          get("/api/datasets", searchParmas4) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(2)
            assert(result.data.results.map(_.id).contains(oneAttrDatasetId))
            assert(result.data.results.map(_.id).contains(twoAttrDatasetId))
          }

          // 複数のattribute(name, value両方)を指定して検索可能か(AND検索)
          val searchParmas5 = Map("d" -> compact(render(("attributes" -> Seq(attribute1, attribute2)))))
          get("/api/datasets", searchParmas5) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(1)
            assert(result.data.results.map(_.id).contains(twoAttrDatasetId))
          }

          // 存在しないattribute(name)を指定した場合、データは出ないか
          val searchParmas6 = Map(
            "d" -> compact(
              render(
                ("attributes" -> Seq(("name" -> "hoge") ~ ("value" -> "nothing")))
              )
            )
          )
          get("/api/datasets", searchParmas6) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(0)
          }
        }
      }

      "絞り込み条件の組み合わせ検索 " - {
        "query+owners" in {
          session {
            // datasetを別々のユーザーで2つ作成、どちらもguestアクセス可能とする
            signIn()
            val datasetId = createDataset()
            val datasetName = "test1"
            val params = Map("d" -> compact(render(("accessLevel" -> JInt(DefaultAccessLevel.FullPublic)))))
            put("/api/datasets/" + datasetId + "/guest_access", params) {
              checkStatus()
            }
            post("/api/signout") {
              checkStatus()
            }

            signIn("dummy4")
            val anotherDatasetId = createDataset()
            put("/api/datasets/" + anotherDatasetId + "/guest_access", params) {
              checkStatus()
            }
            post("/api/signout") {
              checkStatus()
            }

            signIn()
            // 初期検索結果は2件
            get("/api/datasets") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(2)
              assert(result.data.results.map(_.id).contains(datasetId))
              assert(result.data.results.map(_.id).contains(anotherDatasetId))
            }

            // 検索クエリとユーザー名を指定して検索可能か
            var searchParmas = Map(
              "d" -> compact(
                render(
                  ("query" -> datasetName) ~
                    ("owners" -> Seq(testUserName))
                )
              )
            )
            get("/api/datasets", searchParmas) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(1)
              assert(result.data.results.map(_.id).contains(datasetId))
              // 検索クエリとユーザー名で絞り込めているかチェック
              val dataset = result.data.results.find(_.id == datasetId).get
              assert(dataset.name.contains(datasetName))
              assert(dataset.ownerships.filter(_.ownerType == OwnerType.User).map(_.name).contains(testUserName))
            }

            var searchParmas2 = Map(
              "d" -> compact(
                render(
                  ("query" -> datasetName) ~
                    ("owners" -> Seq(dummyUserName))
                )
              )
            )
            get("/api/datasets", searchParmas2) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(1)
              assert(result.data.results.map(_.id).contains(anotherDatasetId))
              // 検索クエリとユーザー名で絞り込めているかチェック
              val dataset = result.data.results.find(_.id == anotherDatasetId).get
              assert(dataset.name.contains(datasetName))
              assert(dataset.ownerships.filter(_.ownerType == OwnerType.User).map(_.name).contains(dummyUserName))
            }
          }
        }

        "query+groups" in {
          session {
            // datasetを2つ作成、片方はグループに所属させる(Provider権限を与える)
            signIn()
            createDataset()
            val datasetId = createDataset()
            val datasetName = "test1"
            val groupName = "groupName" + UUID.randomUUID().toString
            val createGroupParams = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "group description"))))
            val groupId = post("/api/groups", createGroupParams) {
              checkStatus()
              parse(body).extract[AjaxResponse[Group]].data.id
            }
            val groupAclParams = Map(
              "d" -> compact(
                render(
                  Seq(
                    ("id" -> groupId) ~
                      ("ownerType" -> JInt(OwnerType.Group)) ~
                      ("accessLevel" -> JInt(GroupAccessLevel.Provider))
                  )
                )
              )
            )
            post("/api/datasets/" + datasetId + "/acl", groupAclParams) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
              assert(result.data.map(_.id) contains (groupId))
            }

            // 初期検索結果は2件
            get("/api/datasets") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(2)
              assert(result.data.results.map(_.id).contains(datasetId))
            }

            // 検索クエリとグループ名を指定して検索可能か
            var searchParmas = Map(
              "d" -> compact(
                render(
                  ("query" -> datasetName) ~
                    ("groups" -> Seq(groupName))
                )
              )
            )
            get("/api/datasets", searchParmas) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(1)
              assert(result.data.results.map(_.id).contains(datasetId))
              // 検索クエリとグループ名で絞り込めているかチェック
              val dataset = result.data.results.find(_.id == datasetId).get
              assert(dataset.name.contains(datasetName))
              assert(dataset.ownerships.filter(_.ownerType == OwnerType.Group).map(_.name).contains(groupName))
            }
          }
        }

        "query+attributes" in {
          session {
            // datasetを2つ作成、片方はatrribute付与
            signIn()
            createDataset()
            val datasetId = createDataset()
            val datasetName = "TEST NAME" + UUID.randomUUID
            val attribute = ("name" -> "hoge") ~ ("value" -> "piyo")
            val metadataParams = Map(
              "d" -> compact(
                render(
                  ("name" -> datasetName) ~
                    ("description" -> "description") ~
                    ("license" -> AppConf.defaultLicenseId) ~
                    ("attributes" -> Seq(attribute))
                )
              )
            )
            put("/api/datasets/" + datasetId + "/metadata", metadataParams) {
              checkStatus()
            }

            // 初期検索結果は2件
            get("/api/datasets") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(2)
              assert(result.data.results.map(_.id).contains(datasetId))
            }

            // 検索クエリとattributeを指定して検索可能か
            var searchParmas = Map(
              "d" -> compact(
                render(
                  ("query" -> datasetName) ~
                    ("attributes" -> Seq(attribute))
                )
              )
            )
            get("/api/datasets", searchParmas) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(1)
              assert(result.data.results.map(_.id).contains(datasetId))
              // 検索クエリとattributeで絞り込めているかチェック
              val dataset = result.data.results.find(_.id == datasetId).get
              assert(dataset.name.contains(datasetName))
              assert(dataset.attributes.map(_.name).contains(attribute.values.get("name").get))
              assert(dataset.attributes.map(_.value).contains(attribute.values.get("value").get))
            }
          }
        }

        "owners+gropus" in {
          session {
            // datasetを2つ作成、片方はグループに所属させる(Provider権限を与える)
            signIn()
            createDataset()
            val datasetId = createDataset()
            val groupName = "groupName" + UUID.randomUUID().toString
            val createGroupParams = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "group description"))))
            val groupId = post("/api/groups", createGroupParams) {
              checkStatus()
              parse(body).extract[AjaxResponse[Group]].data.id
            }
            val groupAclParams = Map(
              "d" -> compact(
                render(
                  Seq(
                    ("id" -> groupId) ~
                      ("ownerType" -> JInt(OwnerType.Group)) ~
                      ("accessLevel" -> JInt(GroupAccessLevel.Provider))
                  )
                )
              )
            )
            post("/api/datasets/" + datasetId + "/acl", groupAclParams) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
              assert(result.data.map(_.id) contains (groupId))
            }

            // 初期検索結果は2件
            get("/api/datasets") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(2)
              assert(result.data.results.map(_.id).contains(datasetId))
            }

            // ユーザー名とグループ名を指定して検索可能か
            var searchParmas = Map(
              "d" -> compact(
                render(
                  ("owners" -> Seq(testUserName)) ~
                    ("groups" -> Seq(groupName))
                )
              )
            )
            get("/api/datasets", searchParmas) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(1)
              assert(result.data.results.map(_.id).contains(datasetId))
              // ユーザー名とグループ名で絞り込めているかチェック
              val dataset = result.data.results.find(_.id == datasetId).get
              assert(dataset.ownerships.filter(_.ownerType == OwnerType.User).map(_.name).contains(testUserName))
              assert(dataset.ownerships.filter(_.ownerType == OwnerType.Group).map(_.name).contains(groupName))
            }
          }
        }

        "owners+attributes" in {
          session {
            // datasetを2つ作成、片方はatrribute付与
            signIn()
            createDataset()
            val datasetId = createDataset()
            val datasetName = "TEST NAME" + UUID.randomUUID
            val attribute = ("name" -> "hoge") ~ ("value" -> "piyo")
            val metadataParams = Map(
              "d" -> compact(
                render(
                  ("name" -> datasetName) ~
                    ("description" -> "description") ~
                    ("license" -> AppConf.defaultLicenseId) ~
                    ("attributes" -> Seq(attribute))
                )
              )
            )
            put("/api/datasets/" + datasetId + "/metadata", metadataParams) {
              checkStatus()
            }

            // 初期検索結果は2件
            get("/api/datasets") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(2)
              assert(result.data.results.map(_.id).contains(datasetId))
            }

            // ユーザー名とattributeを指定して検索可能か
            var searchParmas = Map(
              "d" -> compact(
                render(
                  ("owners" -> Seq(testUserName)) ~
                    ("attributes" -> Seq(attribute))
                )
              )
            )
            get("/api/datasets", searchParmas) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(1)
              assert(result.data.results.map(_.id).contains(datasetId))
              // ユーザー名とattributeで絞り込めているかチェック
              val dataset = result.data.results.find(_.id == datasetId).get
              assert(dataset.ownerships.filter(_.ownerType == OwnerType.User).map(_.name).contains(testUserName))
              assert(dataset.attributes.map(_.name).contains(attribute.values.get("name").get))
              assert(dataset.attributes.map(_.value).contains(attribute.values.get("value").get))
            }
          }
        }

        "groups+attributes" in {
          session {
            // datasetを2つ作成、片方はグループ権限(Provider)とatrribute付与
            signIn()
            createDataset()
            val datasetId = createDataset()
            // group
            val groupName = "groupName" + UUID.randomUUID().toString
            val createGroupParams = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "group description"))))
            val groupId = post("/api/groups", createGroupParams) {
              checkStatus()
              parse(body).extract[AjaxResponse[Group]].data.id
            }
            val groupAclParams = Map(
              "d" -> compact(
                render(
                  Seq(
                    ("id" -> groupId) ~
                      ("ownerType" -> JInt(OwnerType.Group)) ~
                      ("accessLevel" -> JInt(GroupAccessLevel.Provider))
                  )
                )
              )
            )
            post("/api/datasets/" + datasetId + "/acl", groupAclParams) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
              assert(result.data.map(_.id) contains (groupId))
            }
            // attributes
            val datasetName = "TEST NAME" + UUID.randomUUID
            val attribute = ("name" -> "hoge") ~ ("value" -> "piyo")
            val metadataParams = Map(
              "d" -> compact(
                render(
                  ("name" -> datasetName) ~
                    ("description" -> "description") ~
                    ("license" -> AppConf.defaultLicenseId) ~
                    ("attributes" -> Seq(attribute))
                )
              )
            )
            put("/api/datasets/" + datasetId + "/metadata", metadataParams) {
              checkStatus()
            }

            // 初期検索結果は2件
            get("/api/datasets") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(2)
              assert(result.data.results.map(_.id).contains(datasetId))
            }

            // グループ名とattributeを指定して検索可能か
            var searchParmas = Map(
              "d" -> compact(
                render(
                  ("groups" -> Seq(groupName)) ~
                    ("attributes" -> Seq(attribute))
                )
              )
            )
            get("/api/datasets", searchParmas) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(1)
              assert(result.data.results.map(_.id).contains(datasetId))
              // グループ名とattributeで絞り込めているかチェック
              val dataset = result.data.results.find(_.id == datasetId).get
              assert(dataset.ownerships.filter(_.ownerType == OwnerType.Group).map(_.name).contains(groupName))
              assert(dataset.attributes.map(_.name).contains(attribute.values.get("name").get))
              assert(dataset.attributes.map(_.value).contains(attribute.values.get("value").get))
            }
          }
        }

        "query+owners+groups" in {
          session {
            // datasetを2つ作成、片方はグループに所属させる(Provider権限を与える)
            signIn()
            createDataset()
            val datasetId = createDataset()
            val datasetName = "test1"
            val groupName = "groupName" + UUID.randomUUID().toString
            val createGroupParams = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "group description"))))
            val groupId = post("/api/groups", createGroupParams) {
              checkStatus()
              parse(body).extract[AjaxResponse[Group]].data.id
            }
            val groupAclParams = Map(
              "d" -> compact(
                render(
                  Seq(
                    ("id" -> groupId) ~
                      ("ownerType" -> JInt(OwnerType.Group)) ~
                      ("accessLevel" -> JInt(GroupAccessLevel.Provider))
                  )
                )
              )
            )
            post("/api/datasets/" + datasetId + "/acl", groupAclParams) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
              assert(result.data.map(_.id) contains (groupId))
            }

            // 初期検索結果は2件
            get("/api/datasets") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(2)
              assert(result.data.results.map(_.id).contains(datasetId))
            }

            // 検索クエリ・ユーザー名・グループ名を指定して検索可能か
            var searchParmas = Map(
              "d" -> compact(
                render(
                  ("query" -> datasetName) ~
                    ("owners" -> Seq(testUserName)) ~
                    ("groups" -> Seq(groupName))
                )
              )
            )
            get("/api/datasets", searchParmas) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(1)
              assert(result.data.results.map(_.id).contains(datasetId))
              // 検索クエリ・ユーザー名・グループ名で絞り込めているかチェック
              val dataset = result.data.results.find(_.id == datasetId).get
              assert(dataset.name.contains(datasetName))
              assert(dataset.ownerships.filter(_.ownerType == OwnerType.User).map(_.name).contains(testUserName))
              assert(dataset.ownerships.filter(_.ownerType == OwnerType.Group).map(_.name).contains(groupName))
            }
          }
        }

        "owners+groups+attributes" in {
          session {
            // datasetを2つ作成、片方はグループ権限(Provider)とatrribute付与
            signIn()
            createDataset()
            val datasetId = createDataset()
            // group
            val groupName = "groupName" + UUID.randomUUID().toString
            val createGroupParams = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "group description"))))
            val groupId = post("/api/groups", createGroupParams) {
              checkStatus()
              parse(body).extract[AjaxResponse[Group]].data.id
            }
            val groupAclParams = Map(
              "d" -> compact(
                render(
                  Seq(
                    ("id" -> groupId) ~
                      ("ownerType" -> JInt(OwnerType.Group)) ~
                      ("accessLevel" -> JInt(GroupAccessLevel.Provider))
                  )
                )
              )
            )
            post("/api/datasets/" + datasetId + "/acl", groupAclParams) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
              assert(result.data.map(_.id) contains (groupId))
            }
            // attributes
            val datasetName = "TEST NAME" + UUID.randomUUID
            val attribute = ("name" -> "hoge") ~ ("value" -> "piyo")
            val metadataParams = Map(
              "d" -> compact(
                render(
                  ("name" -> datasetName) ~
                    ("description" -> "description") ~
                    ("license" -> AppConf.defaultLicenseId) ~
                    ("attributes" -> Seq(attribute))
                )
              )
            )
            put("/api/datasets/" + datasetId + "/metadata", metadataParams) {
              checkStatus()
            }

            // 初期検索結果は2件
            get("/api/datasets") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(2)
              assert(result.data.results.map(_.id).contains(datasetId))
            }

            // ユーザー名・グループ名・attributeを指定して検索可能か
            var searchParmas = Map(
              "d" -> compact(
                render(
                  ("owners" -> Seq(testUserName)) ~
                    ("groups" -> Seq(groupName)) ~
                    ("attributes" -> Seq(attribute))
                )
              )
            )
            get("/api/datasets", searchParmas) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(1)
              assert(result.data.results.map(_.id).contains(datasetId))
              // ユーザー名・グループ名・attributeで絞り込めているかチェック
              val dataset = result.data.results.find(_.id == datasetId).get
              assert(dataset.ownerships.filter(_.ownerType == OwnerType.User).map(_.name).contains(testUserName))
              assert(dataset.ownerships.filter(_.ownerType == OwnerType.Group).map(_.name).contains(groupName))
              assert(dataset.attributes.map(_.name).contains(attribute.values.get("name").get))
              assert(dataset.attributes.map(_.value).contains(attribute.values.get("value").get))
            }
          }
        }

        "query+groups+attributes" in {
          session {
            // datasetを2つ作成、片方はグループ権限(Provider)とatrribute付与
            signIn()
            createDataset()
            val datasetId = createDataset()
            // group
            val groupName = "groupName" + UUID.randomUUID().toString
            val createGroupParams = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "group description"))))
            val groupId = post("/api/groups", createGroupParams) {
              checkStatus()
              parse(body).extract[AjaxResponse[Group]].data.id
            }
            val groupAclParams = Map(
              "d" -> compact(
                render(
                  Seq(
                    ("id" -> groupId) ~
                      ("ownerType" -> JInt(OwnerType.Group)) ~
                      ("accessLevel" -> JInt(GroupAccessLevel.Provider))
                  )
                )
              )
            )
            post("/api/datasets/" + datasetId + "/acl", groupAclParams) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
              assert(result.data.map(_.id) contains (groupId))
            }
            // attributes
            val datasetName = "TEST NAME" + UUID.randomUUID
            val attribute = ("name" -> "hoge") ~ ("value" -> "piyo")
            val metadataParams = Map(
              "d" -> compact(
                render(
                  ("name" -> datasetName) ~
                    ("description" -> "description") ~
                    ("license" -> AppConf.defaultLicenseId) ~
                    ("attributes" -> Seq(attribute))
                )
              )
            )
            put("/api/datasets/" + datasetId + "/metadata", metadataParams) {
              checkStatus()
            }

            // 初期検索結果は2件
            get("/api/datasets") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(2)
              assert(result.data.results.map(_.id).contains(datasetId))
            }

            // 検索クエリ・グループ名・attributeを指定して検索可能か
            var searchParmas = Map(
              "d" -> compact(
                render(
                  ("query" -> datasetName) ~
                    ("groups" -> Seq(groupName)) ~
                    ("attributes" -> Seq(attribute))
                )
              )
            )
            get("/api/datasets", searchParmas) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(1)
              assert(result.data.results.map(_.id).contains(datasetId))
              // 検索クエリ・グループ名・attributeで絞り込めているかチェック
              val dataset = result.data.results.find(_.id == datasetId).get
              assert(dataset.name.contains(datasetName))
              assert(dataset.ownerships.filter(_.ownerType == OwnerType.Group).map(_.name).contains(groupName))
              assert(dataset.attributes.map(_.name).contains(attribute.values.get("name").get))
              assert(dataset.attributes.map(_.value).contains(attribute.values.get("value").get))
            }
          }
        }

        "query+owners+attributes" in {
          session {
            // datasetを2つ作成、片方はatrribute付与
            signIn()
            createDataset()
            val datasetId = createDataset()
            val datasetName = "TEST NAME" + UUID.randomUUID
            val attribute = ("name" -> "hoge") ~ ("value" -> "piyo")
            val metadataParams = Map(
              "d" -> compact(
                render(
                  ("name" -> datasetName) ~
                    ("description" -> "description") ~
                    ("license" -> AppConf.defaultLicenseId) ~
                    ("attributes" -> Seq(attribute))
                )
              )
            )
            put("/api/datasets/" + datasetId + "/metadata", metadataParams) {
              checkStatus()
            }

            // 初期検索結果は2件
            get("/api/datasets") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(2)
              assert(result.data.results.map(_.id).contains(datasetId))
            }

            // 検索クエリ・ユーザー名・attributeを指定して検索可能か
            var searchParmas = Map(
              "d" -> compact(
                render(
                  ("query" -> datasetName) ~
                    ("owners" -> Seq(testUserName)) ~
                    ("attributes" -> Seq(attribute))
                )
              )
            )
            get("/api/datasets", searchParmas) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(1)
              assert(result.data.results.map(_.id).contains(datasetId))
              // 検索クエリ・ユーザー名・attributeで絞り込めているかチェック
              val dataset = result.data.results.find(_.id == datasetId).get
              assert(dataset.name.contains(datasetName))
              assert(dataset.ownerships.filter(_.ownerType == OwnerType.User).map(_.name).contains(testUserName))
              assert(dataset.attributes.map(_.name).contains(attribute.values.get("name").get))
              assert(dataset.attributes.map(_.value).contains(attribute.values.get("value").get))
            }
          }
        }

        "all" in {
          session {
            // datasetを2つ作成、片方はグループ権限(Provider)とatrribute付与
            signIn()
            createDataset()
            val datasetId = createDataset()
            // group
            val groupName = "groupName" + UUID.randomUUID().toString
            val createGroupParams = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "group description"))))
            val groupId = post("/api/groups", createGroupParams) {
              checkStatus()
              parse(body).extract[AjaxResponse[Group]].data.id
            }
            val groupAclParams = Map(
              "d" -> compact(
                render(
                  Seq(
                    ("id" -> groupId) ~
                      ("ownerType" -> JInt(OwnerType.Group)) ~
                      ("accessLevel" -> JInt(GroupAccessLevel.Provider))
                  )
                )
              )
            )
            post("/api/datasets/" + datasetId + "/acl", groupAclParams) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Seq[DatasetOwnership]]]
              assert(result.data.map(_.id) contains (groupId))
            }
            // attributes
            val datasetName = "TEST NAME" + UUID.randomUUID
            val attribute = ("name" -> "hoge") ~ ("value" -> "piyo")
            val metadataParams = Map(
              "d" -> compact(
                render(
                  ("name" -> datasetName) ~
                    ("description" -> "description") ~
                    ("license" -> AppConf.defaultLicenseId) ~
                    ("attributes" -> Seq(attribute))
                )
              )
            )
            put("/api/datasets/" + datasetId + "/metadata", metadataParams) {
              checkStatus()
            }

            // 初期検索結果は2件
            get("/api/datasets") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(2)
              assert(result.data.results.map(_.id).contains(datasetId))
            }

            // 検索クエリ・ユーザー名・グループ名・attributeを指定して検索可能か
            var searchParmas = Map(
              "d" -> compact(
                render(
                  ("query" -> datasetName) ~
                    ("owners" -> Seq(testUserName)) ~
                    ("groups" -> Seq(groupName)) ~
                    ("attributes" -> Seq(attribute))
                )
              )
            )
            get("/api/datasets", searchParmas) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
              result.data.summary.total should be(1)
              assert(result.data.results.map(_.id).contains(datasetId))
              // 検索クエリ・ユーザー名・グループ名・attributeで絞り込めているかチェック
              val dataset = result.data.results.find(_.id == datasetId).get
              assert(dataset.name.contains(datasetName))
              assert(dataset.ownerships.filter(_.ownerType == OwnerType.User).map(_.name).contains(testUserName))
              assert(dataset.ownerships.filter(_.ownerType == OwnerType.Group).map(_.name).contains(groupName))
              assert(dataset.attributes.map(_.name).contains(attribute.values.get("name").get))
              assert(dataset.attributes.map(_.value).contains(attribute.values.get("value").get))
            }
          }
        }
      }

      "ゲストからデータセットのオーナー情報が閲覧できないか" in {
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
            result.data.results.head.ownerships.length should be(0)
          }

          get("/api/datasets/" + datasetId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            result.data.ownerships.length should be(0)
          }
        }
      }

      "Featured Datasets検索で、attributeのvalue順にソートされるか" in {
        session {
          // datasetを5つ作成、それぞれname:featuredのattributeを付与(ただしvalueは全て異なる)
          signIn()
          val attributeValues = Array("hoge", "piyo", "foo", "bar", "てすと")
          val datasetIdAndValueTuple = for (i <- 0 to attributeValues.length - 1) yield {
            (createDataset(), attributeValues(i))
          }
          // attributes設定
          val datasetBaseName = "TEST NAME"
          val featuredAttributeName = ("name" -> "featured")
          datasetIdAndValueTuple.foreach { tuple =>
            val metadataParams = Map(
              "d" -> compact(
                render(
                  ("name" -> (datasetBaseName + UUID.randomUUID)) ~
                    ("description" -> "description") ~
                    ("license" -> AppConf.defaultLicenseId) ~
                    ("attributes" -> Seq(featuredAttributeName ~ ("value" -> tuple._2)))
                )
              )
            )
            put("/api/datasets/" + tuple._1 + "/metadata", metadataParams) {
              checkStatus()
            }
          }

          // 初期検索結果は5件
          get("/api/datasets") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(datasetIdAndValueTuple.length)
            result.data.results.map(_.id).diff(datasetIdAndValueTuple.map(_._1)).length should be(0)
          }

          // "orderby"に"attribute"、"attributes"に"name":"featured"を指定して検索できるか
          var searchParmas = Map(
            "d" -> compact(
              render(
                ("attributes" -> Seq(featuredAttributeName ~ ("value" -> ""))) ~
                  ("orderby" -> "attribute")
              )
            )
          )
          get("/api/datasets", searchParmas) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
            result.data.summary.total should be(datasetIdAndValueTuple.length)

            // attributeのvalue順にソートされているかチェック
            val sortedTuple = datasetIdAndValueTuple.sortBy(d => d._2)
            for (i <- 0 to sortedTuple.length - 1) {
              val dataset = result.data.results(i)
              dataset.id should be(sortedTuple(i)._1)
              dataset.attributes.find(_.name == "featured") match {
                case Some(attr) => attr.value should be(sortedTuple(i)._2)
                case None => fail("attribute 'featured' not found.")
              }
            }
          }
        }
      }
      "ファイル情報取得API" - {
        "datasetId無効" in {
          session {
            signIn()
            val files = Seq(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            get(s"/api/datasets/${datasetId.reverse}/files") {
              status should be(404)
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.status should be("Illegal Argument")
            }
          }
        }
        "GuestUser+アクセス権なし" in {
          session {
            signIn()
            val files = Seq(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            post("/api/signout") { checkStatus() }
            get(s"/api/datasets/${datasetId}/files") {
              status should be(403)
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.status should be("AccessDenied")
            }
          }
        }
        "LoginUser+アクセス権なし" in {
          session {
            signIn()
            val files = Seq(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            post("/api/signout") { checkStatus() }
            signIn("dummy4")
            get(s"/api/datasets/${datasetId}/files") {
              status should be(403)
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.status should be("AccessDenied")
            }
          }
        }
        "GuestUser+アクセス権あり" in {
          session {
            signIn()
            val files = Seq(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val params = Map("d" -> compact(render(("accessLevel" -> JInt(DefaultAccessLevel.FullPublic)))))
            put(s"/api/datasets/${datasetId}/guest_access", params) { checkStatus() }
            post("/api/signout") { checkStatus() }
            get(s"/api/datasets/${datasetId}/files") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.summary.count should be(1)
              result.data.results.size should be(1)
            }
          }
        }
        "LoginUser+アクセス権あり" in {
          session {
            signIn()
            val files = Seq(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val params = Map(
              "d" -> compact(
                render(
                  Seq(
                    ("id" -> dummyUserId) ~
                      ("ownerType" -> JInt(OwnerType.User)) ~
                      ("accessLevel" -> JInt(UserAccessLevel.FullPublic))
                  )
                )
              )
            )
            post(s"/api/datasets/${datasetId}/acl", params) {
              checkStatus()
            }
            post("/api/signout") { checkStatus() }
            signIn("dummy4")
            get(s"/api/datasets/${datasetId}/files") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.summary.count should be(1)
              result.data.results.size should be(1)
            }
          }
        }
      }
      "ZIPファイル情報取得API" - {
        "datasetId無効" in {
          session {
            signIn()
            val files = Seq(("file[]", dummyZipFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            get(s"/api/datasets/${datasetId.reverse}/files/${fileId}/zippedfiles") {
              status should be(404)
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.status should be("Illegal Argument")
            }
          }
        }
        "fileId無効" in {
          session {
            signIn()
            val files = Seq(("file[]", dummyZipFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            get(s"/api/datasets/${datasetId}/files/${fileId.reverse}/zippedfiles") {
              status should be(404)
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.status should be("Illegal Argument")
            }
          }
        }
        "fileId無効(zipでない)" in {
          session {
            signIn()
            val files = Seq(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(400)
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.status should be("BadRequest")
            }
          }
        }
        "GuestUser+アクセス権なし" in {
          session {
            signIn()
            val files = Seq(("file[]", dummyZipFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            post("/api/signout") { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(403)
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.status should be("AccessDenied")
            }
          }
        }
        "LoginUser+アクセス権なし" in {
          session {
            signIn()
            val files = Seq(("file[]", dummyZipFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            post("/api/signout") { checkStatus() }
            signIn("dummy4")
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              status should be(403)
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.status should be("AccessDenied")
            }
          }
        }
        "GuestUser+アクセス権あり" in {
          session {
            signIn()
            val files = Seq(("file[]", dummyZipFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val params = Map("d" -> compact(render(("accessLevel" -> JInt(DefaultAccessLevel.FullPublic)))))
            put(s"/api/datasets/${datasetId}/guest_access", params) { checkStatus() }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            post("/api/signout") { checkStatus() }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.data.summary.count should be(2)
              result.data.results.size should be(2)
            }
          }
        }
        "LoginUser+アクセス権あり" in {
          session {
            signIn()
            val files = Seq(("file[]", dummyZipFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val params = Map(
              "d" -> compact(
                render(
                  Seq(
                    ("id" -> dummyUserId) ~
                      ("ownerType" -> JInt(OwnerType.User)) ~
                      ("accessLevel" -> JInt(UserAccessLevel.FullPublic))
                  )
                )
              )
            )
            post(s"/api/datasets/${datasetId}/acl", params) {
              checkStatus()
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            post("/api/signout") { checkStatus() }
            signIn("dummy4")
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.data.summary.count should be(2)
              result.data.results.size should be(2)
            }
          }
        }
      }
      "ファイル情報取得API(リクエスト)" - {
        "JSONなし" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq.fill(151)(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            get(s"/api/datasets/${datasetId}/files") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.summary.total should be(151)
              result.data.summary.count should be(150)
              result.data.summary.offset should be(0)
              result.data.results.size should be(150)
            }
          }
        }
        "JSONとして不正な文字列" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq.fill(151)(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val params = Map("d" -> "null")
            get(s"/api/datasets/${datasetId}/files", params) {
              status should be(400)
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.status should be("Illegal Argument")
            }
          }
        }
        "空JSON" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq.fill(151)(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val params = Map("d" -> "{}")
            get(s"/api/datasets/${datasetId}/files", params) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.summary.total should be(151)
              result.data.summary.count should be(150)
              result.data.summary.offset should be(0)
              result.data.results.size should be(150)
            }
          }
        }
        "limit=-1" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq.fill(151)(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val params = Map("d" -> compact(render(Seq(("limit" -> JInt(-1))))))
            get(s"/api/datasets/${datasetId}/files", params) {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.status should be("Illegal Argument")
            }
          }
        }
        "limit=0" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq.fill(151)(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val params = Map("d" -> compact(render(Seq(("limit" -> JInt(0))))))
            get(s"/api/datasets/${datasetId}/files", params) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.summary.total should be(151)
              result.data.summary.count should be(0)
              result.data.summary.offset should be(0)
              result.data.results.size should be(0)
            }
          }
        }
        "limit=1" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq.fill(151)(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val params = Map("d" -> compact(render(Seq(("limit" -> JInt(1))))))
            get(s"/api/datasets/${datasetId}/files", params) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.summary.total should be(151)
              result.data.summary.count should be(1)
              result.data.summary.offset should be(0)
              result.data.results.size should be(1)
            }
          }
        }
        "limit=152" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq.fill(151)(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val params = Map("d" -> compact(render(Seq(("limit" -> JInt(152))))))
            get(s"/api/datasets/${datasetId}/files", params) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.summary.total should be(151)
              result.data.summary.count should be(151)
              result.data.summary.offset should be(0)
              result.data.results.size should be(151)
            }
          }
        }
        "limit=数値以外" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq.fill(151)(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val params = Map("d" -> compact(render(Seq(("limit" -> "hoge")))))
            get(s"/api/datasets/${datasetId}/files", params) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.summary.total should be(151)
              result.data.summary.count should be(150)
              result.data.summary.offset should be(0)
              result.data.results.size should be(150)
            }
          }
        }
        "offset=-1" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq.fill(20)(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val params = Map("d" -> compact(render(Seq(("offset" -> JInt(-1))))))
            get(s"/api/datasets/${datasetId}/files", params) {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.status should be("Illegal Argument")
            }
          }
        }
        "offset=0" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq.fill(20)(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val params = Map("d" -> compact(render(Seq(("offset" -> JInt(0))))))
            get(s"/api/datasets/${datasetId}/files", params) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.summary.total should be(20)
              result.data.summary.count should be(20)
              result.data.summary.offset should be(0)
              result.data.results.size should be(20)
            }
          }
        }
        "offset=20" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq.fill(20)(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val params = Map("d" -> compact(render(Seq(("offset" -> JInt(20))))))
            get(s"/api/datasets/${datasetId}/files", params) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.summary.total should be(20)
              result.data.summary.count should be(0)
              result.data.summary.offset should be(20)
              result.data.results.size should be(0)
            }
          }
        }
        "offset=21" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq.fill(20)(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val params = Map("d" -> compact(render(Seq(("offset" -> JInt(21))))))
            get(s"/api/datasets/${datasetId}/files", params) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.summary.total should be(20)
              result.data.summary.count should be(0)
              result.data.summary.offset should be(21)
              result.data.results.size should be(0)
            }
          }
        }
        "offset=数値以外" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq.fill(20)(("file[]", dummyFile))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val params = Map("d" -> compact(render(Seq(("offset" -> "hoge")))))
            get(s"/api/datasets/${datasetId}/files", params) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.summary.total should be(20)
              result.data.summary.count should be(20)
              result.data.summary.offset should be(0)
              result.data.results.size should be(20)
            }
          }
        }
      }
      "ZIPファイル内情報取得API(リクエスト)" - {
        "JSONなし" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq(("file[]", new File("../testdata/test3.zip")))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles") {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.data.summary.total should be(151)
              result.data.summary.count should be(150)
              result.data.summary.offset should be(0)
              result.data.results.size should be(150)
            }
          }
        }
        "JSONとして不正な文字列" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq(("file[]", new File("../testdata/test3.zip")))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            val params = Map("d" -> "null")
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params) {
              status should be(400)
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.status should be("Illegal Argument")
            }
          }
        }
        "空JSON" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq(("file[]", new File("../testdata/test3.zip")))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            val params = Map("d" -> "{}")
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.data.summary.total should be(151)
              result.data.summary.count should be(150)
              result.data.summary.offset should be(0)
              result.data.results.size should be(150)
            }
          }
        }
        "limit=-1" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq(("file[]", new File("../testdata/test3.zip")))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            val params = Map("d" -> compact(render(Seq(("limit" -> JInt(-1))))))
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params) {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.status should be("Illegal Argument")
            }
          }
        }
        "limit=0" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq(("file[]", new File("../testdata/test3.zip")))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            val params = Map("d" -> compact(render(Seq(("limit" -> JInt(0))))))
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.data.summary.total should be(151)
              result.data.summary.count should be(0)
              result.data.summary.offset should be(0)
              result.data.results.size should be(0)
            }
          }
        }
        "limit=1" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq(("file[]", new File("../testdata/test3.zip")))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            val params = Map("d" -> compact(render(Seq(("limit" -> JInt(1))))))
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.data.summary.total should be(151)
              result.data.summary.count should be(1)
              result.data.summary.offset should be(0)
              result.data.results.size should be(1)
            }
          }
        }
        "limit=152" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq(("file[]", new File("../testdata/test3.zip")))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            val params = Map("d" -> compact(render(Seq(("limit" -> JInt(152))))))
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.data.summary.total should be(151)
              result.data.summary.count should be(151)
              result.data.summary.offset should be(0)
              result.data.results.size should be(151)
            }
          }
        }
        "limit=数値以外" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq(("file[]", new File("../testdata/test3.zip")))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            val params = Map("d" -> compact(render(Seq(("limit" -> "hoge")))))
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.data.summary.total should be(151)
              result.data.summary.count should be(150)
              result.data.summary.offset should be(0)
              result.data.results.size should be(150)
            }
          }
        }
        "offset=-1" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq(("file[]", new File("../testdata/test4.zip")))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            val params = Map("d" -> compact(render(Seq(("offset" -> JInt(-1))))))
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params) {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.status should be("Illegal Argument")
            }
          }
        }
        "offset=0" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq(("file[]", new File("../testdata/test4.zip")))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            val params = Map("d" -> compact(render(Seq(("offset" -> JInt(0))))))
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.data.summary.total should be(20)
              result.data.summary.count should be(20)
              result.data.summary.offset should be(0)
              result.data.results.size should be(20)
            }
          }
        }
        "offset=20" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq(("file[]", new File("../testdata/test4.zip")))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            val params = Map("d" -> compact(render(Seq(("offset" -> JInt(20))))))
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.data.summary.total should be(20)
              result.data.summary.count should be(0)
              result.data.summary.offset should be(20)
              result.data.results.size should be(0)
            }
          }
        }
        "offset=21" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq(("file[]", new File("../testdata/test4.zip")))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            val params = Map("d" -> compact(render(Seq(("offset" -> JInt(21))))))
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.data.summary.total should be(20)
              result.data.summary.count should be(0)
              result.data.summary.offset should be(21)
              result.data.results.size should be(0)
            }
          }
        }
        "offset=数値以外" in {
          // このテストはapplication.conf file_limit=150が前提です
          session {
            signIn()
            val files = Seq(("file[]", new File("../testdata/test4.zip")))
            val createParams = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
            val datasetId = post("/api/datasets", createParams, files) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[Dataset]]
              result.data.id
            }
            val fileId = get(s"/api/datasets/${datasetId}/files") {
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetFile]]]
              result.data.results(0).id
            }
            val params = Map("d" -> compact(render(Seq(("offset" -> "hoge")))))
            get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", params) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]]
              result.data.summary.total should be(20)
              result.data.summary.count should be(20)
              result.data.summary.offset should be(0)
              result.data.results.size should be(20)
            }
          }
        }
      }

      "POST /api/datasets/:dataset_id/acl" - {
        "オーナー変更がない場合" in {
          session {
            signIn()
            val datasetId = createDataset()
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3))))))
            post(s"/api/datasets/${datasetId}/acl", params) {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }

        "オーナーが1人から0人に変更される場合" in {
          session {
            signIn()
            val datasetId = createDataset()
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(0))))))
            post(s"/api/datasets/${datasetId}/acl", params) {
              status should be(400)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("BadRequest")
            }
          }
        }

        "オーナーが2人から1人に変更される場合" in {
          session {
            signIn()
            val datasetId = createDataset()
            // オーナー1人追加
            val addParams = Map("d" -> compact(render(Seq(("id" -> "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04") ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3))))))
            post(s"/api/datasets/${datasetId}/acl", addParams) {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
            // オーナー1人除去
            val removeParams = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(0))))))
            post(s"/api/datasets/${datasetId}/acl", removeParams) {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }

        "オーナーが2人から0人に変更される場合" in {
          session {
            signIn()
            val datasetId = createDataset()
            // オーナー1人追加
            val addParams = Map("d" -> compact(render(Seq(("id" -> "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04") ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3))))))
            post(s"/api/datasets/${datasetId}/acl", addParams) {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
            // オーナー2人除去
            val removeParams = Map("d" -> compact(render(Seq(
              ("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(0)),
              ("id" -> "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04") ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(0))
            ))))
            post(s"/api/datasets/${datasetId}/acl", removeParams) {
              status should be(400)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("BadRequest")
            }
          }
        }

        "オーナーが1人から1人追加、1人除去される場合" in {
          session {
            signIn()
            val datasetId = createDataset()
            // オーナー1人追加、1人除去
            val changeParams = Map("d" -> compact(render(Seq(
              ("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(0)),
              ("id" -> "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04") ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3))
            ))))
            post(s"/api/datasets/${datasetId}/acl", changeParams) {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }

        "オーナーが2人から2人追加、2人除去される場合" in {
          session {
            signIn()
            val datasetId = createDataset()
            // オーナー1人追加
            val addParams = Map("d" -> compact(render(Seq(("id" -> "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04") ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3))))))
            post(s"/api/datasets/${datasetId}/acl", addParams) {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
            // オーナー2人追加、2人除去
            val changeParams = Map("d" -> compact(render(Seq(
              ("id" -> "4aaefd45-2fe5-4ce0-b156-3141613f69a6") ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3)),
              ("id" -> "eb7a596d-e50c-483f-bbc7-50019eea64d7") ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3)),
              ("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(0)),
              ("id" -> "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04") ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(0))
            ))))
            post(s"/api/datasets/${datasetId}/acl", changeParams) {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
      }
    }
  }

  private def changeStorageState(id: String, local: Int, s3: Int): Unit = {
    DB.localTx { implicit s =>
      dsmoq.persistence.Dataset.find(id).get.copy(
        localState = local,
        s3State = s3
      ).save()
      withSQL {
        val c = dsmoq.persistence.File.column
        update(dsmoq.persistence.File)
          .set(
            c.localState -> local,
            c.s3State -> s3
          )
          .where
          .eqUuid(c.datasetId, id)
      }.update.apply()
    }
  }
  private def flattenFilePath(file: File): List[File] = file match {
    case f: File if f.isDirectory => {
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

  private def createDataset(): String = {
    createDataset(name = "test1", file = Some(dummyFile))
  }
}
