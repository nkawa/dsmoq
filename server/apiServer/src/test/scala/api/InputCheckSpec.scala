package api

import java.io.File
import java.net.URLEncoder

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import common.DsmoqSpec
import dsmoq.AppConf
import dsmoq.controllers.AjaxResponse
import dsmoq.services.json.DatasetData.Dataset
import dsmoq.services.json.DatasetData.DatasetAddFiles
import dsmoq.services.json.DatasetData.DatasetAddImages
import dsmoq.services.json.DatasetData.DatasetTask
import dsmoq.services.json.GroupData.Group
import dsmoq.services.json.GroupData.GroupAddImages

class InputCheckSpec extends DsmoqSpec {
  private val zeroByteImage = new File("../testdata/image/0byte.png")
  private val nonZeroByteImage = new File("../testdata/image/1byteover.png")
  private val zeroByteCsv = new File("../testdata/test0.csv")
  private val nonZeroByteCsv = new File("../testdata/test1.csv")
  private val dummyZipFile = new File("../testdata/test1.zip")

  private val testUserName = "dummy1"
  private val dummyUserName = "dummy4"
  private val testUserId = "023bfa40-e897-4dad-96db-9fd3cf001e79" // dummy1
  private val dummyUserId = "eb7a596d-e50c-483f-bbc7-50019eea64d7" // dummy 4
  private val dummyUserLoginParams = Map("d" -> compact(render(("id" -> "dummy4") ~ ("password" -> "password"))))

  "API test" - {
    "signin" - {
      "POST /api/signin" in {
        // JSON形式
        jsonFormatCheck(POST, "/api/signin")
        // 省略不可能パラメータ省略(id)
        block {
          val params = Map("d" -> compact(render(("password" -> "password"))))
          requireCheck(POST, "/api/signin", params)
        }
        // 省略不可能パラメータ省略(password)
        block {
          val params = Map("d" -> compact(render(("id" -> "dummy1"))))
          requireCheck(POST, "/api/signin", params)
        }
        // 空文字不許可チェック(id)
        block {
          val generator = (x: String) => Map("d" -> compact(render(("id" -> x) ~ ("password" -> "password"))))
          nonEmptyCheck(POST, "/api/signin", "dummy1", generator)
        }
        // 空文字不許可チェック(password)
        block {
          val generator = (x: String) => Map("d" -> compact(render(("id" -> "dummy1") ~ ("password" -> x))))
          nonEmptyCheck(POST, "/api/signin", "password", generator)
        }
      }
    }

    "profile" - {
      "PUT /api/profile" in {
        session {
          signIn()
          // JSON形式
          jsonFormatCheck(PUT, "/api/profile")
          // 省略不可能パラメータ省略(name)
          block {
            val params = Map("d" -> compact(render(("fullname" -> "fullname1"))))
            requireCheck(PUT, "/api/profile", params)
          }
          // 省略不可能パラメータ省略(fullname)
          block {
            val params = Map("d" -> compact(render(("name" -> "name1"))))
            requireCheck(PUT, "/api/profile", params)
          }
          // 空文字不許可チェック(name)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("name" -> x) ~ ("fullname" -> "fullname1"))))
            nonEmptyCheck(PUT, "/api/profile", "name1", generator)
          }
          // 空文字不許可チェック(fullname)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("name" -> "name1") ~ ("fullname" -> x))))
            nonEmptyCheck(PUT, "/api/profile", "fullname1", generator)
          }
        }
      }

