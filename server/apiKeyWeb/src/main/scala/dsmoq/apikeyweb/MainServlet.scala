package dsmoq.apikeyweb

import java.util.UUID

import dsmoq.persistence.{ApiKey, User}
import org.apache.commons.codec.digest.DigestUtils
import org.joda.time.DateTime
import scalikejdbc._

class MainServlet extends ApiKeyWebToolStack {
  val systemUserId = "dccc110c-c34f-40ed-be2c-7e34a9f1b8f0"

  get("/") {
    contentType = "text/html"
    layoutTemplate("/WEB-INF/templates/views/index.ssp", "userName" -> "", "errorMessage" -> "")
  }

  get("/error/:username") {
    val userName = params("username")
    val msg = "ユーザ \"%s\" は存在しません。".format(userName)

    contentType = "text/html"
    layoutTemplate("/WEB-INF/templates/views/index.ssp", "userName" -> userName, "errorMessage" -> msg)
  }

  post("/publish") {
    val userName = params("user_name")

    DB localTx { implicit s =>
      val u = User.u
      val userId = withSQL {
        select(u.result.id).from(User as u).where.eq(u.name, userName)
      }.map(rs => rs.string(u.resultName.id)).single.apply

      val timestamp = DateTime.now
      userId match {
        case Some(uId) =>
          val apiKey = DigestUtils.sha256Hex(UUID.randomUUID().toString)
          val secretKey = DigestUtils.sha256Hex(UUID.randomUUID().toString + apiKey)
          ApiKey.create(
            id = UUID.randomUUID().toString,
            userId = uId,
            apiKey = apiKey,
            secretKey = secretKey,
            permission = 3,
            createdBy = systemUserId,
            createdAt = timestamp,
            updatedBy = systemUserId,
            updatedAt = timestamp
          )
          contentType = "text/html"
          layoutTemplate("/WEB-INF/templates/views/result.ssp",
            "userName" -> userName, "consumerKey" -> apiKey, "secretKey" -> secretKey)
        case _ =>
          redirect("/error/" + userName)
      }
    }
  }

}
