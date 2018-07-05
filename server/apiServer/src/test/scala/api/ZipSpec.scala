package api

import java.io.File

import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import common.DsmoqSpec
import dsmoq.AppConf
import dsmoq.controllers.AjaxResponse
import dsmoq.services.json.DatasetData.{ Dataset, DatasetFile, DatasetZipedFile }
import dsmoq.services.json.GroupData.Group
import dsmoq.services.json.RangeSlice

class ZipSpec extends DsmoqSpec {
  private val dummyFile = new File("../README.md")

  "API test" - {
    "dataset" - {
      "非ZipファイルをZipファイルで上書きした場合に、zipの中身が更新されるか" in {
        session {
          signIn()
          val files = Map("file[]" -> dummyFile)
          val params = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
          val (datasetId, fileId) = post("/api/datasets", params, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            val datasetId = result.data.id
            val fileId = result.data.files.head.id
            result.data.filesCount should be(1)
            result.data.files.head.isZip should be(false)
            result.data.files.head.zipCount should be(0)
            result.data.files.head.zipedFiles should be(Seq.empty)
            (datasetId, fileId)
          }

          val file = Map("file" -> new File("../testdata/test1.zip"))
          val fileUrl = post("/api/datasets/" + datasetId + "/files/" + fileId, Map.empty, file) {
            checkStatus()
            val f = parse(body).extract[AjaxResponse[DatasetFile]].data
            f.id should be(fileId)
            f.isZip should be(true)
            f.zipCount should be(2)
            f.zipedFiles should be(Seq.empty)
            f.url.get
          }

          get(new java.net.URI(fileUrl).getPath) {
            status should be(200)
          }

          val paramsZip = Map("limit" -> "20", "offset" -> "0")
          val zipUrls = get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", paramsZip) {
            checkStatus()
            val z = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]].data
            z.summary.total should be(2)
            z.summary.offset should be(0)
            z.summary.count should be(2)

            // ZIPファイル内のエントリはファイル名で昇順ソートされている
            z.results(0).name should be("test1/test1.txt")
            z.results(0).url should not be (None)
            z.results(1).name should be("test1/test2.txt")
            z.results(1).url should not be (None)

            z.results.map(f => f.url.get)
          }

          zipUrls.foreach {
            zipUrl =>
              get(new java.net.URI(zipUrl).getPath) {
                status should be(200)
              }
          }
        }
      }

      "Zipファイルを非Zipファイルで上書きした場合に、zipの中身が更新されるか" in {
        session {
          signIn()
          val files = Map("file[]" -> new File("../testdata/test1.zip"))
          val params = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
          val (datasetId, fileId) = post("/api/datasets", params, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            val datasetId = result.data.id
            val fileId = result.data.files.head.id
            result.data.filesCount should be(1)
            result.data.files.head.isZip should be(true)
            result.data.files.head.zipCount should be(2)
            result.data.files.head.zipedFiles should be(Seq.empty)
            (datasetId, fileId)
          }
          val file = Map("file" -> dummyFile)
          val url = post("/api/datasets/" + datasetId + "/files/" + fileId, Map.empty, file) {
            checkStatus()
            val f = parse(body).extract[AjaxResponse[DatasetFile]].data
            f.id should be(fileId)
            f.isZip should be(false)
            f.zipCount should be(0)
            f.zipedFiles should be(Seq.empty)
            f.url.get
          }

          get(new java.net.URI(url).getPath) {
            status should be(200)
          }
        }
      }

      "ZipファイルをZipファイルで上書きした場合に、zipの中身が更新されるか" in {
        session {
          signIn()
          val files = Map("file[]" -> new File("../testdata/test1.zip"))
          val params = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
          val (datasetId, fileId) = post("/api/datasets", params, files) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Dataset]]
            val datasetId = result.data.id
            val fileId = result.data.files.head.id
            result.data.filesCount should be(1)
            result.data.files.head.isZip should be(true)
            result.data.files.head.zipCount should be(2)
            result.data.files.head.zipedFiles should be(Seq.empty)
            (datasetId, fileId)
          }
          val file = Map("file" -> new File("../testdata/test2.zip"))
          val fileUrl = post("/api/datasets/" + datasetId + "/files/" + fileId, Map.empty, file) {
            checkStatus()
            val f = parse(body).extract[AjaxResponse[DatasetFile]].data
            f.isZip should be(true)
            f.zipCount should be(3)
            f.zipedFiles should be(Seq.empty)
            f.url.get
          }

          get(new java.net.URI(fileUrl).getPath) {
            status should be(200)
          }

          val paramsZip = Map("limit" -> "20", "offset" -> "0")
          val zipUrls = get(s"/api/datasets/${datasetId}/files/${fileId}/zippedfiles", paramsZip) {
            checkStatus()
            val z = parse(body).extract[AjaxResponse[RangeSlice[DatasetZipedFile]]].data
            z.summary.total should be(3)
            z.summary.offset should be(0)
            z.summary.count should be(3)

            // ZIPファイル内のエントリはファイル名で昇順ソートされている
            z.results(0).name should be("test2/test3.txt")
            z.results(0).url should not be (None)
            z.results(1).name should be("test2/test4.txt")
            z.results(1).url should not be (None)
            z.results(2).name should be("test2/test5.txt")
            z.results(2).url should not be (None)

            z.results.map(f => f.url.get)
          }

          zipUrls.foreach {
            zipUrl =>
              get(new java.net.URI(zipUrl).getPath) {
                status should be(200)
              }
          }
        }
      }
    }
  }
}
