package api

import java.util.UUID

import common.DsmoqSpec
import scalikejdbc.config.DBsWithEnv

class GuestHeaderSpec extends DsmoqSpec {
  "Guest header test" in {
    for {
      sessionUser <- Seq(true, false)
      resource <- Seq(true, false)
      permission <- Seq(true, false)
      innerError <- Seq(true, false)
      if resource || !permission
    } {
      guestHeaderCheck(sessionUser, resource, permission, innerError) {
        if (!innerError) {
          header.get("X-Dsmoq-Guest") should be(Some((!sessionUser).toString))
        }
      }
    }
  }

  def guestHeaderCheck(sessionUser: Boolean, resource: Boolean, permission: Boolean, innerError: Boolean)(expected: => Any): Unit = {
    withClue(s"sessionUser: ${sessionUser}, resource: ${resource}, permission: ${permission}, innerError: ${innerError}") {
      val uuid = UUID.randomUUID.toString
      val datasetId = session {
        signIn()
        createDataset(permission)
      }
      session {
        if (sessionUser) {
          signIn(if (permission) "dummy1" else "dummy2")
        }
        val proc = () => {
          get(s"/api/datasets/${if (resource) datasetId else uuid}") {
            expected
          }
        }
        if (innerError) {
          dbDisconnectedBlock {
            proc()
          }
        } else {
          proc()
        }
      }
    }
  }

  def dbDisconnectedBlock[T](procedure: => T): T = {
    DBsWithEnv("test").close()
    try {
      procedure
    } finally {
      DBsWithEnv("test").setup()
    }
  }
}
