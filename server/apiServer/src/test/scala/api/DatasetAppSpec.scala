package api

import java.io.File
import java.nio.file.{ Files, Paths, StandardCopyOption }
import java.util.UUID

import api.common.DsmoqSpec
import dsmoq.controllers.AjaxResponse
import dsmoq.{ AppConf, persistence }
import dsmoq.persistence.OwnerType
import dsmoq.services.json.{ DatasetData, RangeSlice }
import org.joda.time.DateTime
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._

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

  val uuid: String = UUID.randomUUID.toString

  "get app" - {
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
      } {
        val clue = s"datasetId: ${testDatasetId}, deleted: ${datasetDeleted}, guest: ${guestAccess}, " +
          s"auth: ${authenticated}, owner: ${owner}"
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
              signIn(if (owner) "dummy1" else "dummy2")
            }
            get(s"/api/datasets/${datasetId}/app", params = Map.empty) {
              if (testDatasetId.contains("")) {
                checkStatus(404, Some("NotFound"))
              } else if (testDatasetId.contains("hello")) {
                checkStatus(404, Some("Illegal Argument"))
              } else if (!authenticated) {
                checkStatus(403, Some("Unauthorized"))
              } else if (testDatasetId.contains(uuid) || datasetDeleted) {
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
    "success" in {
      for {
        hasApp <- Seq(true, false) // Datasetのアプリの保持有無
      } {
        val clue = s"hasApp: ${hasApp}"
        withClue(clue) {
          session {
            signIn()
            val datasetId = createDataset()
            val appId = if (hasApp) createApp(datasetId) else ""

            get(s"/api/datasets/${datasetId}/app", params = Map.empty) {
              checkStatus()
              val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetData.App]]].data
              if (hasApp) {
                result.summary.total should be(1)
                result.summary.count should be(1)
                result.results.size should be(1)
                val app = result.results.head
                app.id shouldEqual appId

                delete(s"/api/datasets/${datasetId}/app") { checkStatus() }
              } else {
                result.summary.total should be(0)
                result.summary.count should be(0)
                result.results.size should be(0)
              }
            }
          }
        }
      }
    }
  }
  "add app" - {
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
        description <- Seq(Some(""), Some("description"), Some("アプリ説明"), None)
        fileParam <- Seq(None, Some(emptyFile), Some(zipFile), Some(jarFile))
      } {
        val clue = s"datasetId: ${testDatasetId}, deleted: ${datasetDeleted}, guest: ${guestAccess}, " +
          s"auth: ${authenticated}, owner: ${owner}, description: ${description} file: ${fileParam}"
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
              signIn(if (owner) "dummy1" else "dummy2")
            }
            val params = description.fold(Map.empty[String, String]) { d => Map("description" -> d) }
            val files = fileParam.fold(Map.empty[String, File]) { file => Map("file" -> file) }
            post(s"/api/datasets/${datasetId}/app", params = params, files = files) {
              if (testDatasetId.contains("")) {
                checkStatus(404, Some("NotFound"))
              } else if (!fileParam.contains(jarFile)) {
                checkStatus(400, Some("Illegal Argument"))
              } else if (testDatasetId.contains("hello")) {
                checkStatus(404, Some("Illegal Argument"))
              } else if (!authenticated) {
                checkStatus(403, Some("Unauthorized"))
              } else if (testDatasetId.contains(uuid) || datasetDeleted) {
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
    "success" in {
      session {
        signIn()
        val datasetId = createDataset()
        val names = for {
          ascii <- Seq(true, false)
          //multi <- Seq(true, false)
          hyphen <- Seq(true, false)
          under <- Seq(true, false)
          dot <- Seq(true, false)
          leadDot <- Seq(true, false)
          res <- Seq(true, false)
          ext <- Seq("", ".jar", ".zip", ".txt")
          name = "" +
            (if (leadDot) "." else "") +
            (if (ascii) "a" else "") +
            //(if (multi) "あ" else "") +
            (if (hyphen) "-" else "") +
            (if (under) "_" else "") +
            (if (ascii) "a" else "") +
            //(if (multi) "あ" else "") +
            (if (hyphen) "-" else "") +
            (if (res) "__Ljp__V1" else "") +
            (if (dot) ".1" else "") +
            ext
          if !name.isEmpty && name != "." && name != ".."
        } yield name
        for {
          name <- names
          description <- Seq(Some(""), Some("description"), Some("アプリ説明"), None)
        } {
          val clue = s"name: ${name}, description: ${description}"
          withClue(clue) {
            val path = tempDir.resolve(name)
            Files.copy(jarFile.toPath, path, StandardCopyOption.REPLACE_EXISTING)
            val params = if (description.isDefined) Map("description" -> description.get) else Map.empty
            val files = Map("file" -> path.toFile)
            post(s"/api/datasets/${datasetId}/app", params = params, files = files) {
              Files.delete(path)
              checkStatus()
              val app = parse(body).extract[AjaxResponse[DatasetData.App]].data
              app.name should be(name)
            }
            delete(s"/api/datasets/${datasetId}/app") { checkStatus() }
          }
        }
      }
    }
  }
  "update app" - {
    "invalids" in {
      for {
        testDatasetId <- Seq(Some(""), Some("hello"), Some(uuid), None) // None: create new dataset
        datasetDeleted <- Seq(true, false)
        if !(testDatasetId.isDefined && datasetDeleted)
        guestAccess <- Seq(true, false)
        if !(testDatasetId.isDefined && guestAccess)
        testAppId <- Seq("", "hello", uuid)
        authenticated <- Seq(true, false)
        owner <- Seq(true, false)
        if !(!authenticated && owner)
        description <- Seq(Some(""), Some("description"), Some("アプリ説明"), None)
        fileParam <- Seq(None, Some(emptyFile), Some(zipFile), Some(jarFile))
      } {
        val clue = s"datasetId: ${testDatasetId}, deleted: ${datasetDeleted}, guest: ${guestAccess}, " +
          s"appId: ${testAppId}, auth: ${authenticated}, owner: ${owner}, " +
          s"description: ${description}, file: ${fileParam}"
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
              signIn(if (owner) "dummy1" else "dummy2")
            }
            val params = description.fold(Map("appId" -> testAppId)) { d =>
              Map("appId" -> testAppId, "description" -> d)
            }
            val files = fileParam.map(file => Map("file" -> file)).getOrElse(Map.empty)
            post(s"/api/datasets/${datasetId}/app", params = params, files = files) {
              if (testDatasetId.contains("")) {
                checkStatus(404, Some("NotFound"))
              } else if (testAppId == "") {
                checkStatus(404, Some("Illegal Argument"))
              } else if (testDatasetId.contains("hello") || testAppId == "hello") {
                checkStatus(404, Some("Illegal Argument"))
              } else if (fileParam.isDefined && !fileParam.contains(jarFile)) {
                checkStatus(400, Some("Illegal Argument"))
              } else if (!authenticated) {
                checkStatus(403, Some("Unauthorized"))
              } else if (testDatasetId.contains(uuid) || datasetDeleted || testAppId == uuid) {
                checkStatus(404, Some("NotFound"))
              } else if (!owner) {
                checkStatus(403, Some("AccessDenied"))
              } else {
                checkStatus()
              }
            }
          }
          delete(s"/api/datasets/${datasetId}/app") {}
        }
      }
    }
    "success" in {
      session {
        signIn()
        val datasetId = createDataset()
        val appId = createApp(datasetId)
        val names = for {
          ascii <- Seq(true, false)
          //multi <- Seq(true, false)
          hyphen <- Seq(true, false)
          under <- Seq(true, false)
          dot <- Seq(true, false)
          leadDot <- Seq(true, false)
          res <- Seq(true, false)
          ext <- Seq("", ".jar", ".zip", ".txt")
          name = "" +
            (if (leadDot) "." else "") +
            (if (ascii) "a" else "") +
            //(if (multi) "あ" else "") +
            (if (hyphen) "-" else "") +
            (if (under) "_" else "") +
            (if (ascii) "a" else "") +
            //(if (multi) "あ" else "") +
            (if (hyphen) "-" else "") +
            (if (res) "__Ljp__V1" else "") +
            (if (dot) ".1" else "") +
            ext
          if !name.isEmpty && name != "." && name != ".."
        } yield name
        for {
          name <- names
          description <- Seq(Some(""), Some("description"), Some("アプリ説明"), None)
        } {
          val clue = s"name: ${name}, description: ${description}"
          withClue(clue) {
            val path = tempDir.resolve(name)
            Files.copy(jarFile.toPath, path, StandardCopyOption.REPLACE_EXISTING)
            val params = description.fold(Map("appId" -> appId)) { d => Map("appId" -> appId, "description" -> d) }
            val files = Map("file" -> path.toFile)
            post(s"/api/datasets/${datasetId}/app", params = params, files = files) {
              Files.delete(path)
              checkStatus()
              val app = parse(body).extract[AjaxResponse[DatasetData.App]].data
              app.name should be(name)
            }
          }
        }
        delete(s"/api/datasets/${datasetId}/app") { checkStatus() }
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
        hasApp <- Seq(false)
        authenticated <- Seq(true, false)
        owner <- Seq(true, false)
        if !(!authenticated && owner)
      } {
        val clue = s"datasetId: ${testDatasetId}, deleted: ${datasetDeleted}, guest: ${guestAccess}, " +
          s"auth: ${authenticated}, owner: ${owner}"
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
              signIn(if (owner) "dummy1" else "dummy2")
            }
            delete(s"/api/datasets/${datasetId}/app") {
              if (testDatasetId.contains("")) {
                checkStatus(404, Some("NotFound"))
              } else if (testDatasetId.contains("hello")) {
                checkStatus(404, Some("Illegal Argument"))
              } else if (!authenticated) {
                checkStatus(403, Some("Unauthorized"))
              } else if (testDatasetId.contains(uuid) || datasetDeleted || !hasApp) {
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
    "success" in {
      session {
        signIn()
        val datasetId = createDataset()
        for {
          hasApp <- Seq(true, false)
        } {
          val clue = s"datasetId: ${datasetId}, hasApp: ${hasApp}"
          withClue(clue) {
            if (hasApp) createApp(datasetId)
            delete(s"/api/datasets/${datasetId}/app") {
              if (!hasApp) {
                checkStatus(404, Some("NotFound"))
              } else {
                checkStatus()
              }
            }
          }
        }
      }
    }
  }
  "get app jnlp/jar file" - {
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
                  val guestAccessLevelParams = Map("d" -> compact(render("accessLevel" -> permission)))
                  put(s"/api/datasets/${datasetId}/guest_access", guestAccessLevelParams) { checkStatus() }
                }
              }
              datasetId
            }
            val primaryAppId = if (currentPrimaryApp) {
              val currentPrimaryAppId = createApp(datasetId)
              if (currentPrimaryAppDeleted) {
                delete(s"/api/datasets/${datasetId}/app") { checkStatus() }
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
                    if (user == "" || testDatasetId.contains("")) {
                      checkStatus(404, Some("NotFound"))
                    } else if (user == "hello" || testDatasetId.contains("hello")) {
                      checkStatus(404, Some("Illegal Argument"))
                    } else if (user == uuid || testDatasetId.contains(uuid) || datasetDeleted
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

  private def createApp(datasetId: String): String = {
    post(
      s"/api/datasets/${datasetId}/app",
      params = Map("description" -> "アプリ説明"),
      files = Map("file" -> jarFile)
    ) {
        checkStatus()
        parse(body).extract[AjaxResponse[DatasetData.App]].data.id
      }
  }

  private def withApiKey[T](userId: Option[String])(body: => T): T = {
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
