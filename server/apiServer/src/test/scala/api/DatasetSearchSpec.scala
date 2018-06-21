package api

import java.util.UUID

import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._

import common.DsmoqSpec
import dsmoq.AppConf
import dsmoq.controllers.AjaxResponse
import dsmoq.persistence
import dsmoq.persistence.{ OwnerType, UserAccessLevel, GroupAccessLevel, GroupMemberRole }
import dsmoq.persistence.PostgresqlHelper.PgConditionSQLBuilder
import dsmoq.persistence.PostgresqlHelper.PgSQLSyntaxType
import dsmoq.services.json.DatasetData.DatasetsSummary
import dsmoq.services.json.GroupData.Group
import dsmoq.services.json.RangeSlice
import scalikejdbc.DB
import scalikejdbc.config.DBs
import scalikejdbc.withSQL
import scalikejdbc.update
import scalikejdbc.interpolation.Implicits.scalikejdbcSQLInterpolationImplicitDef
import scalikejdbc.interpolation.Implicits.scalikejdbcSQLSyntaxToStringImplicitDef

class DatasetSearchSpec extends DsmoqSpec {
  val dummy2Id = "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04"
  val uuid = UUID.randomUUID.toString
  val validQuery = parse("""{"target":"query","operator":"contain","value":"test"}""")

