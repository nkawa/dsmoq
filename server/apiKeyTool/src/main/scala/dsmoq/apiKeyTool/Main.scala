package dsmoq.apiKeyTool

import java.util.UUID
import org.joda.time.DateTime

import dsmoq.persistence._
import scalikejdbc._
import PostgresqlHelper._
import scalikejdbc.config.DBs
import org.apache.commons.codec.digest.DigestUtils

object Main {
  val systemUserId = "dccc110c-c34f-40ed-be2c-7e34a9f1b8f0"

  def main(args: Array[String]): Unit = {
    try {
      DBs.setup()
      args.toList match {
        case "list" :: Nil => listKeys()
        case "search" :: loginName :: Nil => searchKeyFromName(loginName)
        case "publish" :: loginName :: Nil => publishKey(loginName)
        case "remove" :: consumerKey :: Nil => deleteKey(consumerKey)
        case _ => printUsage()
      }
    } catch {
      case e: Exception => {
        println("エラーが発生したため、プログラムを終了します: ")
        e.printStackTrace()
      }
    }
  }

  def listKeys(): Unit = {
    DB readOnly { implicit s =>
      val ak = ApiKey.ak
      val u = User.u
      val apiKeys = withSQL {
        select(u.result.*, ak.result.apiKey, ak.result.secretKey)
          .from(ApiKey as ak)
          .innerJoin(User as u).on(ak.userId, u.id)
          .where
          .isNull(ak.deletedBy)
          .and
          .isNull(ak.deletedAt)
          .orderBy(u.name, ak.createdAt)
      }.map(rs => (rs.string(u.resultName.name), rs.string(ak.resultName.apiKey), rs.string(ak.resultName.secretKey))).list.apply()
      println("%s found.".format(apiKeys.size))
      println("login name/consumer key/secret key")
      apiKeys.map(x => x._1 + "/" + x._2 + "/" + x._3).foreach(println)
    }
  }

  def searchKeyFromName(loginName: String): Unit = {
    DB readOnly { implicit s =>
      val ak = ApiKey.ak
      val u = User.u
      val apiKeys = withSQL {
        select(u.result.*, ak.result.apiKey, ak.result.secretKey)
          .from(ApiKey as ak)
          .innerJoin(User as u).on(ak.userId, u.id)
          .where
          .eq(u.name, loginName)
          .and
          .isNull(ak.deletedBy)
          .and
          .isNull(ak.deletedAt)
          .orderBy(ak.createdAt)
      }.map(rs => (rs.string(u.resultName.name), rs.string(ak.resultName.apiKey), rs.string(ak.resultName.secretKey))).list.apply()
      println("%s found.".format(apiKeys.size))
      println("login name/consumer key/secret key")
      apiKeys.map(x => x._1 + "/" + x._2 + "/" + x._3).foreach(println)
    }
  }

  def publishKey(loginName: String): Unit = {
    DB localTx { implicit s =>
      val u = User.u
      val userId = withSQL {
        select(u.result.id).from(User as u).where.eq(u.name, loginName)
      }.map(rs => rs.string(u.resultName.id)).single.apply

      val timestamp = DateTime.now
      userId match {
        case Some(uId) => {
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
          println("login name/consumer key/secret key")
          println("%s/%s/%s".format(loginName, apiKey, secretKey))
        }
        case _ => println("error: そのユーザ名のユーザは存在しません。")
      }
    }
  }

  def deleteKey(key: String): Unit = {
    DB localTx { implicit s =>
      val ak = ApiKey.ak
      val apiKey = withSQL {
        select.from(ApiKey as ak).where.eq(ak.apiKey, key)
      }.map(ApiKey(ak)).single.apply

      val timestamp = DateTime.now
      apiKey match {
        case Some(k) => {
          val c = ApiKey.column
          withSQL {
            update(ApiKey)
              .set(c.deletedAt -> timestamp, c.deletedBy -> sqls.uuid(systemUserId))
              .where
              .eq(c.id, sqls.uuid(k.id))
          }.update().apply
        }
        case _ => println("error: そのAPIキーは存在しません。")
      }
    }
  }

  private def printUsage(): Unit = {
    println(
      """usage:
        |apiKeyTool list                      : APIキーの一覧を表示します.
        |apiKeyTool search <some login name>  : 指定したユーザ名に割り当てられたAPIキーの一覧を表示します。
        |apiKeyTool publish <some login name> : 指定したユーザ名のユーザに対して、APIキーを発行します。
        |apiKeyTool remove <consumer_key>     : 指定したAPIキーを無効化します。
      """.stripMargin
    )
  }

}
