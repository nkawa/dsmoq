package initGroupMember

import java.io.File
import java.util.UUID
import dsmoq.persistence.PostgresqlHelper._
import dsmoq.persistence._
import org.joda.time.DateTime
import org.scalatest._
import scalikejdbc._
import scalikejdbc.config.DBsWithEnv

class InitGroupMemberSpec extends FreeSpec with BeforeAndAfter with BeforeAndAfterAll with org.scalatest.Suite with org.scalatest.Matchers {
  val systemUserId = "dccc110c-c34f-40ed-be2c-7e34a9f1b8f0"
  val defaultAvatarImageId = "8a981652-ea4d-48cf-94db-0ceca7d81aef"

  override def beforeAll() {
    super.beforeAll()
    DBsWithEnv("test").setup()
  }

  override def afterAll() {
    DBsWithEnv("test").close()
    super.afterAll()
  }

  before {
    DB localTx { implicit s =>
      deleteAllData(deleteFrom(User))
      deleteAllData(deleteFrom(Password))
      deleteAllData(deleteFrom(MailAddress))
      deleteAllData(deleteFrom(Group))
      deleteAllData(deleteFrom(Member))
      deleteAllData(deleteFrom(GoogleUser))
    }
  }

  after {
    DB localTx { implicit s =>
      deleteAllData(deleteFrom(User))
      deleteAllData(deleteFrom(Password))
      deleteAllData(deleteFrom(MailAddress))
      deleteAllData(deleteFrom(Group))
      deleteAllData(deleteFrom(Member))
      deleteAllData(deleteFrom(GoogleUser))
    }
  }

  "Test" - {
    "Main" - {
      "バッチ実行後、Googleユーザーがなければユーザー情報が作成されるか" in {
        val groupId1 = createGroup("test group1")
        Main.execute(new File("testdata/test1.csv"))
        val fullname = "test name1 test1"
        val email = "test1@google.com"
        DB.readOnly { implicit session =>
          val u = User.u
          val ma = MailAddress.ma
          val g = Group.g
          val m = Member.m
          val gu = GoogleUser.gu
          val userId: String = withSQL {
            select.from(User as u).where.eq(u.name, email)
          }.map(User(u.resultName)).single().apply() match {
            case Some(usr) => {
              usr.fullname should be(fullname)
              usr.imageId should be(defaultAvatarImageId)
              usr.id
            }
            case None => fail("ユーザーが作られていません")
          }

          withSQL {
            select.from(MailAddress as ma).where.eqUuid(ma.userId, userId)
          }.map(MailAddress(ma.resultName)).single().apply() match {
            case Some(mailAddr) => {
              mailAddr.address should be(email)
              mailAddr.status should be(1)
            }
            case None => fail("メールアドレスが作られていません")
          }

          val pGroupId = withSQL {
            select.from(Group as g).where.eq(g.name, email)
          }.map(Group(g.resultName)).single().apply() match {
            case Some(group) => {
              group.groupType should be(GroupType.Personal)
              group.id
            }
            case None => fail("グループが作られていません")
          }

          withSQL {
            select.from(Member as m).where.eqUuid(m.userId, userId).and.eqUuid(m.groupId, pGroupId)
          }.map(Member(m.resultName)).single().apply() match {
            case Some(member) => {
              member.role should be(GroupMemberRole.Manager)
              member.status should be(1)
            }
            case None => fail("メンバーが作成されていません")
          }

          withSQL {
            select.from(Member as m).where.eqUuid(m.userId, userId).and.eqUuid(m.groupId, groupId1)
          }.map(Member(m.resultName)).single().apply() match {
            case Some(member) => {
              member.role should be(GroupMemberRole.Manager)
              member.status should be(1)
            }
            case None => fail("メンバーに追加されていません")
          }

          withSQL {
            select.from(GoogleUser as gu).where.eqUuid(gu.userId, userId)
          }.map(GoogleUser(gu.resultName)).single().apply() match {
            case Some(googleUsr) => {
              googleUsr.googleId should be(null)
            }
            case None => fail("Googleユーザーが作成されていません")
          }
        }
      }

      "バッチ実行後、ユーザーのグループ権限が追加されているか" in {
        val groupId1 = createGroup("test group1")
        val groupId2 = createGroup("test group2")
        Main.execute(new File("testdata/test2.csv"))
        val email = "test2@google.com"
        DB.readOnly { implicit session =>
          val u = User.u
          val m = Member.m
          val userId: String = withSQL {
            select.from(User as u).where.eq(u.name, email)
          }.map(User(u.resultName)).single().apply() match {
            case Some(usr) => {
              usr.id
            }
            case None => fail("ユーザーが作られていません")
          }

          withSQL {
            select.from(Member as m).where.eqUuid(m.userId, userId).and.eqUuid(m.groupId, groupId1)
          }.map(Member(m.resultName)).single().apply() match {
            case Some(member) => {
              member.role should be(GroupMemberRole.Manager)
              member.status should be(1)
            }
            case None => fail("メンバーに追加されていません")
          }

          withSQL {
            select.from(Member as m).where.eqUuid(m.userId, userId).and.eqUuid(m.groupId, groupId2)
          }.map(Member(m.resultName)).single().apply() match {
            case Some(member) => {
              member.role should be(GroupMemberRole.Member)
              member.status should be(1)
            }
            case None => fail("メンバーに追加されていません")
          }
        }
      }

      "バッチ実行後、ユーザーグループの権限が更新されているか" in {
        val groupId1 = createGroup("test group1")
        Main.execute(new File("testdata/test1.csv"))
        Main.execute(new File("testdata/test3.csv"))
        val email = "test1@google.com"
        DB.readOnly { implicit session =>
          val u = User.u
          val m = Member.m
          val userId: String = withSQL {
            select.from(User as u).where.eq(u.name, email)
          }.map(User(u.resultName)).single().apply() match {
            case Some(usr) => {
              usr.id
            }
            case None => fail("ユーザーが作られていません")
          }

          withSQL {
            select.from(Member as m).where.eqUuid(m.userId, userId).and.eqUuid(m.groupId, groupId1)
          }.map(Member(m.resultName)).single().apply() match {
            case Some(member) => {
              member.role should be(GroupMemberRole.Member)
              member.status should be(1)
            }
            case None => fail("メンバーに追加されていません")
          }
        }
      }
    }
  }

  private def createGroup(name: String): String = {
    DB localTx { implicit s =>
      val now = DateTime.now
      Group.create(
        id = UUID.randomUUID().toString,
        name = name,
        description = "",
        groupType = GroupType.Public,
        createdBy = systemUserId,
        createdAt = now,
        updatedBy = systemUserId,
        updatedAt = now
      ).id
    }
  }

  private def deleteAllData(query: SQLBuilder[UpdateOperation])(implicit s: DBSession) {
    withSQL {
      query
    }.update().apply
  }
}
