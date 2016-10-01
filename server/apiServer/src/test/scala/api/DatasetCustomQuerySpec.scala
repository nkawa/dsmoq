package api

import java.util.UUID

import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._

import common.DsmoqSpec
import dsmoq.controllers.AjaxResponse
import dsmoq.services.json.DatasetQuery
import dsmoq.services.json.SearchDatasetCondition

class DatasetCustomQuerySpec extends DsmoqSpec {
  val uuid = UUID.randomUUID.toString
  val validQuery = parse("""{"target":"query","operator":"contain","value":"abcd"}""")

  "save query" in {
    val multiText = "𠮷田尾骶骨表示"
    val xssText = """test","a":"b",</a><a href="javascript:alert('x');">"""
    for {
      name <- Seq(None, Some(""), Some("test"), Some(xssText), Some(multiText))
      query <- Seq(None, Some(JNull), Some(JString("")), Some(JString("hello")), Some(validQuery))
      authenticated <- Seq(true, false)
    } {
      withClue(s"name: ${name}, query: ${query}, auth: ${authenticated}") {
        session {
          if (authenticated) {
            signIn()
          }
          val params = Map("d" -> compact(render(("name" -> name) ~ ("query" -> query))))
          post(s"/api/dataset_queries", params) {
            if (name.filter(!_.isEmpty).isEmpty || query != Some(validQuery)) {
              checkStatus(400, Some("Illegal Argument"))
            } else if (!authenticated) {
              checkStatus(403, Some("Unauthorized"))
            } else {
              checkStatus()
              val data = parse(body).extract[AjaxResponse[DatasetQuery]].data
              Some(data.name) should be(name)
              data.query should be(validQuery.extract[SearchDatasetCondition])
            }
          }
        }
      }
    }
  }
  "get queries" - {
    for {
      authenticated <- Seq(true, false)
      queries <- 0 to 2
    } {
      s"auth: ${authenticated}, queries: ${queries}" in {
        session {
          signIn()
          for {
            i <- 1 to queries
          } {
            val params = Map("d" -> compact(render(("name" -> s"test ${i}") ~ ("query" -> validQuery))))
            post(s"/api/dataset_queries", params) {
              checkStatus()
              parse(body).extract[AjaxResponse[DatasetQuery]]
            }
          }
        }
        session {
          if (authenticated) {
            signIn()
          }
          get("/api/dataset_queries") {
            checkStatus()
            val data = parse(body).extract[AjaxResponse[Seq[DatasetQuery]]].data
            data.size should be(if (authenticated) queries else 0)
          }
        }
      }
    }
  }
  "get/delete query" in {
    for {
      deleteQuery <- Seq(true, false)
      testId <- Seq(None, Some(""), Some("hello"), Some(uuid))
      otherUsers <- Seq(true, false)
      if !(testId.isDefined && otherUsers)
      authenticated <- Seq(true, false)
    } {
      withClue(s"delete: ${deleteQuery}, id: ${testId}, otherUsers: ${otherUsers}") {
        val id = testId.getOrElse {
          session {
            signIn(if (otherUsers) "dummy2" else "dummy1", "password")
            val params = Map("d" -> compact(render(("name" -> "a") ~ ("query" -> validQuery))))
            post(s"/api/dataset_queries", params) {
              checkStatus()
              parse(body).extract[AjaxResponse[DatasetQuery]].data.id
            }
          }
        }
        session {
          if (authenticated) {
            signIn()
          }
          val url = s"/api/dataset_queries/${id}"
          val check = () => {
            if (testId == Some("")) {
              checkStatus(404, Some("NotFound"))
            } else if (testId == Some("hello")) {
              checkStatus(404, Some("Illegal Argument"))
            } else if (!authenticated) {
              checkStatus(403, Some("Unauthorized"))
            } else if (testId == Some(uuid) || otherUsers) {
              checkStatus(404, Some("NotFound"))
            } else {
              checkStatus()
            }
          }
          if (deleteQuery) {
            delete(url)(check())
          } else {
            get(url)(check())
          }
        }
      }
    }
  }
}
