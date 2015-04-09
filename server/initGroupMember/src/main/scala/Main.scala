package initGroupMember

import java.io.File
import java.util.UUID

import com.github.tototoshi.csv.CSVReader
import dsmoq.persistence
import dsmoq.persistence.{GroupType, GroupMemberRole}
import dsmoq.persistence.PostgresqlHelper._
import org.joda.time._
import scalikejdbc._
import scalikejdbc.config.DBs

object Main {
  def main(args: Array[String]) {
    if (args.length < 1) {
      println("usage: initGroupMember/run filename")
      return
    }

    val file = new File(args(0))
    if (!file.exists()) {
      println("ファイルが見つかりません")
      return
    }
    if (!file.getPath.endsWith(".csv")) {
      println("csvファイルではありません")
      return
    }

    val reader = CSVReader.open(file)
    // 1行目(ヘッダ行)は飛ばす
    reader.readNext()

    val systemUserId = "dccc110c-c34f-40ed-be2c-7e34a9f1b8f0"
    val defaultAvatarImageId = "8a981652-ea4d-48cf-94db-0ceca7d81aef"
    val timestamp = DateTime.now
    val userDataColumns = 6

    DBs.setup()

    try {
      DB localTx { implicit s =>
        reader.foreach { seq =>
          // ユーザー情報6列 + グループ所属情報2*n = 偶数
          if (seq.length < userDataColumns || seq.length % 2 != 0) {
            throw new RuntimeException("グループ設定情報が不足しています:" + seq.toString)
          }

          val mailAddress = seq(0)
          val fullname = seq(1) + ' ' + seq(2)

          // ユーザーが存在するか検索(users.nameにメールアドレスが入る前提)
          val u = persistence.User.u
          val gu = persistence.GoogleUser.gu
          val googleUser = withSQL {
            select(u.result.*)
              .from(persistence.User as u)
              .innerJoin(persistence.GoogleUser as gu).on(u.id, gu.userId)
              .where
              .eq(u.name, mailAddress)
              .and
              .isNull(u.deletedAt)
              .and
              .isNull(gu.deletedAt)
          }.map(persistence.User(u.resultName)).single().apply

          val user = googleUser match {
            case Some(x) => x
            case None =>
              // なければデータ作る
              val user = persistence.User.create(
                id = UUID.randomUUID.toString,
                name = mailAddress,
                fullname = fullname,
                organization = "",
                title = "",
                description = "",
                imageId = defaultAvatarImageId,
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
                role = GroupMemberRole.Manager,
                status = 1,
                createdBy = systemUserId,
                createdAt = timestamp,
                updatedBy = systemUserId,
                updatedAt = timestamp
              )
              user
          }

          // グループにメンバー登録
          def toTupleList(list: List[String]): List[(String, String)] = list match {
            case x1 :: x2 :: xs => List((x1, x2)) ::: toTupleList(xs)
            case _ => List.empty
          }
          val groupAffiliations = toTupleList(seq.drop(userDataColumns).toList)
          groupAffiliations.foreach { ga =>
            // グループの存在確認
            val g = persistence.Group.g
            val group = withSQL {
              select(g.result.*)
                .from(persistence.Group as g)
                .where
                .eq(g.groupType, GroupType.Public)
                .and
                .eq(g.name, ga._1)
                .and
                .isNull(g.deletedAt)
            }.map(persistence.Group(g.resultName)).single().apply

            group match {
              case Some(x) =>
                val role = try {
                  val role = ga._2.toInt
                  if (role != GroupMemberRole.Member && role != GroupMemberRole.Manager) {
                      throw new RuntimeException("\"" + ga._1 + "\"グループの権限設定が間違っています(1:Member, 2:Manager):" + seq)
                  }
                  role
                } catch {
                  case e: NumberFormatException =>
                    throw new RuntimeException("\"" + ga._1 + "\"グループの権限設定が数値ではありません(1:Member, 2:Manager):" + seq)
                }

                val m = persistence.Member.m
                val member = withSQL {
                  select(m.result.*)
                    .from(persistence.Member as m)
                    .where
                    .eq(m.groupId, sqls.uuid(x.id))
                    .and
                    .eq(m.userId, sqls.uuid(user.id))
                }.map(persistence.Member(m.resultName)).single().apply

                member match {
                  case Some(y) =>
                    withSQL {
                      val m = persistence.Member.column
                      update(persistence.Member)
                        .set(m.role -> role, m.updatedAt -> timestamp, m.updatedBy -> sqls.uuid(systemUserId),
                          m.deletedAt -> None, m.deletedBy -> None)
                        .where
                        .eq(m.id, sqls.uuid(y.id))
                    }.update().apply
                  case None =>
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
                }
              case None =>
                throw new RuntimeException("\"" + ga._1 + "\"グループは存在しません:" + seq)
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