  "invalids" in {
    val ds = session {
      signIn()
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
  "シンプル検索" - {
    "query" in {
      session {
        signIn()
        val d1 = createDataset(name = "abc")
        val d2 = createDataset(name = "def")
        val query = (x: String) =>
          ("target" -> "query") ~
            ("operator" -> "contain") ~
            ("value" -> x)

        paramTest(query("a"), Seq(d1))
        paramTest(query("d"), Seq(d2))
      }
    }
    "permissions" in {
      val ds = session {
        signIn()
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
      val query = (x: String) =>
        ("target" -> "query") ~
          ("operator" -> "contain") ~
          ("value" -> x)
      val params = Map("d" -> compact(render(("query" -> query("b")))))
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
  }
  "アドバンスト検索" - {
    "query" in {
      session {
        signIn()
        val d1 = createDataset(name = "abc")
        val d2 = createDataset(name = "def")
        val query = (x: String, y: String, z: String) => ("operator" -> "or") ~
          ("value" -> Seq(
            ("operator" -> "and") ~
              ("value" -> Seq(
                ("target" -> x) ~
                  ("operator" -> y) ~
                  ("value" -> z)
              ))
          ))
        paramTest(query("query", "contain", "a"), Seq(d1))
        paramTest(query("query", "not-contain", "a"), Seq(d2))
      }
    }
    "owner" in {
      val d1 = session {
        signIn()
        createDataset(allowGuest = true)
      }
      val d2 = session {
        signIn("dummy2", "password")
        createDataset(allowGuest = true)
      }
      val query = (x: String, y: String, z: String) => ("operator" -> "or") ~
        ("value" -> Seq(
          ("operator" -> "and") ~
            ("value" -> Seq(
              ("target" -> x) ~
                ("operator" -> y) ~
                ("value" -> z)
            ))
        ))
      paramTest(query("owner", "equal", "dummy1"), Seq(d1))
      paramTest(query("owner", "not-equal", "dummy1"), Seq(d2))
    }
    "tag" in {
      session {
        signIn()
        val d1 = createDataset()
        setAttribute(d1, Seq("tuvwx" -> "$tag"))
        val d2 = createDataset()
        val query = (x: String, y: String) => ("operator" -> "or") ~
          ("value" -> Seq(
            ("operator" -> "and") ~
              ("value" -> Seq(
                ("target" -> x) ~
                  ("value" -> y)
              ))
          ))
        paramTest(query("tag", "tuvwx"), Seq(d1))
      }
    }
    "attribute" in {
      session {
        signIn()
        val d1 = createDataset()
        setAttribute(d1, Seq("abc" -> "def"))
        val d2 = createDataset()
        val query = (x: String, y: String) =>
          ("operator" -> "or") ~
            ("value" -> Seq(
              ("operator" -> "and") ~
                ("value" -> Seq(
                  ("target" -> "attribute") ~
                    ("key" -> x) ~
                    ("value" -> y)
                ))
            ))
        paramTest(query("abc", "def"), Seq(d1))
        paramTest(query("xyz", "stu"), Seq.empty)
        paramTest(query("", ""), Seq(d2, d1))
      }
    }
    "public" in {
      session {
        signIn()
        val d1 = createDataset(allowGuest = true)
        val d2 = createDataset(allowGuest = false)
        val query = (x: String) =>
          ("operator" -> "or") ~
            ("value" -> Seq(
              ("operator" -> "and") ~
                ("value" -> Seq(
                  ("target" -> "public") ~
                    ("value" -> x)
                ))
            ))
        paramTest(query("public"), Seq(d1))
        paramTest(query("private"), Seq(d2))
      }
    }
    "total-size(1ファイル,byteチェック)" in {
      session {
        signIn()
        val d1 = createDataset()
        val query = (x: String, y: Double, z: String) =>
          ("operator" -> "or") ~
            ("value" -> Seq(
              ("operator" -> "and") ~
                ("value" -> Seq(
                  ("target" -> "total-size") ~
                    ("operator" -> x) ~
                    ("value" -> y) ~
                    ("unit" -> z)
                ))
            ))

        // fileSize = 0
        setFileSize(d1, 0)
        paramTest(query("le", -1, "byte"), Seq.empty)
        paramTest(query("le", 0, "byte"), Seq(d1))
        paramTest(query("le", 1, "byte"), Seq(d1))
        paramTest(query("ge", -1, "byte"), Seq(d1))
        paramTest(query("ge", 0, "byte"), Seq(d1))
        paramTest(query("ge", 1, "byte"), Seq.empty)

        // fileSize = 1
        setFileSize(d1, 1)
        paramTest(query("le", -1, "byte"), Seq.empty)
        paramTest(query("le", 0, "byte"), Seq.empty)
        paramTest(query("le", 1, "byte"), Seq(d1))
        paramTest(query("ge", -1, "byte"), Seq(d1))
        paramTest(query("ge", 0, "byte"), Seq(d1))
        paramTest(query("ge", 1, "byte"), Seq(d1))

        // fileSize = 2
        setFileSize(d1, 2)
        paramTest(query("le", -1, "byte"), Seq.empty)
        paramTest(query("le", 0, "byte"), Seq.empty)
        paramTest(query("le", 1, "byte"), Seq.empty)
        paramTest(query("ge", -1, "byte"), Seq(d1))
        paramTest(query("ge", 0, "byte"), Seq(d1))
        paramTest(query("ge", 1, "byte"), Seq(d1))
      }
    }
    "total-size(1ファイル,kbチェック)" in {
      session {
        signIn()
        val d1 = createDataset()
        val query = (x: String, y: Double, z: String) =>
          ("operator" -> "or") ~
            ("value" -> Seq(
              ("operator" -> "and") ~
                ("value" -> Seq(
                  ("target" -> "total-size") ~
                    ("operator" -> x) ~
                    ("value" -> y) ~
                    ("unit" -> z)
                ))
            ))

        // fileSize = 1 * 1024 = 1KB
        setFileSize(d1, 1 * 1024)
        paramTest(query("le", 1023, "byte"), Seq.empty)
        paramTest(query("le", 1024, "byte"), Seq(d1))
        paramTest(query("le", 1025, "byte"), Seq(d1))
        paramTest(query("ge", 1023, "byte"), Seq(d1))
        paramTest(query("ge", 1024, "byte"), Seq(d1))
        paramTest(query("ge", 1025, "byte"), Seq.empty)
        paramTest(query("le", 0.9, "kb"), Seq.empty)
        paramTest(query("le", 1.0, "kb"), Seq(d1))
        paramTest(query("le", 1.1, "kb"), Seq(d1))
        paramTest(query("ge", 0.9, "kb"), Seq(d1))
        paramTest(query("ge", 1.0, "kb"), Seq(d1))
        paramTest(query("ge", 1.1, "kb"), Seq.empty)
      }
    }
    "total-size(1ファイル,mbチェック)" in {
      session {
        signIn()
        val d1 = createDataset()
        val query = (x: String, y: Double, z: String) =>
          ("operator" -> "or") ~
            ("value" -> Seq(
              ("operator" -> "and") ~
                ("value" -> Seq(
                  ("target" -> "total-size") ~
                    ("operator" -> x) ~
                    ("value" -> y) ~
                    ("unit" -> z)
                ))
            ))

        // fileSize = 1 * 1024 * 1024 = 1MB
        setFileSize(d1, 1 * 1024 * 1024)
        paramTest(query("le", (1024 * 1024) - 1, "byte"), Seq.empty)
        paramTest(query("le", (1024 * 1024) + 0, "byte"), Seq(d1))
        paramTest(query("le", (1024 * 1024) + 1, "byte"), Seq(d1))
        paramTest(query("ge", (1024 * 1024) - 1, "byte"), Seq(d1))
        paramTest(query("ge", (1024 * 1024) + 0, "byte"), Seq(d1))
        paramTest(query("ge", (1024 * 1024) + 1, "byte"), Seq.empty)
        paramTest(query("le", 1024 - 1, "kb"), Seq.empty)
        paramTest(query("le", 1024 + 0, "kb"), Seq(d1))
        paramTest(query("le", 1024 + 1, "kb"), Seq(d1))
        paramTest(query("ge", 1024 - 1, "kb"), Seq(d1))
        paramTest(query("ge", 1024 + 0, "kb"), Seq(d1))
        paramTest(query("ge", 1024 + 1, "kb"), Seq.empty)
        paramTest(query("le", 0.9, "mb"), Seq.empty)
        paramTest(query("le", 1.0, "mb"), Seq(d1))
        paramTest(query("le", 1.1, "mb"), Seq(d1))
        paramTest(query("ge", 0.9, "mb"), Seq(d1))
        paramTest(query("ge", 1.0, "mb"), Seq(d1))
        paramTest(query("ge", 1.1, "mb"), Seq.empty)
      }
    }
    "total-size(1ファイル,gbチェック)" in {
      session {
        signIn()
        val d1 = createDataset()
        val query = (x: String, y: Double, z: String) =>
          ("operator" -> "or") ~
            ("value" -> Seq(
              ("operator" -> "and") ~
                ("value" -> Seq(
                  ("target" -> "total-size") ~
                    ("operator" -> x) ~
                    ("value" -> y) ~
                    ("unit" -> z)
                ))
            ))

        // fileSize = 1 * 1024 * 1024 * 1024 = 1GB
        setFileSize(d1, 1 * 1024 * 1024 * 1024)
        paramTest(query("le", (1024 * 1024 * 1024) - 1, "byte"), Seq.empty)
        paramTest(query("le", (1024 * 1024 * 1024) + 0, "byte"), Seq(d1))
        paramTest(query("le", (1024 * 1024 * 1024) + 1, "byte"), Seq(d1))
        paramTest(query("ge", (1024 * 1024 * 1024) - 1, "byte"), Seq(d1))
        paramTest(query("ge", (1024 * 1024 * 1024) + 0, "byte"), Seq(d1))
        paramTest(query("ge", (1024 * 1024 * 1024) + 1, "byte"), Seq.empty)
        paramTest(query("le", (1024 * 1024) - 1, "kb"), Seq.empty)
        paramTest(query("le", (1024 * 1024) + 0, "kb"), Seq(d1))
        paramTest(query("le", (1024 * 1024) + 1, "kb"), Seq(d1))
        paramTest(query("ge", (1024 * 1024) - 1, "kb"), Seq(d1))
        paramTest(query("ge", (1024 * 1024) + 0, "kb"), Seq(d1))
        paramTest(query("ge", (1024 * 1024) + 1, "kb"), Seq.empty)
        paramTest(query("le", 1024 - 1, "mb"), Seq.empty)
        paramTest(query("le", 1024 + 0, "mb"), Seq(d1))
        paramTest(query("le", 1024 + 1, "mb"), Seq(d1))
        paramTest(query("ge", 1024 - 1, "mb"), Seq(d1))
        paramTest(query("ge", 1024 + 0, "mb"), Seq(d1))
        paramTest(query("ge", 1024 + 1, "mb"), Seq.empty)
        paramTest(query("le", 0.9, "gb"), Seq.empty)
        paramTest(query("le", 1.0, "gb"), Seq(d1))
        paramTest(query("le", 1.1, "gb"), Seq(d1))
        paramTest(query("ge", 0.9, "gb"), Seq(d1))
        paramTest(query("ge", 1.0, "gb"), Seq(d1))
        paramTest(query("ge", 1.1, "gb"), Seq.empty)
      }
    }
    "total-size(3ファイル)" in {
      session {
        signIn()
        val d1 = createDataset()
        val d2 = createDataset()
        val d3 = createDataset()
        val query = (x: String, y: Double, z: String) =>
          ("operator" -> "or") ~
            ("value" -> Seq(
              ("operator" -> "and") ~
                ("value" -> Seq(
                  ("target" -> "total-size") ~
                    ("operator" -> x) ~
                    ("value" -> y) ~
                    ("unit" -> z)
                ))
            ))
        // fileSize = 9, 10, 11
        setFileSize(d1, 9)
        setFileSize(d2, 10)
        setFileSize(d3, 11)
        paramTest(query("ge", 9, "byte"), Seq(d3, d2, d1))
        paramTest(query("ge", 10, "byte"), Seq(d3, d2))
        paramTest(query("ge", 11, "byte"), Seq(d3))
        paramTest(query("le", 9, "byte"), Seq(d1))
        paramTest(query("le", 10, "byte"), Seq(d2, d1))
        paramTest(query("le", 11, "byte"), Seq(d3, d2, d1))
      }
    }
    "num-of-files(1ファイル)" in {
      session {
        signIn()
        val d1 = createDataset()
        val query = (x: String, y: Double) =>
          ("operator" -> "or") ~
            ("value" -> Seq(
              ("operator" -> "and") ~
                ("value" -> Seq(
                  ("target" -> "num-of-files") ~
                    ("operator" -> x) ~
                    ("value" -> y)
                ))
            ))

        // filenum = 0
        setFileNum(d1, 0)
        paramTest(query("ge", -1), Seq(d1))
        paramTest(query("ge", 0), Seq(d1))
        paramTest(query("ge", 1), Seq.empty)
        paramTest(query("le", -1), Seq.empty)
        paramTest(query("le", 0), Seq(d1))
        paramTest(query("le", 1), Seq(d1))

        // filenum = 1
        setFileNum(d1, 1)
        paramTest(query("ge", -1), Seq(d1))
        paramTest(query("ge", 0), Seq(d1))
        paramTest(query("ge", 1), Seq(d1))
        paramTest(query("le", -1), Seq.empty)
        paramTest(query("le", 0), Seq.empty)
        paramTest(query("le", 1), Seq(d1))

        // filenum = 2
        setFileNum(d1, 2)
        paramTest(query("ge", -1), Seq(d1))
        paramTest(query("ge", 0), Seq(d1))
        paramTest(query("ge", 1), Seq(d1))
        paramTest(query("le", -1), Seq.empty)
        paramTest(query("le", 0), Seq.empty)
        paramTest(query("le", 1), Seq.empty)
      }
    }
    "num-of-files(3ファイル)" in {
      session {
        signIn()
        val d1 = createDataset()
        val d2 = createDataset()
        val d3 = createDataset()
        val query = (x: String, y: Double) =>
          ("operator" -> "or") ~
            ("value" -> Seq(
              ("operator" -> "and") ~
                ("value" -> Seq(
                  ("target" -> "num-of-files") ~
                    ("operator" -> x) ~
                    ("value" -> y)
                ))
            ))

        // filenum = 9, 10, 11
        setFileNum(d1, 9)
        setFileNum(d2, 10)
        setFileNum(d3, 11)
        paramTest(query("ge", 9), Seq(d3, d2, d1))
        paramTest(query("ge", 10), Seq(d3, d2))
        paramTest(query("ge", 11), Seq(d3))
        paramTest(query("le", 9), Seq(d1))
        paramTest(query("le", 10), Seq(d2, d1))
        paramTest(query("le", 11), Seq(d3, d2, d1))
      }
    }
    "permissions" in {
      val ds = session {
        signIn()
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
      val query = (x: String, y: String, z: String) => ("operator" -> "or") ~
        ("value" -> Seq(
          ("operator" -> "and") ~
            ("value" -> Seq(
              ("target" -> x) ~
                ("operator" -> y) ~
                ("value" -> z)
            ))
        ))
      val params = Map("d" -> compact(render(("query" -> query("query", "contain", "b")))))
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
    "組み合わせ条件(ANDのみ)" in {
      session {
        signIn()

        val d1 = createDataset(name = "abc")
        val d2 = createDataset(name = "def")
        val queryRoot = (x: Seq[JValue]) =>
          ("operator" -> "or") ~
            ("value" -> x)
        val queryAndContainer = (x: Seq[JValue]) =>
          ("operator" -> "and") ~
            ("value" -> x)
        val queryKeyword = (x: String, y: String, z: String) =>
          ("target" -> x) ~
            ("operator" -> y) ~
            ("value" -> z)

        val query1 = queryRoot(Seq(queryAndContainer(Seq(
          queryKeyword("query", "contain", "a")
        ))))
        paramTest(query1, Seq(d1))
        val query2 = queryRoot(Seq(queryAndContainer(Seq(
          queryKeyword("query", "contain", "a"),
          queryKeyword("query", "contain", "b")
        ))))
        paramTest(query2, Seq(d1))
        val query3 = queryRoot(Seq(queryAndContainer(Seq(
          queryKeyword("query", "contain", "a"),
          queryKeyword("query", "contain", "b"),
          queryKeyword("query", "not-contain", "c")
        ))))
        paramTest(query3, Seq.empty)
      }
    }
    "組み合わせ条件(ORのみ)" in {
      session {
        signIn()

        val d1 = createDataset(name = "abc")
        val d2 = createDataset(name = "def")
        val queryRoot = (x: Seq[JValue]) =>
          ("operator" -> "or") ~
            ("value" -> x)
        val queryAndContainer = (x: Seq[JValue]) =>
          ("operator" -> "and") ~
            ("value" -> x)
        val queryKeyword = (x: String, y: String, z: String) =>
          ("target" -> x) ~
            ("operator" -> y) ~
            ("value" -> z)

        val query1 = queryRoot(Seq(
          queryAndContainer(
            Seq(queryKeyword("query", "contain", "a"))
          ),
          queryAndContainer(
            Seq(queryKeyword("query", "contain", "d"))
          )
        ))
        paramTest(query1, Seq(d2, d1))

        val query2 = queryRoot(Seq(
          queryAndContainer(
            Seq(queryKeyword("query", "contain", "a"))
          ),
          queryAndContainer(
            Seq(queryKeyword("query", "not-contain", "d"))
          )
        ))
        paramTest(query2, Seq(d1))
      }
    }
    "組み合わせ条件(AND,OR)" in {
      session {
        signIn()

        val d1 = createDataset(name = "abc")
        val d2 = createDataset(name = "def")
        val queryRoot = (x: Seq[JValue]) =>
          ("operator" -> "or") ~
            ("value" -> x)
        val queryAndContainer = (x: Seq[JValue]) =>
          ("operator" -> "and") ~
            ("value" -> x)
        val queryKeyword = (x: String, y: String, z: String) =>
          ("target" -> x) ~
            ("operator" -> y) ~
            ("value" -> z)

        val query1 = queryRoot(Seq(
          queryAndContainer(
            Seq(
              queryKeyword("query", "contain", "a"),
              queryKeyword("query", "contain", "d")
            )
          ),
          queryAndContainer(
            Seq(queryKeyword("query", "contain", "d"))
          )
        ))
        paramTest(query1, Seq(d2))
        val query2 = queryRoot(Seq(
          queryAndContainer(
            Seq(
              queryKeyword("query", "contain", "a"),
              queryKeyword("query", "not-contain", "d")
            )
          ),
          queryAndContainer(
            Seq(queryKeyword("query", "not-contain", "d"))
          )
        ))
        paramTest(query2, Seq(d1))
      }
    }
  }

  def paramTest(query: JValue, expecteds: Seq[String]): Unit = {
    val params = Map("d" -> compact(render(("query" -> query))))
    withClue(s"query: ${compact(render(query))}") {
      get("/api/datasets", params) {
        checkStatus()
        val data = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]].data
        data.results.map(_.id) should be(expecteds)
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
}
