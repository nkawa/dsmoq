package api

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

import org.joda.time.DateTime
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._

import common.DsmoqSpec
import dsmoq.AppConf
import dsmoq.controllers.AjaxResponse
import dsmoq.persistence
import dsmoq.persistence.{ OwnerType, UserAccessLevel, GroupAccessLevel }
import dsmoq.services.DatasetService.GetAppDeletedTypes
import dsmoq.services.json.DatasetData
import dsmoq.services.json.DatasetData.Dataset
import dsmoq.services.json.DatasetData.DatasetAddFiles
import dsmoq.services.json.DatasetData.DatasetAddImages
import dsmoq.services.json.DatasetData.DatasetDeleteImage
import dsmoq.services.json.GroupData.Group
import dsmoq.services.json.RangeSlice

class DatasetAppSpec extends DsmoqSpec {
  private val testdataDir = Paths.get("../testdata")
  private val tempDir = testdataDir.resolve("temp")
  private val emptyFile = testdataDir.resolve("empty").toFile
  private val zipFile = testdataDir.resolve("test1.zip").toFile
  private val jarFile = testdataDir.resolve("hello.jar").toFile

  val dummy2UserId = "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04"
  val dummy3UserId = "4aaefd45-2fe5-4ce0-b156-3141613f69a6"

  override def beforeAll() {
    super.beforeAll()
    Files.createDirectories(tempDir)
  }

  val uuid = UUID.randomUUID.toString

