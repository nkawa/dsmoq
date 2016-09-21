package api

import java.util.UUID
import java.util.ResourceBundle

import org.eclipse.jetty.servlet.ServletHolder
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{ BeforeAndAfter, FreeSpec }
import org.scalatra.servlet.MultipartConfig
import org.scalatra.test.scalatest.ScalatraSuite

import _root_.api.api.logic.SpecCommonLogic
import dsmoq.AppConf
import dsmoq.controllers.AjaxResponse
import dsmoq.controllers.ApiController
import dsmoq.controllers.json.SearchDatasetParams
import dsmoq.controllers.json.SearchDatasetParamsSerializer
import dsmoq.persistence
import dsmoq.persistence.{ DefaultAccessLevel, OwnerType, UserAccessLevel, GroupAccessLevel, GroupMemberRole }
import dsmoq.persistence.PostgresqlHelper.PgConditionSQLBuilder
import dsmoq.persistence.PostgresqlHelper.PgSQLSyntaxType
import dsmoq.services.json.DatasetData.Dataset
import dsmoq.services.json.DatasetData.DatasetsSummary
import dsmoq.services.json.GroupData.Group
import dsmoq.services.json.RangeSlice
import dsmoq.services.json.SearchDatasetCondition
import dsmoq.services.json.SearchDatasetConditionSerializer
import scalikejdbc._
import scalikejdbc.config.{ DBsWithEnv, DBs }
import scalikejdbc.interpolation.Implicits.scalikejdbcSQLInterpolationImplicitDef
import scalikejdbc.interpolation.Implicits.scalikejdbcSQLSyntaxToStringImplicitDef

class DatasetSearchSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormats: Formats = DefaultFormats +
    SearchDatasetConditionSerializer + SearchDatasetParamsSerializer

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

  val dummy2Id = "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04"
  val uuid = UUID.randomUUID.toString
  val validQuery = parse("""{"target":"query","operator":"contain","value":"test"}""")

  "invalids" in {
    val ds = session {
      signInDummy1()
      (1 to 2).map(_ => createDataset(allowGuest = true)).toSeq.reverse
    }
    for {
      query <- Seq(None, Some(JNull), Some(JString("")), Some(JString("test")), Some(validQuery))
      limit <- Seq(None, Some(JString("a"))) ++ (-1 to 1).map(x => Some(JInt(x)))
      offset <- Seq(None, Some(JString("a"))) ++ (-1 to 1).map(x => Some(JInt(x)))
    } {
      val params = Map("d" -> compact(render(("query" -> query) ~ ("limit" -> limit) ~ ("offset" -> offset))))
      withClue(params.toString) {
        get("/api/datasets", params) {
          if (limit == Some(JInt(-1)) || offset == Some(JInt(-1))) {
            checkStatus(400, Some("Illegal Argument"))
          } else if (query != Some(validQuery)) {
            // 旧系API
            checkStatus()
          } else {
            // 新系API
            checkStatus()
            val data = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]].data
            val o = offset.collect { case JInt(x) => x.toInt }.getOrElse(0)
            val lim = limit.collect { case JInt(x) => x.toInt }.getOrElse(20)
            data.summary.total should be(ds.size)
            data.summary.count should be(lim)
            data.summary.offset should be(o)
            data.results.map(_.id) should be(ds.drop(o).take(lim))
          }
        }
      }
    }
  }
  "query targets" - {
    "query" in {
      session {
        signInDummy1()
        val d1 = createDataset(name = "abc")
        val d2 = createDataset(name = "def")
        paramTest("query", JString("a"), Seq("contain", "not-contain"), Seq(d1, d2))
      }
    }
    "owner" in {
      val d1 = session {
        signInDummy1()
        createDataset(allowGuest = true)
      }
      val d2 = session {
        signIn("dummy2", "password")
        createDataset(allowGuest = true)
      }
      paramTest("owner", JString("dummy1"), Seq("equal", "not-equal"), Seq(d1, d2))
    }
    "tag" in {
      session {
        signInDummy1()
        val d1 = createDataset()
        setAttribute(d1, Seq("tuvwx" -> "$tag"))
        val d2 = createDataset()
        paramTest("tag", JString("tuvwx"), Seq(""), Seq(d1))
      }
    }
    "attribute" in {
      session {
        signInDummy1()
        val d1 = createDataset()
        setAttribute(d1, Seq("abc" -> "def"))
        val d2 = createDataset()
        for {
          key <- Seq(None, Some(""), Some("abc"), Some("xyz"))
          value <- Seq(None, Some(""), Some("def"), Some("stu"))
        } {
          withClue(s"key: ${key}, value: ${value}") {
            val query = render(("target" -> "attribute") ~ ("key" -> key) ~ ("value" -> value))
            val params = Map("d" -> compact(render(("query" -> query))))
            get("/api/datasets", params) {
              checkStatus()
              val data = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]].data
              val expected = if (key == Some("xyz") || value == Some("stu")) {
                Seq.empty
              } else if (key == Some("abc") || value == Some("def")) {
                Seq(d1)
              } else {
                Seq(d2, d1)
              }
              data.results.map(_.id) should be(expected)
            }
          }
        }
      }
    }
    "public" in {
      session {
        signInDummy1()
        val d1 = createDataset(allowGuest = true)
        val d2 = createDataset(allowGuest = false)
        paramTest("public", JString("public"), Seq(""), Seq(d1))
        paramTest("public", JString("private"), Seq(""), Seq(d2))
      }
    }
    "total-size" in {
      session {
        signInDummy1()
        val d1 = createDataset()
        val d2 = createDataset()
        val d3 = createDataset()
        for {
          operator <- Seq(None, Some(""), Some("le"), Some("ge"), Some("xx"))
          value <- Seq(None, Some(JString("")), Some(JString("aa")), Some(JInt(-1)), Some(JDouble(10.0)), Some(JInt(10)))
          unit <- Seq(None, Some(JString("")), Some(JString("byte")), Some(JString("kb")), Some(JString("mb")), Some(JString("gb")), Some(JString("xx")))
        } {
          withClue(s"operator: ${operator}, value: ${value}, unit: ${unit}") {
            val m = unit match {
              case Some(JString("kb")) => 1024L
              case Some(JString("mb")) => 1024L * 1024
              case Some(JString("gb")) => 1024L * 1024 * 1024
              case _ => 1
            }
            setFileSize(d1, 9 * m)
            setFileSize(d2, 10 * m)
            setFileSize(d3, 11 * m)
            val query = render(("target" -> "total-size") ~ ("operator" -> operator) ~ ("value" -> value) ~ ("unit" -> unit))
            val params = Map("d" -> compact(render(("query" -> query))))
            get("/api/datasets", params) {
              checkStatus()
              val data = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]].data
              val v = value.flatMap(_.extractOpt[Double])
              val expected = (operator, v) match {
                case (Some("le"), Some(10)) => Seq(d2, d1)
                case (Some("le"), _) => Seq.empty
                case (_, Some(10)) => Seq(d3, d2)
                case _ => Seq(d3, d2, d1)
              }
              data.results.map(_.id) should be(expected)
            }
          }
        }
      }
    }
    "num-of-files" in {
      session {
        signInDummy1()
        val d1 = createDataset()
        setFileNum(d1, 9)
        val d2 = createDataset()
        setFileNum(d2, 10)
        val d3 = createDataset()
        setFileNum(d3, 11)
        for {
          operator <- Seq(None, Some(""), Some("le"), Some("ge"), Some("xx"))
          value <- Seq(None, Some(JString("")), Some(JString("aa")), Some(JInt(-1)), Some(JDouble(10.0)), Some(JInt(10)))
        } {
          withClue(s"operator: ${operator}, value: ${value}") {
            val query = render(("target" -> "num-of-files") ~ ("operator" -> operator) ~ ("value" -> value))
            val params = Map("d" -> compact(render(("query" -> query))))
            get("/api/datasets", params) {
              checkStatus()
              val data = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]].data
              val v = value.flatMap(_.extractOpt[Int])
              val expected = (operator, v) match {
                case (Some("le"), Some(10)) => Seq(d2, d1)
                case (Some("le"), _) => Seq.empty
                case (_, Some(10)) => Seq(d3, d2)
                case _ => Seq(d3, d2, d1)
              }
              data.results.map(_.id) should be(expected)
            }
          }
        }
      }
    }
  }
  "array params" in {
    val trueElement = render(("target" -> "query") ~ ("value" -> "test"))
    val falseElement = render(("target" -> "query") ~ ("value" -> "abc"))
    val andTrueElement = render(("operator" -> "and") ~ ("value" -> render(Seq(trueElement))))
    val andFalseElement = render(("operator" -> "and") ~ ("value" -> render(Seq(falseElement))))
    val orTrueElement = render(("operator" -> "or") ~ ("value" -> render(Seq(trueElement))))
    val orFalseElement = render(("operator" -> "or") ~ ("value" -> render(Seq(falseElement))))
    session {
      signInDummy1()
      val id = createDataset()
      for {
        operator <- Seq("or", "and")
        trueElements <- 0 to 1
        falseElements <- 0 to 1
        andTrueElements <- 0 to 1
        andFalseElements <- 0 to 1
        orTrueElements <- 0 to 1
        orFalseElements <- 0 to 1
      } {
        val trues = trueElements + andTrueElements + orTrueElements
        val falses = falseElements + andFalseElements + orFalseElements
        val value = Seq(
          (1 to trueElements).map(_ => trueElement),
          (1 to falseElements).map(_ => falseElement),
          (1 to andTrueElements).map(_ => andTrueElement),
          (1 to andFalseElements).map(_ => andFalseElement),
          (1 to orTrueElements).map(_ => orTrueElement),
          (1 to orFalseElements).map(_ => orFalseElement)
        ).flatten
        val query = render(("operator" -> operator) ~ ("value" -> value))
        val params = Map("d" -> compact(render(("query" -> query))))
        withClue(params) {
          get("/api/datasets", params) {
            checkStatus()
            val data = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]].data
            val expected = (operator, trues, falses) match {
              case (_, 0, 0) => Seq(id)
              case ("or", 0, _) => Seq.empty
              case ("or", _, _) => Seq(id)
              case ("and", _, 0) => Seq(id)
              case ("and", _, _) => Seq.empty
              case _ => Seq(id)
            }
            data.results.map(_.id) should be(expected)
          }
        }
      }
    }
  }
  "permissions" in {
    val ds = session {
      signInDummy1()
      val gid = createGroup(Seq(dummy2Id))
      for {
        found <- Seq(true, false)
        guestAccess <- Seq(true, false)
        userAccess <- Seq(true, false)
        groupAccess <- Seq(true, false)
      } yield {
        val id = createDataset(name = if (found) "abc" else "xxx", allowGuest = guestAccess)
        val acl = Seq(
          if (userAccess) Some((dummy2Id, true)) else None,
          if (groupAccess) Some((gid, false)) else None
        ).flatten
        if (!acl.isEmpty) {
          setDatasetAcl(id, acl)
        }
        (found, guestAccess, userAccess, groupAccess, id)
      }
    }.toSeq.reverse
    val query = render(("target" -> "query") ~ ("value" -> "b"))
    val params = Map("d" -> compact(render(("query" -> query))))
    for {
      guest <- Seq(true, false)
    } yield {
      withClue(s"guest: ${guest}, params: ${params}") {
        session {
          if (!guest) {
            signIn("dummy2", "password")
          }
          get("/api/datasets", params) {
            val data = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]].data
            val expected = ds.collect {
              case (true, true, _, _, id) => id
              case (true, false, ua, ga, id) if (!guest && (ua || ga)) => id
            }
            data.results.map(_.id) should be(expected)
          }
        }
      }
    }
  }

  def paramTest(target: String, value: JValue, operator: Seq[String], expecteds: Seq[String]): Unit = {
    (operator zip expecteds).foreach {
      case (operator, expected) =>
        withClue(operator) {
          val query = render(("target" -> target) ~ ("operator" -> operator) ~ ("value" -> value))
          val params = Map("d" -> compact(render(("query" -> query))))
          get("/api/datasets", params) {
            checkStatus()
            val data = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]].data
            data.results.map(_.id) should be(Seq(expected))
          }
        }
    }
  }

  def setAttribute(id: String, attributes: Seq[(String, String)]): Unit = {
    val params = Map(
      "d" -> compact(
        render(
          ("name" -> "with attribute") ~
            ("description" -> "") ~
            ("license" -> AppConf.defaultLicenseId) ~
            ("attributes" -> attributes.map { case (k, v) => ("name" -> k) ~ ("value" -> v) })
        )
      )
    )
    put(s"/api/datasets/${id}/metadata", params) {
      checkStatus()
    }
  }

  def setFileNum(id: String, num: Int): Unit = {
    DB.localTx { implicit s =>
      withSQL {
        val d = persistence.Dataset.column
        update(persistence.Dataset)
          .set(d.filesCount -> num)
          .where.eqUuid(d.id, id)
      }.update.apply()
    }
  }

  def setFileSize(id: String, size: Long): Unit = {
    DB.localTx { implicit s =>
      withSQL {
        val d = persistence.Dataset.column
        update(persistence.Dataset)
          .set(d.filesSize -> size)
          .where.eqUuid(d.id, id)
      }.update.apply()
    }
  }

  def signInDummy1(): Unit = {
    signIn("dummy1", "password")
  }

  def signIn(id: String, password: String): Unit = {
    post("/api/signin", params = Map("d" -> compact(render(("id" -> id) ~ ("password" -> password))))) {
      checkStatus()
    }
  }

  def createDataset(name: String = "test", allowGuest: Boolean = false): String = {
    val params = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> name)
    val dataset = post("/api/datasets", params) {
      checkStatus()
      parse(body).extract[AjaxResponse[Dataset]].data
    }
    if (allowGuest) {
      val params = Map("d" -> compact(render(("accessLevel" -> JInt(DefaultAccessLevel.FullPublic)))))
      put(s"/api/datasets/${dataset.id}/guest_access", params) {
        checkStatus()
      }
    }
    dataset.id
  }

  def createGroup(members: Seq[String] = Seq.empty): String = {
    val createParams = Map("d" -> compact(render(("name" -> "gg") ~ ("description" -> ""))))
    val gid = post("/api/groups", createParams) {
      checkStatus()
      parse(body).extract[AjaxResponse[Group]].data.id
    }
    members.foreach { member =>
      val addParams = Map("d" -> compact(render(Seq(("userId" -> member) ~ ("role" -> JInt(GroupMemberRole.Member))))))
      post(s"/api/groups/${gid}/members", addParams) {
        checkStatus()
      }
    }
    gid
  }

  def setDatasetAcl(datasetId: String, acl: Seq[(String, Boolean)]): Unit = {
    val params = Map(
      "d" -> compact(
        render(
          acl.map {
            case (id, user) =>
              ("id" -> id) ~
                ("ownerType" -> JInt(if (user) OwnerType.User else OwnerType.Group)) ~
                ("accessLevel" -> JInt(if (user) UserAccessLevel.FullPublic else GroupAccessLevel.FullPublic))
          }
        )
      )
    )
    post(s"/api/datasets/${datasetId}/acl", params) {
      checkStatus()
    }
  }

  def checkStatus(expectedCode: Int = 200, expectedAjaxStatus: Option[String] = Some("OK")): Unit = {
    status should be(expectedCode)
    expectedAjaxStatus.foreach { expected =>
      val result = parse(body).extract[AjaxResponse[Any]]
      result.status should be(expected)
    }
  }
}
