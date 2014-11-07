package createUser

import java.io.File
import java.util.UUID

import com.github.tototoshi.csv.CSVReader
import dsmoq.persistence
import dsmoq.persistence.GroupMemberRole
import dsmoq.persistence.PostgresqlHelper._
import org.joda.time._
import scalikejdbc._
import scalikejdbc.config.DBs

object Main {
  def main(args: Array[String]) {
    if (args.length < 1) {
      println("usage: createUser/run filename")
      return
    }

    val file = new File(args(0))
    if (!file.exists()) {
      println("ファイルが見つかりません")
      return
    }

    val reader = CSVReader.open(file)
    // 1行目(ヘッダ行)は飛ばす
    reader.readNext()

    val systemUserId = "dccc110c-c34f-40ed-be2c-7e34a9f1b8f0"
    val defaultDatasetImageId = "8b570468-9814-4d30-8c04-392b263b6404"
    val timestamp = DateTime.now

    DBs.setup()

    try {
      DB localTx { implicit s =>
        // メンバー情報全削除
        val g = persistence.Group.g
        val publicGroups = withSQL {
          select(g.result.*)
            .from(persistence.Group as g)
            .where
            .eq(g.groupType, 0)
            .and
            .isNull(g.deletedAt)
        }.map(persistence.Group(g.resultName)).list().apply.map{_.id}

        if (publicGroups.length > 0) {
          val m = persistence.Member.m
          withSQL {
            delete.from(persistence.Member as m)
              .where
              .inUuid(m.groupId, publicGroups)
          }.update().apply
        }

        reader.foreach { seq =>
          // ユーザー情報6列 + グループ所属情報2*n = 偶数
          if (seq.length < 8 || seq.length % 2 != 0) {
            throw new Exception("グループ設定情報が不足しています:" + seq.toString)
          }

          val mailAddress = seq(0)
          val username = seq(2) + seq(1)

          // ユーザーが存在するか検索(users.nameにメールアドレスが入る前提)
          val u = persistence.User.u
          val gu = persistence.GoogleUser.gu
          val googleUser = withSQL {
            select(u.result.*)
              .from(persistence.User as u)
              .innerJoin(persistence.GoogleUser as gu).on(u.id, gu.userId)
              .where
              .eq(u.name, seq(0))
              .and
              .isNull(u.deletedAt)
          }.map(persistence.User(u.resultName)).single().apply

          val user = googleUser match {
            case Some(x) =>
              // ユーザーデータがあれば情報更新
              withSQL {
                val u = persistence.User.column
                update(persistence.User)
                  .set(u.fullname -> username, u.updatedAt -> timestamp, u.updatedBy -> sqls.uuid(systemUserId))
                  .where
                  .eq(u.id, sqls.uuid(x.id))
              }.update().apply

              withSQL {
                val m = persistence.MailAddress.column
                update(persistence.MailAddress)
                  .set(m.address -> mailAddress, m.updatedAt -> timestamp, m.updatedBy -> sqls.uuid(systemUserId))
                  .where
                  .eq(m.userId, sqls.uuid(x.id))
              }.update().apply

              x
            case None =>
              // なければデータ作る
              val user = persistence.User.create(
                id = UUID.randomUUID.toString,
                name = mailAddress,
                fullname = username,
                organization = "",
                title = "",
                description = "",
                imageId = defaultDatasetImageId,
                createdBy = systemUserId,
                createdAt = timestamp,
                updatedBy = systemUserId,
                updatedAt = timestamp
              )

              persistence.MailAddress.create(
                id = UUID.randomUUID.toString,
                userId = user.id,
                address = mailAddress,
                status = 1,
                createdBy = systemUserId,
                createdAt = timestamp,
                updatedBy = systemUserId,
                updatedAt = timestamp
              )

              persistence.GoogleUser.create(
                id = UUID.randomUUID.toString,
                userId = user.id,
                googleId = null,
                createdBy = systemUserId,
                createdAt = timestamp,
                updatedBy = systemUserId,
                updatedAt = timestamp
              )

              val group = persistence.Group.create(
                id = UUID.randomUUID.toString,
                name = mailAddress,
                description = "",
                groupType = persistence.GroupType.Personal,
                createdBy = systemUserId,
                createdAt = timestamp,
                updatedBy = systemUserId,
                updatedAt = timestamp
              )

              persistence.Member.create(
                id = UUID.randomUUID.toString,
                groupId = group.id,
                userId = user.id,
                role = 1,
                status = 1,
                createdBy = systemUserId,
                createdAt = timestamp,
                updatedBy = systemUserId,
                updatedAt = timestamp
              )
              user
          }

          // グループにメンバー登録
          def convertTupleList(list: List[String]): List[(String, String)] = list match {
            case x1 :: x2 :: xs => List((x1, x2)) ::: convertTupleList(xs)
            case _ => List.empty
          }
          val groupAffiliations = convertTupleList(seq.drop(6).toList)
          groupAffiliations.foreach { ga =>
            // グループの存在確認
            val g = persistence.Group.g
            val group = withSQL {
              select(g.result.*)
                .from(persistence.Group as g)
                .where
                .eq(g.groupType, 0)
                .and
                .eq(g.name, ga._1)
            }.map(persistence.Group(g.resultName)).single().apply

            group match {
              case Some(x) =>
                val role = ga._2 match {
                  case "0" => GroupMemberRole.Member
                  case "1" => GroupMemberRole.Manager
                  case _ =>
                    throw new Exception("\"" +ga._1 + "\"グループの権限設定が間違っています(0:Member, 1:Manager):" + seq)
                    0
                }
                persistence.Member.create(
                  id = UUID.randomUUID.toString,
                  groupId = x.id,
                  userId = user.id,
                  role = role,
                  status = 1,
                  createdBy = systemUserId,
                  createdAt = timestamp,
                  updatedBy = systemUserId,
                  updatedAt = timestamp
                )
              case None =>
                throw new Exception("\"" + ga._1 + "\"グループは存在しません:" + seq)
            }
          }
        }
      }
      println("ユーザー追加・メンバー登録処理が完了しました")
    } catch {
      case e: Throwable =>
        if (e.getMessage != null) { println(e.getMessage) }
        println("エラーが発生したため終了します")
    } finally {
      DBs.close()
    }
  }
}
