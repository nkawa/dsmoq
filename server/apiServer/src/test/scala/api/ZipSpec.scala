package api

import java.io.File

import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import common.DsmoqSpec
import dsmoq.AppConf
import dsmoq.controllers.AjaxResponse
import dsmoq.services.json.DatasetData.Dataset
import dsmoq.services.json.DatasetData.DatasetFile
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
            result.data.files.head.zipedFiles.length should be(0)
            (datasetId, fileId)
          }

          val file = Map("file" -> new File("../testdata/test1.zip"))
          val (fileUrl, zipUrl) = post("/api/datasets/" + datasetId + "/files/" + fileId, Map.empty, file) {
            checkStatus()
            val f = parse(body).extract[AjaxResponse[DatasetFile]].data
            f.zipedFiles.length should be(2)
            (f.url.get, f.zipedFiles.head.url.get)
          }

          get(new java.net.URI(fileUrl).getPath) {
            status should be(200)
          }

          get(new java.net.URI(zipUrl).getPath) {
            status should be(200)
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
            result.data.files.head.zipedFiles.length should be(2)
            (datasetId, fileId)
          }
          val file = Map("file" -> dummyFile)
          val url = post("/api/datasets/" + datasetId + "/files/" + fileId, Map.empty, file) {
            checkStatus()
            val f = parse(body).extract[AjaxResponse[DatasetFile]].data
            f.zipedFiles.length should be(0)
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
            result.data.files.head.zipedFiles.length should be(2)
            (datasetId, fileId)
          }
          val file = Map("file" -> new File("../testdata/test2.zip"))
          val (fileUrl, zipUrl) = post("/api/datasets/" + datasetId + "/files/" + fileId, Map.empty, file) {
            checkStatus()
            val f = parse(body).extract[AjaxResponse[DatasetFile]].data
            f.zipedFiles.length should be(3)
            (f.url.get, f.zipedFiles.head.url.get)
          }

          get(new java.net.URI(fileUrl).getPath) {
            status should be(200)
          }

          get(new java.net.URI(zipUrl).getPath) {
            status should be(200)
          }
        }
      }
    }
  }
}