      "PUT /api/profile/password" in {
        session {
          signIn()
          // JSON形式
          jsonFormatCheck(PUT, "/api/profile/password")
          // 省略不可能パラメータ省略(currentPassword)
          block {
            val params = Map("d" -> compact(render(("newPassword" -> "hoge"))))
            requireCheck(PUT, "/api/profile/password", params)
          }
          // 省略不可能パラメータ省略(newPassword)
          block {
            val params = Map("d" -> compact(render(("currentPassword" -> "password"))))
            requireCheck(PUT, "/api/profile/password", params)
          }
          // 空文字不許可チェック(currentPassword)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("currentPassword" -> x) ~ ("newPassword" -> "hoge"))))
            nonEmptyCheck(PUT, "/api/profile/password", "password", generator)
          }
          // 空文字不許可チェック(newPassword)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("currentPassword" -> "hoge") ~ ("newPassword" -> x))))
            nonEmptyCheck(PUT, "/api/profile/password", "password", generator)
          }
        }
      }

      "POST /api/profile/image" in {
        session {
          signIn()
          // 省略不可能パラメータ省略(icon)
          requireCheck(POST, "/api/profile/image", Map.empty)
          // ファイル(0byte)
          block {
            val generator = (x: File) => Map("icon" -> x)
            fileCheck(POST, "/api/profile/image", generator)
          }
        }
      }

      "POST /api/profile/email_change_requests" in {
        session {
          signIn()
          // JSONフォーマット
          jsonFormatCheck(POST, "/api/profile/email_change_requests")
          // 省略不可能パラメータ省略(email)
          block {
            val params = Map("d" -> "{}")
            requireCheck(POST, "/api/profile/email_change_requests", params)
          }
          // 空文字不許可チェック(email)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("email" -> x))))
            nonEmptyCheck(POST, "/api/profile/email_change_requests", "hoge@hoge.jp", generator)
          }
        }
      }
    }

    "dataset" - {
      "POST /api/datasets" in {
        session {
          signIn()
          // 省略不可能パラメータ省略(saveLocal)
          block {
            val params = Map("saveS3" -> "false", "name" -> "dummy1")
            requireCheck(POST, "/api/datasets", params)
          }
          // 省略不可能パラメータ省略(saveS3)
          block {
            val params = Map("saveLocal" -> "false", "name" -> "dummy1")
            requireCheck(POST, "/api/datasets", params)
          }
          // 省略不可能パラメータ省略(name)
          block {
            val params = Map("saveLocal" -> "false", "saveS3" -> "true")
            requireCheck(POST, "/api/datasets", params)
          }
          // 真偽値チェック(saveLocal)
          block {
            val generator = (x: String) => Map("saveLocal" -> x, "saveS3" -> "true", "name" -> "dummy1")
            booleanCheckForForm(POST, "/api/datasets", generator)
          }
          // 真偽値チェック(saveS3)
          block {
            val generator = (x: String) => Map("saveLocal" -> "true", "saveS3" -> x, "name" -> "dummy1")
            booleanCheckForForm(POST, "/api/datasets", generator)
          }
          // saveLocal, saveS3の両方にfalseが指定された
          block {
            val params = Map("saveLocal" -> "false", "saveS3" -> "false", "name" -> "dummy1")
            post("/api/datasets", params) {
              parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument")
            }
          }
        }
      }

      "GET /api/datasets" in {
        session {
          signIn()
          // JSONフォーマット
          jsonFormatOnlyCheck(GET, "/api/datasets")
          // orderbyが"attribute"以外
          block {
            val params = Map("d" -> compact(render(("orderby" -> "hoge"))))
            get("/api/datasets", params) {
              parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument")
            }
          }
          // 数値チェック(limit)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("limit" -> x))))
            nonMinusIntCheck(GET, "/api/datasets", generator)
          }
          // 数値チェック(offset)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("offset" -> x))))
            nonMinusIntCheck(GET, "/api/datasets", generator)
          }
        }
      }

      "GET /api/datasets/:dataset_id" in {
        session {
          signIn()
          val datasetId = createDataset()
          // UUIDチェック(dataset_id)
          val generator = (x: String) => s"/api/datasets/${x}"
          uuidCheckForUrl(GET, generator, datasetId)
        }
      }

      "GET /api/datasets/:dataset_id/acl" in {
        session {
          signIn()
          val datasetId = createDataset()
          // JSONフォーマット
          jsonFormatOnlyCheck(GET, s"/api/datasets/${datasetId}/acl")
          // UUIDチェック(dataset_id)
          block {
            val generator = (x: String) => s"/api/datasets/${x}/acl"
            uuidCheckForUrl(GET, generator, datasetId)
          }
          // 数値チェック(limit)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("limit" -> x))))
            nonMinusIntCheck(GET, s"/api/datasets/${datasetId}/acl", generator)
          }
          // 数値チェック(offset)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("offset" -> x))))
            nonMinusIntCheck(GET, s"/api/datasets/${datasetId}/acl", generator)
          }
        }
      }

      "POST /api/datasets/:dataset_id/acl" in {
        session {
          signIn()
          val datasetId = createDataset()
          // JSONフォーマット
          jsonFormatCheck(POST, s"/api/datasets/${datasetId}/acl")
          // UUIDチェック(dataset_id)
          block {
            val generator = (x: String) => s"/api/datasets/${x}/acl"
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3))))))
            uuidCheckForUrl(POST, generator, datasetId, params)
          }
          // 長さチェック
          block {
            val generator = (x: Seq[JValue]) => Map("d" -> compact(render(x)))
            val valid = Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3)))
            nonZeroLengthCheckForParam(POST, s"/api/datasets/${datasetId}/acl", valid, generator)
          }
          // 省略不可能パラメータ省略(id)
          block {
            val params = Map("d" -> compact(render(Seq(("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3))))))
            requireCheck(POST, s"/api/datasets/${datasetId}/acl", params)
          }
          // 省略不可能パラメータ省略(ownerType)
          block {
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("accessLevel" -> JInt(3))))))
            requireCheck(POST, s"/api/datasets/${datasetId}/acl", params)
          }
          // 省略不可能パラメータ省略(accessLevel)
          block {
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1))))))
            requireCheck(POST, s"/api/datasets/${datasetId}/acl", params)
          }
          // 空文字不許可チェック(id)
          block {
            val generator = (x: String) => Map("d" -> compact(render(Seq(("id" -> x) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3))))))
            nonEmptyCheck(POST, s"/api/datasets/${datasetId}/acl", testUserId, generator)
          }
          // UUIDチェック(id)
          block {
            val generator = (x: String) => Map("d" -> compact(render(Seq(("id" -> x) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(3))))))
            uuidCheckForForm(POST, s"/api/datasets/${datasetId}/acl", testUserId, generator)
          }
          // 正常範囲チェック(ownerType)
          block {
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> "") ~ ("accessLevel" -> JInt(3))))))
            post(s"/api/datasets/${datasetId}/acl", params) {
              parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument")
            }
          }
          block {
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(-1)) ~ ("accessLevel" -> JInt(3))))))
            post(s"/api/datasets/${datasetId}/acl", params) {
              parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument")
            }
          }
          block {
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(0)) ~ ("accessLevel" -> JInt(3))))))
            post(s"/api/datasets/${datasetId}/acl", params) {
              parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument")
            }
          }
          // 正常範囲チェック(accessLevel)
          block {
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> "")))))
            post(s"/api/datasets/${datasetId}/acl", params) {
              parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument")
            }
          }
          block {
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(-1))))))
            post(s"/api/datasets/${datasetId}/acl", params) {
              parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument")
            }
          }
          block {
            val params = Map("d" -> compact(render(Seq(("id" -> testUserId) ~ ("ownerType" -> JInt(1)) ~ ("accessLevel" -> JInt(4))))))
            post(s"/api/datasets/${datasetId}/acl", params) {
              parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument")
            }
          }
        }
      }

      "GET /api/datasets/:dataset_id/attributes/export" in {
        // この項目のテストはレスポンス形式修正後に可能になる
        session {
          signIn()
          val datasetId = createDataset()
          // UUIDチェック(dataset_id)
          val urlGenerator = (x: String) => s"/api/datasets/${x}/attributes/export"
          val zeroSpaceUrl = urlGenerator("")
          val moreSpaceUrl = urlGenerator(URLEncoder.encode("   ", "UTF-8"))
          val invalidUrl = urlGenerator("test")
          val validUrl = urlGenerator(datasetId)
          get(zeroSpaceUrl) { parse(body).extract[AjaxResponse[Any]].status should be("NotFound") }
          get(moreSpaceUrl) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
          get(invalidUrl) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
          get(validUrl) {
            status should be(200)
            // bodyの確認の代わりに、正常ケースで付与されるContent-Dispositionヘッダの有無で正常動作か否かを判定している
            response.header.get("Content-Disposition").isDefined should be(true)
          }
        }
      }

      "POST /api/datasets/:dataset_id/attributes/import" in {
        session {
          signIn()
          val datasetId = createDataset()
          // UUIDチェック(dataset_id)
          block {
            val generator = (x: String) => s"/api/datasets/${x}/attributes/import"
            val file = Map("file" -> nonZeroByteCsv)
            uuidCheckForUrl(POST, generator, datasetId, Map.empty, file)
          }
          // ファイルチェック(file)
          block {
            val file = Map("file" -> zeroByteCsv)
            post(s"/api/datasets/${datasetId}/attributes/import", Map.empty, file) {
              parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument")
            }
          }
          block {
            val file = Map("file" -> nonZeroByteCsv)
            post(s"/api/datasets/${datasetId}/attributes/import", Map.empty, file) {
              parse(body).extract[AjaxResponse[Any]].status should be("OK")
            }
          }
        }
      }

      "POST /api/datasets/:dataset_id/copy" in {
        session {
          signIn()
          val datasetId = createDataset()
          // UUIDチェック(dataset_id)
          block {
            val generator = (x: String) => s"/api/datasets/${x}/copy"
            uuidCheckForUrl(POST, generator, datasetId)
          }
        }
      }

      "DELETE /api/datasets/:dataset_id" in {
        session {
          signIn()
          val datasetId = createDataset()
          // UUIDチェック(dataset_id)
          block {
            val generator = (x: String) => s"/api/datasets/${x}"
            uuidCheckForUrl(DELETE, generator, datasetId)
          }
        }
      }

      "GET /api/datasets/:dataset_id/files" in {
        session {
          signIn()
          val datasetId = createDataset()
          // JSONフォーマット
          jsonFormatOnlyCheck(GET, s"/api/datasets/${datasetId}/files")
          // UUIDチェック(dataset_id)
          block {
            val generator = (x: String) => s"/api/datasets/${x}/files"
            uuidCheckForUrl(GET, generator, datasetId)
          }
          // 数値チェック(limit)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("limit" -> x))))
            nonMinusIntCheck(GET, s"/api/datasets/${datasetId}/files", generator)
          }
          // 数値チェック(offset)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("offset" -> x))))
            nonMinusIntCheck(GET, s"/api/datasets/${datasetId}/files", generator)
          }
        }
      }

      "POST /api/datasets/:dataset_id/files" in {
        session {
          signIn()
          val datasetId = createDataset()
          // JSONフォーマット
          block {
            jsonFormatCheck(POST, s"/api/datasets/${datasetId}/files")
          }
          // UUIDチェック(dataset_id)
          block {
            val generator = (x: String) => s"/api/datasets/${x}/files"
            val files = Map("files" -> nonZeroByteImage)
            uuidCheckForUrl(POST, generator, datasetId, Map.empty, files)
          }
          // 長さチェック
          block {
            val generator = (x: File) => Map("files" -> x)
            nonZeroLengthCheckForFile(POST, s"/api/datasets/${datasetId}/files", nonZeroByteImage, Map.empty, generator)
          }
          // ファイル(0byte)
          block {
            val generator = (x: File) => Map("files" -> x)
            fileCheck(POST, s"/api/datasets/${datasetId}/files", generator)
          }
        }
      }

      "PUT /api/datasets/:dataset_id/guest_access" in {
        session {
          signIn()
          val datasetId = createDataset()
          // JSONフォーマット
          jsonFormatCheck(PUT, s"/api/datasets/${datasetId}/guest_access")
          // UUIDチェック(dataset_id)
          block {
            val generator = (x: String) => s"/api/datasets/${x}/guest_access"
            val params = Map("d" -> compact(render(("accessLevel" -> JInt(2)))))
            uuidCheckForUrl(PUT, generator, datasetId, params)
          }
          // 省略不可能パラメータ省略(accessLevel)
          block {
            val params = Map("d" -> "{}")
            requireCheck(PUT, s"/api/datasets/${datasetId}/guest_access", params)
          }
          // 正常範囲チェック(accessLevel)
          block {
            val params = Map("d" -> compact(render(("accessLevel" -> ""))))
            put(s"/api/datasets/${datasetId}/guest_access", params) {
              parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument")
            }
          }
          block {
            val params = Map("d" -> compact(render(("accessLevel" -> JInt(-1)))))
            put(s"/api/datasets/${datasetId}/guest_access", params) {
              parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument")
            }
          }
          block {
            val params = Map("d" -> compact(render(("accessLevel" -> JInt(3)))))
            put(s"/api/datasets/${datasetId}/guest_access", params) {
              parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument")
            }
          }
        }
      }

      "PUT /api/datasets/:dataset_id/images/featured" in {
        session {
          signIn()
          val datasetId = createDataset()
          val imageId = AppConf.defaultDatasetImageId
          // JSONフォーマット
          jsonFormatCheck(PUT, s"/api/datasets/${datasetId}/images/featured")
          // UUIDチェック(dataset_id)
          block {
            val generator = (x: String) => s"/api/datasets/${x}/images/featured"
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            uuidCheckForUrl(PUT, generator, datasetId, params)
          }
          // 省略不可能パラメータ省略(imageId)
          block {
            val params = Map("d" -> "{}")
            requireCheck(PUT, s"/api/datasets/${datasetId}/images/featured", params)
          }
          // 空文字不許可チェック(imageId)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("imageId" -> x))))
            nonEmptyCheck(PUT, s"/api/datasets/${datasetId}/images/featured", imageId, generator)
          }
          // UUIDチェック(imageId)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("imageId" -> x))))
            uuidCheckForForm(PUT, s"/api/datasets/${datasetId}/images/featured", imageId, generator)
          }
        }
      }

      "GET /api/datasets/:dataset_id/images" in {
        session {
          signIn()
          val datasetId = createDataset()
          // JSONフォーマット
          jsonFormatOnlyCheck(GET, s"/api/datasets/${datasetId}/images")
          // UUIDチェック(dataset_id)
          block {
            val generator = (x: String) => s"/api/datasets/${x}/images"
            uuidCheckForUrl(GET, generator, datasetId)
          }
          // 数値チェック(limit)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("limit" -> x))))
            nonMinusIntCheck(GET, s"/api/datasets/${datasetId}/images", generator)
          }
          // 数値チェック(offset)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("offset" -> x))))
            nonMinusIntCheck(GET, s"/api/datasets/${datasetId}/images", generator)
          }
        }
      }

      "PUT /api/datasets/:dataset_id/images/primary" in {
        session {
          signIn()
          val datasetId = createDataset()
          val imageId = AppConf.defaultDatasetImageId
          // JSONフォーマット
          jsonFormatCheck(PUT, s"/api/datasets/${datasetId}/images/primary")
          // UUIDチェック(dataset_id)
          block {
            val generator = (x: String) => s"/api/datasets/${x}/images/primary"
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            uuidCheckForUrl(PUT, generator, datasetId, params)
          }
          // 省略不可能パラメータ省略(imageId)
          block {
            val params = Map("d" -> "{}")
            requireCheck(PUT, s"/api/datasets/${datasetId}/images/primary", params)
          }
          // 空文字不許可チェック(imageId)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("imageId" -> x))))
            nonEmptyCheck(PUT, s"/api/datasets/${datasetId}/images/primary", imageId, generator)
          }
          // UUIDチェック(imageId)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("imageId" -> x))))
            uuidCheckForForm(PUT, s"/api/datasets/${datasetId}/images/primary", imageId, generator)
          }
        }
      }

      "POST /api/datasets/:dataset_id/images" in {
        session {
          signIn()
          val datasetId = createDataset()
          // JSONフォーマット
          block {
            jsonFormatCheck(POST, s"/api/datasets/${datasetId}/images")
          }
          // UUIDチェック(dataset_id)
          block {
            val generator = (x: String) => s"/api/datasets/${x}/images"
            val files = Map("images" -> nonZeroByteImage)
            uuidCheckForUrl(POST, generator, datasetId, Map.empty, files)
          }
          // 長さチェック
          block {
            val generator = (x: File) => Map("images" -> x)
            nonZeroLengthCheckForFile(POST, s"/api/datasets/${datasetId}/images", nonZeroByteImage, Map.empty, generator)
          }
          // ファイル(0byte)
          block {
            val generator = (x: File) => Map("images" -> x)
            fileCheck(POST, s"/api/datasets/${datasetId}/images", generator)
          }
        }
      }

      "PUT /api/datasets/:dataset_id/metadata" in {
        session {
          signIn()
          val datasetId = createDataset()
          val imageId = AppConf.defaultDatasetImageId
          // JSONフォーマット
          jsonFormatCheck(PUT, s"/api/datasets/${datasetId}/metadata")
          // UUIDチェック(dataset_id)
          block {
            val generator = (x: String) => s"/api/datasets/${x}/metadata"
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> "") ~ ("license" -> AppConf.defaultLicenseId) ~ ("attributes" -> Seq()))))
            uuidCheckForUrl(PUT, generator, datasetId, params)
          }
          // 省略不可能パラメータ省略(name)
          block {
            val params = Map("d" -> compact(render(("description" -> "") ~ ("license" -> AppConf.defaultLicenseId) ~ ("attributes" -> Seq()))))
            requireCheck(PUT, s"/api/datasets/${datasetId}/metadata", params)
          }
          // 省略不可能パラメータ省略(description)
          block {
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("license" -> AppConf.defaultLicenseId) ~ ("attributes" -> Seq()))))
            requireCheck(PUT, s"/api/datasets/${datasetId}/metadata", params)
          }
          // 省略不可能パラメータ省略(license)
          block {
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> "") ~ ("attributes" -> Seq()))))
            requireCheck(PUT, s"/api/datasets/${datasetId}/metadata", params)
          }
          // json4sでは、Listの要素がListの型に変換できない場合、その要素をなかったことにするため、テストできない
          //          // 省略不可能パラメータ省略(attribute.name)
          //          block {
          //            val params = Map("d" -> compact(render(
          //              ("name" -> "test1") ~
          //              ("description" -> "") ~
          //              ("license" -> AppConf.defaultLicenseId) ~
          //              ("attributes" -> Seq(("value" -> "v1")))
          //            )))
          //            requireCheck(PUT, s"/api/datasets/${datasetId}/metadata", params)
          //          }
          //          // 省略不可能パラメータ省略(attribute.value)
          //          block {
          //            val params = Map("d" -> compact(render(
          //              ("name" -> "test1") ~
          //              ("description" -> "") ~
          //              ("license" -> AppConf.defaultLicenseId) ~
          //              ("attributes" -> Seq(("name" -> "name1")))
          //            )))
          //            requireCheck(PUT, s"/api/datasets/${datasetId}/metadata", params)
          //          }
          // 空文字不許可チェック(name)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("name" -> x) ~ ("description" -> "") ~ ("license" -> AppConf.defaultLicenseId) ~ ("attributes" -> Seq()))))
            nonEmptyCheck(PUT, s"/api/datasets/${datasetId}/metadata", "test1", generator)
          }
          // 空文字不許可チェック(attribute.name)
          block {
            val generator = (x: String) => Map("d" -> compact(render(
              ("name" -> "test1") ~
                ("description" -> "") ~
                ("license" -> AppConf.defaultLicenseId) ~
                ("attributes" -> Seq(("name" -> x) ~ ("value" -> "v1")))
            )))
            nonEmptyCheck(PUT, s"/api/datasets/${datasetId}/metadata", "name1", generator)
          }
          // 空文字不許可チェック(attribute.value)
          block {
            val generator = (x: String) => Map("d" -> compact(render(
              ("name" -> "test1") ~
                ("description" -> "") ~
                ("license" -> AppConf.defaultLicenseId) ~
                ("attributes" -> Seq(("name" -> "name1") ~ ("value" -> x)))
            )))
            nonEmptyCheck(PUT, s"/api/datasets/${datasetId}/metadata", "v1", generator)
          }
          // UUIDチェック(license)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> "") ~ ("license" -> x) ~ ("attributes" -> Seq()))))
            uuidCheckForForm(PUT, s"/api/datasets/${datasetId}/metadata", AppConf.defaultLicenseId, generator)
          }
          // featured属性二つ
          block {
            val params = Map("d" -> compact(render(
              ("name" -> "test1") ~
                ("description" -> "") ~
                ("license" -> AppConf.defaultLicenseId) ~
                ("attributes" -> Seq(("name" -> "featured") ~ ("value" -> "v1"), ("name" -> "featured") ~ ("value" -> "v2")))
            )))
            put(s"/api/datasets/${datasetId}/metadata", params) {
              parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument")
            }
          }
        }
      }

      "PUT /api/datasets/:dataset_id/storage" in {
        session {
          signIn()
          val datasetId = createDataset()
          val imageId = AppConf.defaultDatasetImageId
          // JSONフォーマット
          jsonFormatCheck(PUT, s"/api/datasets/${datasetId}/storage")
          // UUIDチェック(dataset_id)
          block {
            val generator = (x: String) => s"/api/datasets/${x}/storage"
            val params = Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> JBool(false)))))
            uuidCheckForUrl(PUT, generator, datasetId, params)
          }
          // 省略不可能パラメータ省略(saveLocal)
          block {
            val params = Map("d" -> compact(render(("saveS3" -> JBool(false)))))
            requireCheck(PUT, s"/api/datasets/${datasetId}/storage", params)
          }
          // 省略不可能パラメータ省略(saveS3)
          block {
            val params = Map("d" -> compact(render(("saveLocal" -> JBool(true)))))
            requireCheck(PUT, s"/api/datasets/${datasetId}/storage", params)
          }
          // booleanチェック(saveLocal)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("saveLocal" -> x) ~ ("saveS3" -> JBool(true)))))
            booleanCheckForJson(PUT, s"/api/datasets/${datasetId}/storage", generator)
          }
          // booleanチェック(saveS3)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> x))))
            booleanCheckForJson(PUT, s"/api/datasets/${datasetId}/storage", generator)
          }
        }
      }

      "DELETE /api/datasets/:dataset_id/files/:file_id" in {
        session {
          signIn()
          val datasetId = createDataset()
          val fileId1 = getFileId(datasetId, nonZeroByteImage)
          // UUIDチェック(dataset_id)
          block {
            val generator = (x: String) => s"/api/datasets/${x}/files/${fileId1}"
            uuidCheckForUrl(DELETE, generator, datasetId, Map.empty)
          }
          val fileId2 = getFileId(datasetId, nonZeroByteImage)
          // UUIDチェック(file_id)
          block {
            val generator = (x: String) => s"/api/datasets/${datasetId}/files/${x}"
            uuidCheckForUrl(DELETE, generator, fileId2, Map.empty)
          }
        }
      }

      "PUT /api/datasets/:dataset_id/files/:file_id/metadata" in {
        session {
          signIn()
          val datasetId = createDataset()
          val fileId = getFileId(datasetId, nonZeroByteImage)
          // JSONフォーマット
          jsonFormatCheck(PUT, s"/api/datasets/${datasetId}/files/${fileId}/metadata")
          // UUIDチェック(dataset_id)
          block {
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> ""))))
            val generator = (x: String) => s"/api/datasets/${x}/files/${fileId}/metadata"
            uuidCheckForUrl(PUT, generator, datasetId, params)
          }
          // UUIDチェック(file_id)
          block {
            val params = Map("d" -> compact(render(("name" -> "test1") ~ ("description" -> ""))))
            val generator = (x: String) => s"/api/datasets/${datasetId}/files/${x}/metadata"
            uuidCheckForUrl(PUT, generator, fileId, params)
          }
          // 省略不可能パラメータ省略(name)
          block {
            val params = Map("d" -> compact(render(("description" -> ""))))
            requireCheck(PUT, s"/api/datasets/${datasetId}/files/${fileId}/metadata", params)
          }
          // 省略不可能パラメータ省略(description)
          block {
            val params = Map("d" -> compact(render(("name" -> "test1"))))
            requireCheck(PUT, s"/api/datasets/${datasetId}/files/${fileId}/metadata", params)
          }
          // 空文字不許可チェック(name)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("name" -> x) ~ ("description" -> ""))))
            nonEmptyCheck(PUT, s"/api/datasets/${datasetId}/files/${fileId}/metadata", "test1", generator)
          }
        }
      }

      "POST /api/datasets/:dataset_id/files/:file_id" in {
        session {
          signIn()
          val datasetId = createDataset()
          val fileId = getFileId(datasetId, nonZeroByteImage)
          // JSONフォーマット
          jsonFormatCheck(POST, s"/api/datasets/${datasetId}/files/${fileId}")
          // UUIDチェック(dataset_id)
          block {
            val files = Map("file" -> nonZeroByteImage)
            val generator = (x: String) => s"/api/datasets/${x}/files/${fileId}"
            uuidCheckForUrl(POST, generator, datasetId, Map.empty, files)
          }
          // UUIDチェック(file_id)
          block {
            val files = Map("file" -> nonZeroByteImage)
            val generator = (x: String) => s"/api/datasets/${datasetId}/files/${x}"
            uuidCheckForUrl(POST, generator, fileId, Map.empty, files)
          }
          // ファイル(0byte)
          block {
            val generator = (x: File) => Map("file" -> x)
            fileCheck(POST, s"/api/datasets/${datasetId}/files/${fileId}", generator)
          }
        }
      }

      "GET /api/datasets/:dataset_id/files/:file_id/zippedfiles" in {
        session {
          signIn()
          val datasetId = createDataset()
          val fileId = getFileId(datasetId, dummyZipFile)
          // JSONフォーマット
          jsonFormatOnlyCheck(GET, s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles")
          // UUIDチェック(dataset_id)
          block {
            val generator = (x: String) => s"/api/datasets/${x}/files/${fileId}/zippedfiles"
            uuidCheckForUrl(GET, generator, datasetId)
          }
          // UUIDチェック(file_id)
          block {
            val generator = (x: String) => s"/api/datasets/${datasetId}/files/${x}/zippedfiles"
            uuidCheckForUrl(GET, generator, fileId)
          }
          // 数値チェック(limit)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("limit" -> x))))
            nonMinusIntCheck(GET, s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", generator)
          }
          // 数値チェック(offset)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("offset" -> x))))
            nonMinusIntCheck(GET, s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", generator)
          }
        }
      }

      "DELETE /api/datasets/:dataset_id/images/:image_id" in {
        session {
          signIn()
          val datasetId = createDataset()
          val imageId1 = getDatasetImageId(datasetId)
          // UUIDチェック(dataset_id)
          block {
            val generator = (x: String) => s"/api/datasets/${x}/images/${imageId1}"
            uuidCheckForUrl(DELETE, generator, datasetId, Map.empty)
          }
          val imageId2 = getDatasetImageId(datasetId)
          // UUIDチェック(image_id)
          block {
            val generator = (x: String) => s"/api/datasets/${datasetId}/images/${x}"
            uuidCheckForUrl(DELETE, generator, imageId2, Map.empty)
          }
        }
      }
    }

    "group" - {
      "GET /api/groups" in {
        session {
          signIn()
          // JSONフォーマット
          jsonFormatOnlyCheck(GET, s"/api/groups")
          // UUIDチェック(user)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("user" -> x))))
            uuidCheckForForm(GET, s"/api/groups", testUserId, generator)
          }
          // 数値チェック(limit)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("limit" -> x))))
            nonMinusIntCheck(GET, s"/api/groups", generator)
          }
          // 数値チェック(offset)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("offset" -> x))))
            nonMinusIntCheck(GET, s"/api/groups", generator)
          }
        }
      }

      "POST /api/groups" in {
        session {
          signIn()
          // JSONフォーマット
          jsonFormatCheck(POST, s"/api/groups")
          // 省略不可能パラメータ省略(name)
          block {
            val params = Map("d" -> compact(render(("description" -> ""))))
            requireCheck(POST, s"/api/groups", params)
          }
          // 省略不可能パラメータ省略(description)
          block {
            val params = Map("d" -> compact(render(("name" -> "test1"))))
            requireCheck(POST, s"/api/groups", params)
          }
          // 空文字不許可チェック(name)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("name" -> x) ~ ("description" -> ""))))
            nonEmptyCheck(POST, s"/api/groups", "test1", generator)
          }
        }
      }

      "DELETE /api/groups/:group_id" in {
        session {
          signIn()
          val groupId = createGroup()
          // UUIDチェック(groupId)
          block {
            val generator = (x: String) => s"/api/groups/${x}"
            uuidCheckForUrl(DELETE, generator, groupId, Map.empty)
          }
        }
      }

      "GET /api/groups/:group_id" in {
        session {
          signIn()
          val groupId = createGroup()
          // UUIDチェック(groupId)
          block {
            val generator = (x: String) => s"/api/groups/${x}"
            uuidCheckForUrl(GET, generator, groupId, Map.empty)
          }
        }
      }

      "GET /api/groups/:group_id/images" in {
        session {
          signIn()
          val groupId = createGroup()
          jsonFormatOnlyCheck(GET, s"/api/groups/${groupId}/images")
          // UUIDチェック(groupId)
          block {
            val generator = (x: String) => s"/api/groups/${x}/images"
            uuidCheckForUrl(GET, generator, groupId, Map.empty)
          }
          // 数値チェック(limit)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("limit" -> x))))
            nonMinusIntCheck(GET, s"/api/groups/${groupId}/images", generator)
          }
          // 数値チェック(offset)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("offset" -> x))))
            nonMinusIntCheck(GET, s"/api/groups/${groupId}/images", generator)
          }
        }
      }

      "POST /api/groups/:group_id/images" in {
        session {
          signIn()
          val groupId = createGroup()
          // UUIDチェック(groupId)
          block {
            val generator = (x: String) => s"/api/groups/${x}/images"
            val images = Map("images" -> nonZeroByteImage)
            uuidCheckForUrl(POST, generator, groupId, Map.empty, images)
          }
          // 長さチェック
          block {
            val generator = (x: File) => Map("images" -> x)
            nonZeroLengthCheckForFile(POST, s"/api/groups/${groupId}/images", nonZeroByteImage, Map.empty, generator)
          }
          // ファイル(0byte)
          block {
            val generator = (x: File) => Map("images" -> x)
            fileCheck(POST, s"/api/groups/${groupId}/images", generator)
          }
        }
      }

      "PUT /api/groups/:group_id/images/primary" in {
        session {
          signIn()
          val groupId = createGroup()
          val imageId = AppConf.defaultGroupImageId
          // JSONフォーマット
          jsonFormatCheck(PUT, s"/api/groups/${groupId}/images/primary")
          // UUIDチェック(groupId)
          block {
            val generator = (x: String) => s"/api/groups/${x}/images/primary"
            val params = Map("d" -> compact(render(("imageId" -> imageId))))
            uuidCheckForUrl(PUT, generator, groupId, params)
          }
          // 省略不可能パラメータ省略(imageId)
          block {
            val params = Map("d" -> "{}")
            requireCheck(PUT, s"/api/groups/${groupId}/images/primary", params)
          }
          // 空文字不許可チェック(imageId)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("imageId" -> x))))
            nonEmptyCheck(PUT, s"/api/groups/${groupId}/images/primary", imageId, generator)
          }
          // UUIDチェック(imageId)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("imageId" -> x))))
            uuidCheckForForm(PUT, s"/api/groups/${groupId}/images/primary", imageId, generator)
          }
        }
      }

      "GET /api/groups/:group_id/members" in {
        session {
          signIn()
          val groupId = createGroup()
          jsonFormatOnlyCheck(GET, s"/api/groups/${groupId}/members")
          // UUIDチェック(groupId)
          block {
            val generator = (x: String) => s"/api/groups/${x}/members"
            uuidCheckForUrl(GET, generator, groupId, Map.empty)
          }
          // 数値チェック(limit)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("limit" -> x))))
            nonMinusIntCheck(GET, s"/api/groups/${groupId}/members", generator)
          }
          // 数値チェック(offset)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("offset" -> x))))
            nonMinusIntCheck(GET, s"/api/groups/${groupId}/members", generator)
          }
        }
      }

      "POST /api/groups/:group_id/members" in {
        session {
          signIn()
          val groupId = createGroup()
          // JSONフォーマット
          jsonFormatCheck(POST, s"/api/groups/${groupId}/members")
          // UUIDチェック(groupId)
          block {
            val generator = (x: String) => s"/api/groups/${x}/members"
            val params = Map("d" -> compact(render(Seq(("userId" -> testUserId) ~ ("role" -> JInt(1))))))
            uuidCheckForUrl(POST, generator, groupId, params)
          }
          // 長さチェック
          block {
            val generator = (x: Seq[JValue]) => Map("d" -> compact(render(x)))
            val valid = Seq(("userId" -> testUserId) ~ ("role" -> JInt(1)))
            nonZeroLengthCheckForParam(POST, s"/api/groups/${groupId}/members", valid, generator)
          }
          // 省略不可能パラメータ省略(userId)
          block {
            val params = Map("d" -> compact(render(Seq(("role" -> JInt(1))))))
            requireCheck(POST, s"/api/groups/${groupId}/members", params)
          }
          // 省略不可能パラメータ省略(role)
          block {
            val params = Map("d" -> compact(render(Seq(("role" -> JInt(1))))))
            requireCheck(POST, s"/api/groups/${groupId}/members", params)
          }
          // 空文字不許可チェック(userId)
          block {
            val generator = (x: String) => Map("d" -> compact(render(Seq(("userId" -> x) ~ ("role" -> JInt(1))))))
            nonEmptyCheck(POST, s"/api/groups/${groupId}/members", testUserId, generator)
          }
          // UUIDチェック(id)
          block {
            val generator = (x: String) => Map("d" -> compact(render(Seq(("userId" -> x) ~ ("role" -> JInt(1))))))
            uuidCheckForForm(POST, s"/api/groups/${groupId}/members", testUserId, generator)
          }
          // 正常範囲チェック(role)
          block {
            val params = Map("d" -> compact(render(Seq(("userId" -> testUserId) ~ ("role" -> "")))))
            post(s"/api/groups/${groupId}/members", params) {
              parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument")
            }
          }
          block {
            val params = Map("d" -> compact(render(Seq(("userId" -> testUserId) ~ ("role" -> JInt(-1))))))
            post(s"/api/groups/${groupId}/members", params) {
              parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument")
            }
          }
        }
      }

      "PUT /api/groups/:group_id/members/:user_id" in {
        session {
          signIn()
          val groupId = createGroup()
          addMember(groupId, dummyUserId)
          // JSONフォーマット
          jsonFormatCheck(PUT, s"/api/groups/${groupId}/members/${dummyUserId}")
          // UUIDチェック(groupId)
          block {
            val generator = (x: String) => s"/api/groups/${x}/members/${dummyUserId}"
            val params = Map("d" -> compact(render(("role" -> JInt(1)))))
            uuidCheckForUrl(PUT, generator, groupId, params)
          }
          // UUIDチェック(userId)
          block {
            val generator = (x: String) => s"/api/groups/${groupId}/members/${x}"
            val params = Map("d" -> compact(render(("role" -> JInt(1)))))
            uuidCheckForUrl(PUT, generator, dummyUserId, params)
          }
          // 省略不可能パラメータ省略(role)
          block {
            val params = Map("d" -> "{}")
            requireCheck(PUT, s"/api/groups/${groupId}/members/${dummyUserId}", params)
          }
          // 正常範囲チェック(role)
          block {
            val params = Map("d" -> compact(render(("role" -> ""))))
            put(s"/api/groups/${groupId}/members/${dummyUserId}", params) {
              parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument")
            }
          }
          block {
            val params = Map("d" -> compact(render(("role" -> JInt(-1)))))
            put(s"/api/groups/${groupId}/members/${dummyUserId}", params) {
              parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument")
            }
          }
        }
      }

      "DELETE /api/groups/:group_id/members/:user_id" in {
        session {
          signIn()
          val groupId = createGroup()
          addMember(groupId, dummyUserId)
          // UUIDチェック(groupId)
          block {
            val generator = (x: String) => s"/api/groups/${x}/members/${dummyUserId}"
            uuidCheckForUrl(DELETE, generator, groupId, Map.empty)
          }

          // グループメンバーの削除は物理削除となったため、再追加が必要
          addMember(groupId, dummyUserId)

          // UUIDチェック(userId)
          block {
            val generator = (x: String) => s"/api/groups/${groupId}/members/${x}"
            uuidCheckForUrl(DELETE, generator, dummyUserId, Map.empty)
          }
        }
      }

      "DELETE /api/groups/:group_id/images/:image_id" in {
        session {
          signIn()
          val groupId = createGroup()
          val imageId1 = getGroupImageId(groupId)
          // UUIDチェック(groupId)
          block {
            val generator = (x: String) => s"/api/groups/${x}/images/${imageId1}"
            uuidCheckForUrl(DELETE, generator, groupId, Map.empty)
          }
          val imageId2 = getGroupImageId(groupId)
          // UUIDチェック(imageId)
          block {
            val generator = (x: String) => s"/api/groups/${groupId}/images/${x}"
            uuidCheckForUrl(DELETE, generator, imageId2, Map.empty)
          }
        }
      }

      "PUT /api/groups/:group_id" in {
        session {
          signIn()
          val groupId = createGroup()
          // JSONフォーマット
          jsonFormatCheck(PUT, s"/api/groups/${groupId}")
          // UUIDチェック(groupId)
          block {
            val generator = (x: String) => s"/api/groups/${x}"
            val params = Map("d" -> compact(render(("name" -> "group1") ~ ("description" -> "desc1"))))
            uuidCheckForUrl(PUT, generator, groupId, params)
          }
          // 省略不可能パラメータ省略(name)
          block {
            val params = Map("d" -> compact(render(("description" -> "desc1"))))
            requireCheck(PUT, s"/api/groups/${groupId}", params)
          }
          // 省略不可能パラメータ省略(description)
          block {
            val params = Map("d" -> compact(render(("name" -> "group1"))))
            requireCheck(PUT, s"/api/groups/${groupId}", params)
          }
          // 空文字不許可チェック(name)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("name" -> x) ~ ("description" -> "desc1"))))
            nonEmptyCheck(PUT, s"/api/groups/${groupId}", "name1", generator)
          }
        }
      }

    }

    "suggest" - {
      "GET /api/suggests/attributes" in {
        session {
          signIn()
          jsonFormatOnlyCheck(GET, s"/api/suggests/attributes")
          // 数値チェック(limit)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("limit" -> x))))
            nonMinusIntCheck(GET, s"/api/suggests/attributes", generator)
          }
          // 数値チェック(offset)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("offset" -> x))))
            nonMinusIntCheck(GET, s"/api/suggests/attributes", generator)
          }
        }
      }

      "GET /api/suggests/groups" in {
        session {
          signIn()
          jsonFormatOnlyCheck(GET, s"/api/suggests/groups")
          // 数値チェック(limit)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("limit" -> x))))
            nonMinusIntCheck(GET, s"/api/suggests/groups", generator)
          }
          // 数値チェック(offset)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("offset" -> x))))
            nonMinusIntCheck(GET, s"/api/suggests/groups", generator)
          }
        }
      }

      "GET /api/suggests/users" in {
        session {
          signIn()
          jsonFormatOnlyCheck(GET, s"/api/suggests/users")
          // 数値チェック(limit)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("limit" -> x))))
            nonMinusIntCheck(GET, s"/api/suggests/users", generator)
          }
          // 数値チェック(offset)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("offset" -> x))))
            nonMinusIntCheck(GET, s"/api/suggests/users", generator)
          }
        }
      }

      "GET /api/suggests/users_and_groups" in {
        session {
          signIn()
          jsonFormatOnlyCheck(GET, s"/api/suggests/users_and_groups")
          // 数値チェック(limit)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("limit" -> x))))
            nonMinusIntCheck(GET, s"/api/suggests/users_and_groups", generator)
          }
          // 数値チェック(offset)
          block {
            val generator = (x: JValue) => Map("d" -> compact(render(("offset" -> x))))
            nonMinusIntCheck(GET, s"/api/suggests/users_and_groups", generator)
          }
          // UUIDチェック(excludeIds)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("excludeIds" -> Seq(x)))))
            uuidCheckForForm(GET, s"/api/suggests/users_and_groups", testUserId, generator)
          }
        }
      }
    }

    "others" - {
      "GET /api/statistics" in {
        session {
          signIn()
          jsonFormatOnlyCheck(GET, s"/api/statistics")
          // 日付チェック(from)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("from" -> x))))
            dateCheck(GET, s"/api/statistics", generator)
          }
          // 日付チェック(to)
          block {
            val generator = (x: String) => Map("d" -> compact(render(("to" -> x))))
            dateCheck(GET, s"/api/statistics", generator)
          }
        }
      }

      "GET /api/tasks/:task_id" in {
        session {
          signIn()
          val datasetId = createDataset()
          val taskId = createDatasetTask(datasetId)
          // UUIDチェック(taskId)
          block {
            val generator = (x: String) => s"/api/tasks/${x}"
            uuidCheckForUrl(GET, generator, taskId, Map.empty)
          }
        }
      }
    }
  }

  /**
   * Httpメソッドを表す型
   */
  sealed trait HttpMethod

  /**
   * GETメソッドを表す型
   */
  case object GET extends HttpMethod

  /**
   * PUTメソッドを表す型
   */
  case object PUT extends HttpMethod

  /**
   * POSTメソッドを表す型
   */
  case object POST extends HttpMethod

  /**
   * DELETEメソッドを表す型
   */
  case object DELETE extends HttpMethod

  /**
   * JSON未指定、JSONフォーマット不正のケースをテストします。
   *
   * @param method Httpメソッド
   * @param url テストするAPIのURL
   * @return テスト結果
   */
  private def jsonFormatCheck(method: HttpMethod, url: String) = {
    val params = Map("d" -> "hoge")
    method match {
      case GET => {
        get(url) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        get(url, params) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
      case PUT => {
        put(url) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(url, params) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
      case POST => {
        post(url) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(url, params) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
      case DELETE => {
        delete(url) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        delete(url, params) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
    }
  }

  /**
   * JSONフォーマット不正のケースのみをテストします。
   *
   * @param method Httpメソッド
   * @param url テストするAPIのURL
   * @return テスト結果
   */
  private def jsonFormatOnlyCheck(method: HttpMethod, url: String) = {
    val params = Map("d" -> "null")
    method match {
      case GET => {
        get(url, params) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
      case PUT => {
        put(url, params) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
      case POST => {
        post(url, params) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
      case DELETE => {
        delete(url, params) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
    }
  }

  /**
   * 項目の必須チェックをテストします。
   *
   * @param method Httpメソッド
   * @param url テストするAPIのURL
   * @param params APIのFORMパラメータ
   * @param files APIのFORMパラメータ(file)
   * @return テスト結果
   */
  private def requireCheck(method: HttpMethod, url: String, params: Iterable[(String, String)], files: Iterable[(String, Any)] = Map.empty) = {
    method match {
      case GET => get(url, params) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      case PUT => put(url, params, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      case POST => post(url, params, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      case DELETE => delete(url, params) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
    }
  }

  /**
   * 項目の空文字チェックをテストします。
   *
   * @param method Httpメソッド
   * @param url テストするAPIのURL
   * @param valid 正常なケースで使用する値
   * @param paramGenerator APIのFORMパラメータを生成するための関数
   * @param files APIのFORMパラメータ(file)
   * @return テスト結果
   */
  private def nonEmptyCheck(method: HttpMethod, url: String, valid: String, paramGenerator: String => Iterable[(String, String)], files: Iterable[(String, Any)] = Map.empty) = {
    val zeroSpace = paramGenerator("")
    val nonZeroSpace = paramGenerator("   ")
    val validParams = paramGenerator(valid)
    method match {
      case GET => {
        get(url, zeroSpace) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        get(url, nonZeroSpace) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        get(url, validParams) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
      case PUT => {
        put(url, zeroSpace, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(url, nonZeroSpace, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(url, validParams, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
      case POST => {
        post(url, zeroSpace, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(url, nonZeroSpace, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(url, validParams, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
      case DELETE => {
        delete(url, zeroSpace) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        delete(url, nonZeroSpace) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        delete(url, validParams) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
    }
  }

  /**
   * 項目のfileチェックをテストします。
   *
   * @param method Httpメソッド
   * @param url テストするAPIのURL
   * @param paramGenerator APIのFORMパラメータ(file)を生成するための関数
   * @param params APIのFORMパラメータ
   * @return テスト結果
   */
  private def fileCheck(method: HttpMethod, url: String, paramGenerator: File => Iterable[(String, Any)], params: Iterable[(String, String)] = Map.empty) = {
    val nonZeroByteParam = paramGenerator(nonZeroByteImage)
    val zeroByteParam = paramGenerator(zeroByteImage)
    method match {
      case PUT => {
        put(url, params, zeroByteParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(url, params, nonZeroByteParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
      case POST => {
        post(url, params, zeroByteParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(url, params, nonZeroByteParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
      case _ => throw new Exception("サポートしていない操作です")
    }
  }

  /**
   * 項目のbooleanチェック(JSON項目向け)をテストします。
   *
   * @param method Httpメソッド
   * @param url テストするAPIのURL
   * @param paramGenerator APIのFORMパラメータを生成するための関数
   * @param files APIのFORMパラメータ(file)
   * @return テスト結果
   */
  private def booleanCheckForJson(method: HttpMethod, url: String, paramGenerator: JValue => Iterable[(String, String)], files: Iterable[(String, Any)] = Map.empty) = {
    val zeroSpaceParam = paramGenerator("")
    val trueParam = paramGenerator(JBool(true))
    val falseParam = paramGenerator(JBool(false))
    val zeroParam = paramGenerator("0")
    val oneParam = paramGenerator("1")
    val fullTrueParam = paramGenerator("ｔｒｕｅ")
    val fullFalseParam = paramGenerator("ｆａｌｓｅ")

    method match {
      case GET => {
        get(url, zeroSpaceParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        get(url, trueParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        get(url, falseParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        get(url, zeroParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        get(url, oneParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        get(url, fullTrueParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        get(url, fullFalseParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
      case PUT => {
        put(url, zeroSpaceParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(url, trueParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        put(url, falseParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        put(url, zeroParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(url, oneParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(url, fullTrueParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(url, fullFalseParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
      case POST => {
        post(url, zeroSpaceParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(url, trueParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        post(url, falseParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        post(url, zeroParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(url, oneParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(url, fullTrueParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(url, fullFalseParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
      case DELETE => {
        delete(url, zeroSpaceParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        delete(url, trueParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        delete(url, falseParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        delete(url, zeroParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        delete(url, oneParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        delete(url, fullTrueParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        delete(url, fullFalseParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
    }
  }

  /**
   * 項目のbooleanチェック(FORMパラメータに直に入ってくる場合向け)をテストします。
   *
   * @param method Httpメソッド
   * @param url テストするAPIのURL
   * @param paramGenerator APIのFORMパラメータを生成するための関数
   * @param files APIのFORMパラメータ(file)
   * @return テスト結果
   */
  private def booleanCheckForForm(method: HttpMethod, url: String, paramGenerator: String => Iterable[(String, String)], files: Iterable[(String, Any)] = Map.empty) = {
    val zeroSpaceParam = paramGenerator("")
    val trueParam = paramGenerator("true")
    val falseParam = paramGenerator("false")
    val zeroParam = paramGenerator("0")
    val oneParam = paramGenerator("1")
    val fullTrueParam = paramGenerator("ｔｒｕｅ")
    val fullFalseParam = paramGenerator("ｆａｌｓｅ")

    method match {
      case GET => {
        get(url, zeroSpaceParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        get(url, trueParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        get(url, falseParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        get(url, zeroParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        get(url, oneParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        get(url, fullTrueParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        get(url, fullFalseParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
      case PUT => {
        put(url, zeroSpaceParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(url, trueParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        put(url, falseParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        put(url, zeroParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(url, oneParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(url, fullTrueParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(url, fullFalseParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
      case POST => {
        post(url, zeroSpaceParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(url, trueParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        post(url, falseParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        post(url, zeroParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(url, oneParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(url, fullTrueParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(url, fullFalseParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
      case DELETE => {
        delete(url, zeroSpaceParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        delete(url, trueParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        delete(url, falseParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        delete(url, zeroParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        delete(url, oneParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        delete(url, fullTrueParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        delete(url, fullFalseParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
    }
  }

  /**
   * 項目の非マイナス数値チェックをテストします。
   *
   * @param method Httpメソッド
   * @param url テストするAPIのURL
   * @param paramGenerator APIのFORMパラメータを生成するための関数
   * @param files APIのFORMパラメータ(file)
   * @return テスト結果
   */
  private def nonMinusIntCheck(method: HttpMethod, url: String, paramGenerator: JValue => Iterable[(String, String)], files: Iterable[(String, Any)] = Map.empty) = {
    val zeroSpaceParam = paramGenerator("")
    val zeroParam = paramGenerator(JInt(0))
    val oneParam = paramGenerator(JInt(1))
    val minusParam = paramGenerator(JInt(-1))
    method match {
      case GET => {
        get(url, zeroSpaceParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        get(url, zeroParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        get(url, oneParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        get(url, minusParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
      case PUT => {
        put(url, zeroSpaceParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        put(url, zeroParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        put(url, oneParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        put(url, minusParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
      case POST => {
        post(url, zeroSpaceParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        post(url, zeroParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        post(url, oneParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        post(url, minusParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
      case DELETE => {
        delete(url, zeroSpaceParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        delete(url, zeroParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        delete(url, oneParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
        delete(url, minusParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
      }
    }
  }

  /**
   * 項目のUUIDチェック(URLパラメータ向け)をテストします。
   *
   * @param method Httpメソッド
   * @param urlGenerator テストするAPIのURLを生成するための関数
   * @param valid 正常なケースで使用する値
   * @param params APIのFORMパラメータ
   * @param files APIのFORMパラメータ(file)
   * @return テスト結果
   */
  private def uuidCheckForUrl(method: HttpMethod, urlGenerator: String => String, valid: String, param: Iterable[(String, String)] = Map.empty, files: Iterable[(String, Any)] = Map.empty) = {
    val zeroSpaceUrl = urlGenerator("")
    val moreSpaceUrl = urlGenerator(URLEncoder.encode("   ", "UTF-8"))
    val invalidUrl = urlGenerator("test")
    val validUrl = urlGenerator(valid)
    method match {
      case GET => {
        get(zeroSpaceUrl, param) { parse(body).extract[AjaxResponse[Any]].status should be("NotFound") }
        get(moreSpaceUrl, param) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        get(invalidUrl, param) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        get(validUrl, param) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
      case PUT => {
        put(zeroSpaceUrl, param, files) { parse(body).extract[AjaxResponse[Any]].status should be("NotFound") }
        put(moreSpaceUrl, param, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(invalidUrl, param, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(validUrl, param, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
      case POST => {
        post(zeroSpaceUrl, param, files) { parse(body).extract[AjaxResponse[Any]].status should be("NotFound") }
        post(moreSpaceUrl, param, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(invalidUrl, param, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(validUrl, param, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
      case DELETE => {
        delete(zeroSpaceUrl, param) { parse(body).extract[AjaxResponse[Any]].status should be("NotFound") }
        delete(moreSpaceUrl, param) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        delete(invalidUrl, param) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        delete(validUrl, param) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
    }
  }

  /**
   * 項目のUUIDチェック(FORMパラメータ向け)をテストします。
   *
   * @param method Httpメソッド
   * @param url テストするAPIのURL
   * @param valid 正常なケースで使用する値
   * @param paramGenerator APIのFORMパラメータを生成するための関数
   * @param files APIのFORMパラメータ(file)
   * @return テスト結果
   */
  private def uuidCheckForForm(method: HttpMethod, url: String, valid: String, paramGenerator: String => Iterable[(String, String)], files: Iterable[(String, Any)] = Map.empty) = {
    val zeroSpaceParam = paramGenerator("")
    val moreSpaceParam = paramGenerator(URLEncoder.encode("   ", "UTF-8"))
    val invalidParam = paramGenerator("test")
    val validParam = paramGenerator(valid)
    method match {
      case GET => {
        get(url, zeroSpaceParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        get(url, moreSpaceParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        get(url, invalidParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        get(url, validParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
      case PUT => {
        put(url, zeroSpaceParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(url, moreSpaceParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(url, invalidParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(url, validParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
      case POST => {
        post(url, zeroSpaceParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(url, moreSpaceParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(url, invalidParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(url, validParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
      case DELETE => {
        delete(url, zeroSpaceParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        delete(url, moreSpaceParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        delete(url, invalidParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        delete(url, validParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
    }
  }

  /**
   * 項目の長さチェック(file以外)をテストします。
   *
   * @param method Httpメソッド
   * @param url テストするAPIのURL
   * @param valid 正常なケースで使用する値
   * @param paramGenerator APIのFORMパラメータを生成するための関数
   * @param files APIのFORMパラメータ(file)
   * @return テスト結果
   */
  private def nonZeroLengthCheckForParam[T](method: HttpMethod, url: String, valid: Seq[T], paramGenerator: Seq[T] => Iterable[(String, String)], files: Iterable[(String, Any)] = Map.empty) = {
    val zeroLengthParam = paramGenerator(Seq[T]())
    val nonZeroLengthParam = paramGenerator(valid)
    method match {
      case GET => {
        get(url, zeroLengthParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        get(url, nonZeroLengthParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
      case PUT => {
        put(url, zeroLengthParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(url, nonZeroLengthParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
      case POST => {
        post(url, zeroLengthParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(url, nonZeroLengthParam, files) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
      case DELETE => {
        delete(url, zeroLengthParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        delete(url, nonZeroLengthParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
    }
  }

  /**
   * 項目の長さチェック(fileの場合)をテストします。
   *
   * @param method Httpメソッド
   * @param url テストするAPIのURL
   * @param valid 正常なケースで使用する値
   * @param params APIのFORMパラメータ
   * @param fileGenerator APIのFORMパラメータ(file)を生成するための関数
   * @return テスト結果
   */
  private def nonZeroLengthCheckForFile(method: HttpMethod, url: String, valid: File, params: Iterable[(String, String)] = Map.empty, fileGenerator: File => Iterable[(String, Any)]) = {
    val zeroLengthParam = Map.empty
    val nonZeroLengthParam = fileGenerator(valid)
    method match {
      case PUT => {
        put(url, params, zeroLengthParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        put(url, params, nonZeroLengthParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
      case POST => {
        post(url, params, zeroLengthParam) { parse(body).extract[AjaxResponse[Any]].status should be("Illegal Argument") }
        post(url, params, nonZeroLengthParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
      case _ => throw new Exception("サポートしていない操作です")
    }
  }

  /**
   * 項目の日付チェックをテストします。
   *
   * @param method Httpメソッド
   * @param url テストするAPIのURL
   * @param paramGenerator APIのFORMパラメータを生成するための関数
   * @param files APIのFORMパラメータ(file)
   * @return テスト結果
   */
  private def dateCheck(method: HttpMethod, url: String, paramGenerator: String => Iterable[(String, String)], files: Iterable[(String, Any)] = Map.empty) = {
    val zeroSpaceParam = paramGenerator("")
    val notDateParam = paramGenerator("hoge")
    val invalidParam = paramGenerator("2016/12/32")
    val validParam = paramGenerator("2016/12/31")
    method match {
      case GET => {
        get(url, zeroSpaceParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") } // Optionの場合、None扱いになる
        get(url, notDateParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") } // Optionの場合、None扱いになる
        get(url, invalidParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") } // Optionの場合、None扱いになる
        get(url, validParam) { parse(body).extract[AjaxResponse[Any]].status should be("OK") }
      }
      case _ => throw new Exception("サポートしていない操作です")
    }
  }

  /**
   * 独自スコープを持つブロックを作成するためのメソッドです。
   *
   * @param procedure ブロックで行う処理
   * @return ブロックでの処理結果
   */
  private def block[T](procedure: => T): T = {
    procedure
  }

  /**
   * データセットに画像を追加し、そのIDを取得します。
   *
   * @param datasetId データセットID
   * @return 画像ID
   */
  private def getDatasetImageId(datasetId: String): String = {
    val files = Map("images" -> nonZeroByteImage)
    post(s"/api/datasets/${datasetId}/images", Map.empty, files) {
      parse(body).extract[AjaxResponse[DatasetAddImages]].data.images.headOption.map(_.id).getOrElse("")
    }
  }

  /**
   * データセットにファイルを追加し、そのIDを取得します。
   *
   * @param datasetId データセットID
   * @param file 追加するファイル
   * @return ファイルID
   */
  private def getFileId(datasetId: String, file: File): String = {
    val files = Map("files" -> file)
    post(s"/api/datasets/${datasetId}/files", Map.empty, files) {
      parse(body).extract[AjaxResponse[DatasetAddFiles]].data.files.headOption.map(_.id).getOrElse("")
    }
  }

  /**
   * データセットの保存先を変更し、タスクを生成して、そのIDを取得します。
   *
   * @param datasetId データセットID
   * @return タスクID
   */
  private def createDatasetTask(datasetId: String): String = {
    val params = Map("d" -> compact(render(("saveLocal" -> JBool(true)) ~ ("saveS3" -> JBool(true)))))
    put(s"/api/datasets/${datasetId}/storage", params) {
      parse(body).extract[AjaxResponse[DatasetTask]].data.taskId
    }
  }

  /**
   * グループにメンバーを追加します。
   *
   * @param groupId グループID
   * @param userId 追加するユーザID
   */
  private def addMember(groupId: String, userId: String): Unit = {
    val params = Map("d" -> compact(render(Seq(("userId" -> userId) ~ ("role" -> JInt(1))))))
    post(s"/api/groups/${groupId}/members", params) {
    }
  }

  /**
   * グループに画像を追加し、そのIDを取得します。
   *
   * @param groupId グループID
   * @return 画像ID
   */
  private def getGroupImageId(groupId: String): String = {
    val files = Map("images" -> nonZeroByteImage)
    post(s"/api/groups/${groupId}/images", Map.empty, files) {
      parse(body).extract[AjaxResponse[GroupAddImages]].data.images.headOption.map(_.id).getOrElse("")
    }
  }

  /**
   * グループを作成します。
   *
   * @return 作成したグループ
   */
  private def createGroup(): String = {
    val params = Map("d" -> compact(render(("name" -> "group1") ~ ("description" -> "des1"))))
    post("/api/groups", params) {
      checkStatus()
      parse(body).extract[AjaxResponse[Group]].data.id
    }
  }
}