  "upload app" - {
    "invalids" in {
      for {
        testDatasetId <- Seq(Some(""), Some("hello"), Some(uuid), None) // None: create new dataset
        datasetDeleted <- Seq(true, false)
        if !(testDatasetId.isDefined && datasetDeleted)
        guestAccess <- Seq(true, false)
        if !(testDatasetId.isDefined && guestAccess)
        authenticated <- Seq(true, false)
        owner <- Seq(true, false)
        if !(!authenticated && owner)
        fileParam <- Seq(None, Some(emptyFile), Some(zipFile), Some(jarFile))
      } {
        val clue = s"datasetId: ${testDatasetId}, deleted: ${datasetDeleted}, guest: ${guestAccess}, " +
          s"auth: ${authenticated}, owner: ${owner}, file: ${fileParam}"
        withClue(clue) {
          val datasetId = session {
            signIn()
            val datasetId = testDatasetId.getOrElse(createDataset(guestAccess))
            if (datasetDeleted) {
              delete(s"/api/datasets/${datasetId}") { checkStatus() }
            }
            datasetId
          }
          session {
            if (authenticated) {
              signIn(if (owner) "dummy1" else "dummy2", "password")
            }
            val files = fileParam.map(file => Map("file" -> file)).getOrElse(Map.empty)
            post(s"/api/datasets/${datasetId}/apps", params = Map.empty, files = files) {
              if (testDatasetId == Some("")) {
                checkStatus(404, Some("NotFound"))
              } else if (fileParam != Some(jarFile)) {
                checkStatus(400, Some("Illegal Argument"))
              } else if (testDatasetId == Some("hello")) {
                checkStatus(404, Some("Illegal Argument"))
              } else if (!authenticated) {
                checkStatus(403, Some("Unauthorized"))
              } else if (testDatasetId == Some(uuid) || datasetDeleted) {
                checkStatus(404, Some("NotFound"))
              } else if (!owner) {
                checkStatus(403, Some("AccessDenied"))
              } else {
                checkStatus()
              }
            }
          }
        }
      }
    }
    "file names" in {
      session {
        signIn()
        val datasetId = createDataset()
        for {
          ascii <- Seq(true, false)
          //multi <- Seq(true, false)
          hyphen <- Seq(true, false)
          unders <- Seq(true, false)
          dot <- Seq(true, false)
          leadDot <- Seq(true, false)
          res <- Seq(true, false)
          ext <- Seq("", ".jar", ".zip", ".txt")
          name = "" +
            (if (leadDot) "." else "") +
            (if (ascii) "a" else "") +
            //(if (multi) "あ" else "") +
            (if (hyphen) "-" else "") +
            (if (unders) "_" else "") +
            (if (ascii) "a" else "") +
            //(if (multi) "あ" else "") +
            (if (hyphen) "-" else "") +
            (if (res) "__Ljp__V1" else "") +
            (if (dot) ".1" else "") +
            ext
          if (!name.isEmpty && name != "." && name != "..")
        } {
          withClue(name) {
            val path = tempDir.resolve(name)
            Files.copy(jarFile.toPath, path, StandardCopyOption.REPLACE_EXISTING)
            val files = Map("file" -> path.toFile)
            post(s"/api/datasets/${datasetId}/apps", params = Map.empty, files = files) {
              Files.delete(path)
              checkStatus()
              val app = parse(body).extract[AjaxResponse[DatasetData.App]].data
              app.name should be(name)
            }
          }
        }
      }
    }
  }
  "get app" - {
    "invalids" in {
      for {
        testDatasetId <- Seq(Some(""), Some("hello"), Some(uuid), None) // None: create new dataset
        datasetDeleted <- Seq(true, false)
        if !(testDatasetId.isDefined && datasetDeleted)
        guestAccess <- Seq(true, false)
        if !(testDatasetId.isDefined && guestAccess)
        testAppId <- Seq(Some(""), Some("hello"), Some(uuid), None) // None: create new app
        appDeleted <- Seq(true, false)
        if !(testAppId.isDefined && appDeleted)
        appRelated <- Seq(true, false)
        authenticated <- Seq(true, false)
        owner <- Seq(true, false)
        if !(!authenticated && owner)
      } {
        val clue = s"datasetId: ${testDatasetId}, deleted: ${datasetDeleted}, guest: ${guestAccess}, " +
          s"appId: ${testAppId}, deleted: ${appDeleted}, auth: ${authenticated}, owner: ${owner}"
        withClue(clue) {
          val (datasetId, appId) = session {
            signIn()
            val datasetId = testDatasetId.getOrElse(createDataset(guestAccess))
            val appId = testAppId.getOrElse {
              val appDatasetId = if (testDatasetId.isEmpty && appRelated) datasetId else createDataset(guestAccess)
              val appId = createApp(appDatasetId)
              if (appDeleted) {
                delete(s"/api/datasets/${appDatasetId}/apps/${appId}") { checkStatus() }
              }
              appId
            }
            if (datasetDeleted) {
              delete(s"/api/datasets/${datasetId}") { checkStatus() }
            }
            (datasetId, appId)
          }
          session {
            if (authenticated) {
              signIn(if (owner) "dummy1" else "dummy2", "password")
            }
            get(s"/api/datasets/${datasetId}/apps/${appId}") {
              if (testDatasetId == Some("") || testAppId == Some("")) {
                checkStatus(404, Some("NotFound"))
              } else if (testDatasetId == Some("hello") || testAppId == Some("hello")) {
                checkStatus(404, Some("Illegal Argument"))
              } else if (!authenticated) {
                checkStatus(403, Some("Unauthorized"))
              } else if (testDatasetId == Some(uuid) || datasetDeleted
                || testAppId == Some(uuid) || appDeleted || !appRelated) {
                checkStatus(404, Some("NotFound"))
              } else if (!owner) {
                checkStatus(403, Some("AccessDenied"))
              } else {
                checkStatus()
              }
            }
          }
        }
      }
    }
  }
  "get apps" - {
    "form invalids" in {
      val (datasetId, appId) = session {
        signIn()
        val datasetId = createDataset()
        val appId = createApp(datasetId)
        (datasetId, appId)
      }
      val appIds = for {
        invalidExcludeId <- Seq(true, false)
        nonExistExcludeId <- Seq(true, false)
        existExcludeId <- Seq(true, false)
      } yield {
        Seq(
          if (invalidExcludeId) Seq(JString("a")) else Seq.empty,
          if (nonExistExcludeId) Seq(JString(uuid)) else Seq.empty,
          if (existExcludeId) Seq(JString(appId)) else Seq.empty
        ).flatten
      }
      for {
        deletedType <- Seq(None, Some(JString("a"))) ++ (-1 to 3).map(x => Some(JInt(x)))
        excludeIds <- Seq(None, Some(JString(uuid))) ++ appIds.map(x => Some(JArray(x.toList)))
        limit <- Seq(None, Some(JString("a"))) ++ (-1 to 1).map(x => Some(JInt(x)))
        offset <- Seq(None, Some(JString("a"))) ++ (-1 to 1).map(x => Some(JInt(x)))
      } {
        session {
          signIn()
          val params = Map(
            "d" -> compact(
              render(
                ("deletedType" -> deletedType)
                  ~ ("excludeIds" -> excludeIds)
                  ~ ("limit" -> limit)
                  ~ ("offset" -> offset)
              )
            )
          )
          withClue(params.toString) {
            get(s"/api/datasets/${datasetId}/apps", params = params) {
              if (excludeIds == Some(JString("a"))
                || excludeIds.collect { case JArray(xs) => xs.contains(JString("a")) }.getOrElse(false)
                || limit == Some(JInt(-1))
                || offset == Some(JInt(-1))) {
                checkStatus(400, Some("Illegal Argument"))
              } else {
                checkStatus()
                val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetData.App]]].data
                if (deletedType == Some(JInt(GetAppDeletedTypes.LOGICAL_DELETED_ONLY))
                  || excludeIds.collect { case JArray(xs) => xs.contains(JString(appId)) }.getOrElse(false)) {
                  result.summary.total should be(0)
                } else {
                  result.summary.total should be(1)
                  if (limit == Some(JInt(0)) || offset == Some(JInt(1))) {
                    result.results.length should be(0)
                  } else {
                    result.results.length should be(1)
                  }
                }
              }
            }
          }
        }
      }
    }
    "invalids" in {
      for {
        testDatasetId <- Seq(Some(""), Some("hello"), Some(uuid), None) // None: create new dataset
        datasetDeleted <- Seq(true, false)
        if !(testDatasetId.isDefined && datasetDeleted)
        guestAccess <- Seq(true, false)
        if !(testDatasetId.isDefined && guestAccess)
        authenticated <- Seq(true, false)
        owner <- Seq(true, false)
        if !(!authenticated && owner)
        formInvalid <- Seq(true, false)
      } {
        val clue = s"datasetId: ${testDatasetId}, deleted: ${datasetDeleted}, guest: ${guestAccess}, " +
          s"auth: ${authenticated}, owner: ${owner}, formInvalid: ${formInvalid}"
        withClue(clue) {
          val datasetId = session {
            signIn()
            val datasetId = testDatasetId.getOrElse {
              val datasetId = createDataset(guestAccess)
              createApp(datasetId)
              datasetId
            }
            if (datasetDeleted) {
              delete(s"/api/datasets/${datasetId}") { checkStatus() }
            }
            datasetId
          }
          session {
            if (authenticated) {
              signIn(if (owner) "dummy1" else "dummy2", "password")
            }
            val params = if (formInvalid) {
              Map("d" -> compact(render("limit" -> -1)))
            } else {
              Map.empty
            }
            get(s"/api/datasets/${datasetId}/apps", params = params) {
              if (testDatasetId == Some("")) {
                checkStatus(404, Some("NotFound"))
              } else if (formInvalid) {
                checkStatus(400, Some("Illegal Argument"))
              } else if (testDatasetId == Some("hello")) {
                checkStatus(404, Some("Illegal Argument"))
              } else if (!authenticated) {
                checkStatus(403, Some("Unauthorized"))
              } else if (testDatasetId == Some(uuid) || datasetDeleted) {
                checkStatus(404, Some("NotFound"))
              } else if (!owner) {
                checkStatus(403, Some("AccessDenied"))
              } else {
                checkStatus()
              }
            }
          }
        }
      }
    }
    "result" - {
      "apps" - {
        for {
          deleteds <- 0 to 1
          primary <- Seq(true, false)
          excludes <- 0 to 1
          others <- 0 to 1
          otherDatasets <- 0 to 1
        } {
          s"deleteds: ${deleteds}, primary: ${primary}, excludes: ${excludes}, others: ${others}, otherDatasets: ${otherDatasets}" in {
            session {
              signIn()
              val dataset = createDataset()
              (1 to deleteds).map { _ =>
                val app = createApp(dataset)
                delete(s"/api/datasets/${dataset}/apps/${app}") { checkStatus() }
              }
              if (primary) {
                createApp(dataset, true)
              }
              val excludeIds = (1 to excludes).map { _ =>
                createApp(dataset)
              }
              (1 to others).map { _ =>
                createApp(dataset)
              }
              (1 to otherDatasets).map { _ =>
                val otherDataset = createDataset()
                createApp(otherDataset)
              }
              val excludesParam = JArray(excludeIds.map(JString).toList)
              get(s"/api/datasets/${dataset}/apps", params = Map("d" -> compact(render("excludeIds" -> excludesParam)))) {
                checkStatus()
                val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetData.App]]].data
                result.summary.total should be(others + (if (primary) 1 else 0))
              }
            }
          }
        }
      }
      "primary app" in {
        session {
          signIn()
          val datasetId = createDataset()
          val app1 = createApp(datasetId)
          get(s"/api/datasets/${datasetId}/apps") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetData.App]]].data
            result.summary.total should be(1)
            result.results.map(_.id) should be(Seq(app1))
          }
          val app2 = createApp(datasetId)
          get(s"/api/datasets/${datasetId}/apps") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetData.App]]].data
            result.summary.total should be(2)
            result.results.map(_.id) should be(Seq(app2, app1))
          }
          setPrimaryApp(datasetId, app1)
          get(s"/api/datasets/${datasetId}/apps") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetData.App]]].data
            result.summary.total should be(2)
            result.results.map(_.id) should be(Seq(app1, app2))
          }
        }
      }
      "deleted Type" in {
        session {
          signIn()
          val datasetId = createDataset()
          val app1 = createApp(datasetId)
          val app2 = createApp(datasetId)
          delete(s"/api/datasets/${datasetId}/apps/${app2}") { checkStatus() }
          get(s"/api/datasets/${datasetId}/apps", params = Map("d" -> compact(render("deletedType" -> 0)))) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetData.App]]].data
            result.summary.total should be(1)
            result.results.map(_.id) should be(Seq(app1))
          }
          get(s"/api/datasets/${datasetId}/apps", params = Map("d" -> compact(render("deletedType" -> 1)))) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetData.App]]].data
            result.summary.total should be(2)
            result.results.map(_.id) should be(Seq(app2, app1))
          }
          get(s"/api/datasets/${datasetId}/apps", params = Map("d" -> compact(render("deletedType" -> 2)))) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetData.App]]].data
            result.summary.total should be(1)
            result.results.map(_.id) should be(Seq(app2))
          }
        }
      }
      "offset, limit" in {
        session {
          signIn()
          val datasetId = createDataset()
          val num = 5
          val apps = (0 to (num - 1)).map(_ => createApp(datasetId)).toSeq.reverse
          for {
            offset <- 0 to (num + 1)
            limit <- 0 to (num + 1)
          } {
            withClue(s"num: ${num}, offset: ${offset}, limit: ${limit}") {
              get(s"/api/datasets/${datasetId}/apps", params = Map("d" -> compact(render(("offset" -> offset) ~ ("limit" -> limit))))) {
                checkStatus()
                val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetData.App]]].data
                result.summary.total should be(num)
                result.results.map(_.id) should be(apps.drop(offset).take(limit))
              }
            }
          }
        }
      }
    }
  }
  "upgrade app" - {
    "invalids" in {
      for {
        testDatasetId <- Seq(Some(""), Some("hello"), Some(uuid), None) // None: create new dataset
        datasetDeleted <- Seq(true, false)
        if !(testDatasetId.isDefined && datasetDeleted)
        guestAccess <- Seq(true, false)
        if !(testDatasetId.isDefined && guestAccess)
        testAppId <- Seq(Some(""), Some("hello"), Some(uuid), None) // None: create new app
        appDeleted <- Seq(true, false)
        if !(testAppId.isDefined && appDeleted)
        appRelated <- Seq(true, false)
        authenticated <- Seq(true, false)
        owner <- Seq(true, false)
        if !(!authenticated && owner)
        fileParam <- Seq(None, Some(emptyFile), Some(zipFile), Some(jarFile))
      } {
        val clue = s"datasetId: ${testDatasetId}, deleted: ${datasetDeleted}, guest: ${guestAccess}, " +
          s"appId: ${testAppId}, deleted: ${appDeleted}, auth: ${authenticated}, owner: ${owner}, file: ${fileParam}"
        withClue(clue) {
          val (datasetId, appId) = session {
            signIn()
            val datasetId = testDatasetId.getOrElse(createDataset(guestAccess))
            val appId = testAppId.getOrElse {
              val appDatasetId = if (testDatasetId.isEmpty && appRelated) datasetId else createDataset(guestAccess)
              val appId = createApp(appDatasetId)
              if (appDeleted) {
                delete(s"/api/datasets/${appDatasetId}/apps/${appId}") { checkStatus() }
              }
              appId
            }
            if (datasetDeleted) {
              delete(s"/api/datasets/${datasetId}") { checkStatus() }
            }
            (datasetId, appId)
          }
          session {
            if (authenticated) {
              signIn(if (owner) "dummy1" else "dummy2", "password")
            }
            val files = fileParam.map(file => Map("file" -> file)).getOrElse(Map.empty)
            put(s"/api/datasets/${datasetId}/apps/${appId}", params = Map.empty, files = files) {
              if (testDatasetId == Some("") || testAppId == Some("")) {
                checkStatus(404, Some("NotFound"))
              } else if (fileParam != Some(jarFile)) {
                checkStatus(400, Some("Illegal Argument"))
              } else if (testDatasetId == Some("hello") || testAppId == Some("hello")) {
                checkStatus(404, Some("Illegal Argument"))
              } else if (!authenticated) {
                checkStatus(403, Some("Unauthorized"))
              } else if (testDatasetId == Some(uuid) || datasetDeleted
                || testAppId == Some(uuid) || appDeleted || !appRelated) {
                checkStatus(404, Some("NotFound"))
              } else if (!owner) {
                checkStatus(403, Some("AccessDenied"))
              } else {
                checkStatus()
              }
            }
          }
        }
      }
    }
    "file names" in {
      session {
        signIn()
        val datasetId = createDataset()
        val appId = createApp(datasetId)
        for {
          ascii <- Seq(true, false)
          //multi <- Seq(true, false)
          hyphen <- Seq(true, false)
          unders <- Seq(true, false)
          dot <- Seq(true, false)
          leadDot <- Seq(true, false)
          res <- Seq(true, false)
          ext <- Seq("", ".jar", ".zip", ".txt")
          name = "" +
            (if (leadDot) "." else "") +
            (if (ascii) "a" else "") +
            //(if (multi) "あ" else "") +
            (if (hyphen) "-" else "") +
            (if (unders) "_" else "") +
            (if (ascii) "a" else "") +
            //(if (multi) "あ" else "") +
            (if (hyphen) "-" else "") +
            (if (res) "__Ljp__V1" else "") +
            (if (dot) ".1" else "") +
            ext
          if (!name.isEmpty && name != "." && name != "..")
        } {
          withClue(name) {
            val path = tempDir.resolve(name)
            Files.copy(jarFile.toPath, path, StandardCopyOption.REPLACE_EXISTING)
            val files = Map("file" -> path.toFile)
            put(s"/api/datasets/${datasetId}/apps/${appId}", params = Map.empty, files = files) {
              Files.delete(path)
              checkStatus()
              val app = parse(body).extract[AjaxResponse[DatasetData.App]].data
              app.name should be(name)
            }
          }
        }
      }
    }
  }
  "delete app" - {
    "invalids" in {
      for {
        testDatasetId <- Seq(Some(""), Some("hello"), Some(uuid), None) // None: create new dataset
        datasetDeleted <- Seq(true, false)
        if !(testDatasetId.isDefined && datasetDeleted)
        guestAccess <- Seq(true, false)
        if !(testDatasetId.isDefined && guestAccess)
        testAppId <- Seq(Some(""), Some("hello"), Some(uuid), None) // None: create new app
        appDeleted <- Seq(true, false)
        if !(testAppId.isDefined && appDeleted)
        appRelated <- Seq(true, false)
        authenticated <- Seq(true, false)
        owner <- Seq(true, false)
        if !(!authenticated && owner)
      } {
        val clue = s"datasetId: ${testDatasetId}, deleted: ${datasetDeleted}, guest: ${guestAccess}, " +
          s"appId: ${testAppId}, deleted: ${appDeleted}, auth: ${authenticated}, owner: ${owner}"
        withClue(clue) {
          val (datasetId, appId) = session {
            signIn()
            val datasetId = testDatasetId.getOrElse(createDataset(guestAccess))
            val appId = testAppId.getOrElse {
              val appDatasetId = if (testDatasetId.isEmpty && appRelated) datasetId else createDataset(guestAccess)
              val appId = createApp(appDatasetId)
              if (appDeleted) {
                delete(s"/api/datasets/${appDatasetId}/apps/${appId}") { checkStatus() }
              }
              appId
            }
            if (datasetDeleted) {
              delete(s"/api/datasets/${datasetId}") { checkStatus() }
            }
            (datasetId, appId)
          }
          session {
            if (authenticated) {
              signIn(if (owner) "dummy1" else "dummy2", "password")
            }
            delete(s"/api/datasets/${datasetId}/apps/${appId}") {
              if (testDatasetId == Some("") || testAppId == Some("")) {
                checkStatus(404, Some("NotFound"))
              } else if (testDatasetId == Some("hello") || testAppId == Some("hello")) {
                checkStatus(404, Some("Illegal Argument"))
              } else if (!authenticated) {
                checkStatus(403, Some("Unauthorized"))
              } else if (testDatasetId == Some(uuid) || datasetDeleted
                || testAppId == Some(uuid) || appDeleted || !appRelated) {
                checkStatus(404, Some("NotFound"))
              } else if (!owner) {
                checkStatus(403, Some("AccessDenied"))
              } else {
                checkStatus()
              }
            }
          }
        }
      }
    }
  }
  "set primary app" - {
    "invalids" in {
      for {
        testDatasetId <- Seq(Some(""), Some("hello"), Some(uuid), None) // None: create new dataset
        datasetDeleted <- Seq(true, false)
        if !(testDatasetId.isDefined && datasetDeleted)
        guestAccess <- Seq(true, false)
        if !(testDatasetId.isDefined && guestAccess)
        testAppId <- Seq(Some(""), Some("hello"), Some(uuid), None) // None: create new app
        appDeleted <- Seq(true, false)
        if !(testAppId.isDefined && appDeleted)
        appRelated <- Seq(true, false)
        authenticated <- Seq(true, false)
        owner <- Seq(true, false)
        if !(!authenticated && owner)
        currentPrimaryApp <- Seq(true, false)
        if !(testDatasetId.isDefined && currentPrimaryApp)
        currentPrimaryAppDeleted <- Seq(true, false)
        if !(!currentPrimaryApp && currentPrimaryAppDeleted)
      } {
        val clue = s"datasetId: ${testDatasetId}, deleted: ${datasetDeleted}, guest: ${guestAccess}, " +
          s"appId: ${testAppId}, deleted: ${appDeleted}, auth: ${authenticated}, owner: ${owner}, " +
          s"currentPrimaryApp: ${currentPrimaryApp}, deleted: ${currentPrimaryAppDeleted}"
        withClue(clue) {
          val (datasetId, appId) = session {
            signIn()
            val datasetId = testDatasetId.getOrElse(createDataset(guestAccess))
            if (currentPrimaryApp) {
              val currentPrimaryAppId = createApp(datasetId, true)
              if (currentPrimaryAppDeleted) {
                delete(s"/api/datasets/${datasetId}/apps/${currentPrimaryAppId}") { checkStatus() }
              }
            }
            val appId = testAppId.getOrElse {
              val appDatasetId = if (testDatasetId.isEmpty && appRelated) datasetId else createDataset(guestAccess)
              val appId = createApp(appDatasetId)
              if (appDeleted) {
                delete(s"/api/datasets/${appDatasetId}/apps/${appId}") { checkStatus() }
              }
              appId
            }
            if (datasetDeleted) {
              delete(s"/api/datasets/${datasetId}") { checkStatus() }
            }
            (datasetId, appId)
          }
          session {
            if (authenticated) {
              signIn(if (owner) "dummy1" else "dummy2", "password")
            }
            val params = Map("d" -> compact(render(("appId" -> (if (appId.isEmpty) None else Some(appId))))))
            put(s"/api/datasets/${datasetId}/apps/primary", params = params) {
              if (testDatasetId == Some("")) {
                checkStatus(404, Some("NotFound"))
              } else if (testAppId == Some("") || testAppId == Some("hello")) {
                checkStatus(400, Some("Illegal Argument"))
              } else if (testDatasetId == Some("hello")) {
                checkStatus(404, Some("Illegal Argument"))
              } else if (!authenticated) {
                checkStatus(403, Some("Unauthorized"))
              } else if (testDatasetId == Some(uuid) || datasetDeleted
                || testAppId == Some(uuid) || appDeleted || !appRelated) {
                checkStatus(404, Some("NotFound"))
              } else if (!owner) {
                checkStatus(403, Some("AccessDenied"))
              } else {
                checkStatus()
                get(s"/api/datasets/${datasetId}/apps/primary") {
                  checkStatus()
                  val result = parse(body).extract[AjaxResponse[Option[DatasetData.App]]].data
                  result.map(_.id) should be(Some(appId))
                }
              }
            }
          }
        }
      }
    }
  }
  "get primary app" - {
    "invalids" in {
      for {
        testDatasetId <- Seq(Some(""), Some("hello"), Some(uuid), None) // None: create new dataset
        datasetDeleted <- Seq(true, false)
        if !(testDatasetId.isDefined && datasetDeleted)
        guestAccess <- Seq(true, false)
        if !(testDatasetId.isDefined && guestAccess)
        authenticated <- Seq(true, false)
        owner <- Seq(true, false)
        if !(!authenticated && owner)
        currentPrimaryApp <- Seq(true, false)
        if !(testDatasetId.isDefined && currentPrimaryApp)
        currentPrimaryAppDeleted <- Seq(true, false)
        if !(!currentPrimaryApp && currentPrimaryAppDeleted)
      } {
        val clue = s"datasetId: ${testDatasetId}, deleted: ${datasetDeleted}, guest: ${guestAccess}, " +
          s"auth: ${authenticated}, owner: ${owner}, " +
          s"currentPrimaryApp: ${currentPrimaryApp}, deleted: ${currentPrimaryAppDeleted}"
        withClue(clue) {
          val (datasetId, primaryAppId) = session {
            signIn()
            val datasetId = testDatasetId.getOrElse(createDataset(guestAccess))
            val primaryAppId = if (currentPrimaryApp) {
              val currentPrimaryAppId = createApp(datasetId, true)
              if (currentPrimaryAppDeleted) {
                delete(s"/api/datasets/${datasetId}/apps/${currentPrimaryAppId}") { checkStatus() }
                None
              } else {
                Some(currentPrimaryAppId)
              }
            } else {
              None
            }
            if (datasetDeleted) {
              delete(s"/api/datasets/${datasetId}") { checkStatus() }
            }
            (datasetId, primaryAppId)
          }
          session {
            if (authenticated) {
              signIn(if (owner) "dummy1" else "dummy2", "password")
            }
            get(s"/api/datasets/${datasetId}/apps/primary") {
              if (testDatasetId == Some("")) {
                checkStatus(404, Some("NotFound"))
              } else if (testDatasetId == Some("hello")) {
                checkStatus(404, Some("Illegal Argument"))
              } else if (!authenticated) {
                checkStatus(403, Some("Unauthorized"))
              } else if (testDatasetId == Some(uuid) || datasetDeleted) {
                checkStatus(404, Some("NotFound"))
              } else if (!owner) {
                checkStatus(403, Some("AccessDenied"))
              } else {
                checkStatus()
                val result = parse(body).extract[AjaxResponse[DatasetData.App]].data
                Option(result).map(_.id) should be(primaryAppId)
              }
            }
          }
        }
      }
    }
  }
  "get primary app url" - {
    "invalids" in {
      for {
        testDatasetId <- Seq(Some(""), Some("hello"), Some(uuid), None) // None: create new dataset
        datasetDeleted <- Seq(true, false)
        if !(testDatasetId.isDefined && datasetDeleted)
        authenticated <- Seq(true, false)
        permission <- 0 to 3
        if !(testDatasetId.isDefined && permission != 0)
        if !(!authenticated && permission == 3)
        apiKey <- Seq(true, false)
        currentPrimaryApp <- Seq(true, false)
        if !(testDatasetId.isDefined && currentPrimaryApp)
        currentPrimaryAppDeleted <- Seq(true, false)
        if !(!currentPrimaryApp && currentPrimaryAppDeleted)
      } {
        val clue = s"datasetId: ${testDatasetId}, deleted: ${datasetDeleted}, " +
          s"auth: ${authenticated}, permission: ${permission}, key: ${apiKey}, " +
          s"currentPrimaryApp: ${currentPrimaryApp}, deleted: ${currentPrimaryAppDeleted}"
        withClue(clue) {
          val (datasetId, primaryAppId) = session {
            signIn()
            val datasetId = testDatasetId.getOrElse {
              val datasetId = createDataset()
              if (permission != 0) {
                if (authenticated) {
                  val userId = if (apiKey) dummy2UserId else dummy3UserId
                  val accessLevelParams = Map(
                    "d" -> compact(
                      render(
                        Seq(
                          ("id" -> userId) ~ ("ownerType" -> JInt(OwnerType.User)) ~ ("accessLevel" -> JInt(permission))
                        )
                      )
                    )
                  )
                  post(s"/api/datasets/${datasetId}/acl", accessLevelParams) { checkStatus() }
                } else {
                  val guestAccessLevelParams = Map("d" -> compact(render(("accessLevel" -> permission))))
                  put(s"/api/datasets/${datasetId}/guest_access", guestAccessLevelParams) { checkStatus() }
                }
              }
              datasetId
            }
            val primaryAppId = if (currentPrimaryApp) {
              val currentPrimaryAppId = createApp(datasetId, true)
              if (currentPrimaryAppDeleted) {
                delete(s"/api/datasets/${datasetId}/apps/${currentPrimaryAppId}") { checkStatus() }
                None
              } else {
                Some(currentPrimaryAppId)
              }
            } else {
              None
            }
            if (datasetDeleted) {
              delete(s"/api/datasets/${datasetId}") { checkStatus() }
            }
            (datasetId, primaryAppId)
          }
          withApiKey(if (apiKey && !authenticated) Some(AppConf.guestUser.id) else None) {
            session {
              if (authenticated) {
                signIn(if (apiKey) "dummy2" else "dummy3", "password")
              }
              get(s"/api/datasets/${datasetId}/apps/primary/url") {
                if (testDatasetId == Some("")) {
                  checkStatus(404, Some("NotFound"))
                } else if (testDatasetId == Some("hello")) {
                  checkStatus(404, Some("Illegal Argument"))
                } else if (testDatasetId == Some(uuid) || datasetDeleted) {
                  checkStatus(404, Some("NotFound"))
                } else {
                  checkStatus()
                  val result = parse(body).extract[AjaxResponse[String]].data
                  Option(result).isDefined should be(permission >= 2 && apiKey && currentPrimaryApp && !currentPrimaryAppDeleted)
                }
              }
            }
          }
        }
      }
    }
  }
  "get app file" - {
    "invalids" in {
      for {
        testDatasetId <- Seq(Some(""), Some("hello"), Some(uuid), None) // None: create new dataset
        datasetDeleted <- Seq(true, false)
        if !(testDatasetId.isDefined && datasetDeleted)
        user <- Seq("", "hello", uuid, AppConf.guestUser.id, dummy3UserId)
        userDisabled <- Seq(true, false)
        if !(userDisabled && user == dummy3UserId)
        permission <- 0 to 3
        if !(testDatasetId.isDefined && permission != 0)
        if !((user == "" || user == "hello" || user == uuid) && permission != 0)
        if !(user == AppConf.guestUser.id && permission == 3)
        apiKey <- Seq(true, false)
        if !((user == "" || user == "hello" || user == uuid) && apiKey)
        currentPrimaryApp <- Seq(true, false)
        if !(testDatasetId.isDefined && currentPrimaryApp)
        currentPrimaryAppDeleted <- Seq(true, false)
        if !(!currentPrimaryApp && currentPrimaryAppDeleted)
      } {
        val clue = s"datasetId: ${testDatasetId}, deleted: ${datasetDeleted}, " +
          s"user: ${user}, permission: ${permission}, key: ${apiKey}, " +
          s"currentPrimaryApp: ${currentPrimaryApp}, deleted: ${currentPrimaryAppDeleted}"
        withClue(clue) {
          val (datasetId, primaryAppId) = session {
            signIn()
            val datasetId = testDatasetId.getOrElse {
              val datasetId = createDataset()
              if (permission != 0) {
                if (user == dummy3UserId) {
                  val accessLevelParams = Map(
                    "d" -> compact(
                      render(
                        Seq(
                          ("id" -> user) ~ ("ownerType" -> JInt(OwnerType.User)) ~ ("accessLevel" -> JInt(permission))
                        )
                      )
                    )
                  )
                  post(s"/api/datasets/${datasetId}/acl", accessLevelParams) { checkStatus() }
                } else if (user == AppConf.guestUser.id) {
                  val guestAccessLevelParams = Map("d" -> compact(render(("accessLevel" -> permission))))
                  put(s"/api/datasets/${datasetId}/guest_access", guestAccessLevelParams) { checkStatus() }
                }
              }
              datasetId
            }
            val primaryAppId = if (currentPrimaryApp) {
              val currentPrimaryAppId = createApp(datasetId, true)
              if (currentPrimaryAppDeleted) {
                delete(s"/api/datasets/${datasetId}/apps/${currentPrimaryAppId}") { checkStatus() }
                None
              } else {
                Some(currentPrimaryAppId)
              }
            } else {
              None
            }
            if (datasetDeleted) {
              delete(s"/api/datasets/${datasetId}") { checkStatus() }
            }
            (datasetId, primaryAppId)
          }
          withApiKey(if (apiKey && (user == AppConf.guestUser.id || user == dummy3UserId)) Some(user) else None) {
            session {
              for {
                ext <- Seq("jnlp", "jar")
              } {
                withClue(ext) {
                  get(s"/apps/${user}/${datasetId}.${ext}") {
                    if (user == "" || testDatasetId == Some("")) {
                      checkStatus(404, Some("NotFound"))
                    } else if (user == "hello" || testDatasetId == Some("hello")) {
                      checkStatus(404, Some("Illegal Argument"))
                    } else if (user == uuid || testDatasetId == Some(uuid) || datasetDeleted
                      || !apiKey || !currentPrimaryApp || currentPrimaryAppDeleted) {
                      checkStatus(404, Some("NotFound"))
                    } else if (permission < 2) {
                      checkStatus(403, Some("AccessDenied"))
                    } else {
                      checkStatus(200, None)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  def createApp(datasetId: String, primary: Boolean = false): String = {
    val id = post(s"/api/datasets/${datasetId}/apps", params = Map.empty, files = Map("file" -> jarFile)) {
      checkStatus()
      parse(body).extract[AjaxResponse[DatasetData.App]].data.id
    }
    if (primary) {
      setPrimaryApp(datasetId, id)
    }
    id
  }

  def setPrimaryApp(datasetId: String, appId: String): Unit = {
    put(s"/api/datasets/${datasetId}/apps/primary", params = Map("d" -> compact(render("appId" -> appId)))) {
      checkStatus()
    }
  }

  def withApiKey[T](userId: Option[String])(body: => T): T = {
    userId.map { user =>
      val ts = DateTime.now
      val key = persistence.ApiKey.create(
        id = user,
        userId = user,
        apiKey = "",
        secretKey = "",
        permission = 2,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      try {
        body
      } finally {
        persistence.ApiKey.destroy(key)
      }
    }.getOrElse {
      body
    }
  }
}
