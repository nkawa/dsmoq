package api

import java.util.ResourceBundle

import org.eclipse.jetty.servlet.ServletHolder

import _root_.api.api.logic.SpecCommonLogic
import com.google.api.services.oauth2.model.Userinfoplus
import dsmoq.AppConf
import dsmoq.controllers.{ GoogleOAuthController, AjaxResponse, ApiController, FileController }
import dsmoq.persistence._
import dsmoq.services.GoogleAccountService
import org.eclipse.jetty.server.Connector
import org.json4s.{ DefaultFormats, Formats, _ }
import org.scalatest.{ BeforeAndAfter, FreeSpec }
import org.scalatra.servlet.MultipartConfig
import org.scalatra.test.scalatest.ScalatraSuite
import scalikejdbc._
import scalikejdbc.config.DBsWithEnv
import dsmoq.persistence.PostgresqlHelper._

class GoogleAccountSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  val googleService = new GoogleAccountService(ResourceBundle.getBundle("message"))

  protected implicit val jsonFormats: Formats = DefaultFormats

  override def beforeAll() {
    super.beforeAll()
    DBsWithEnv("test").setup()
    System.setProperty(org.scalatra.EnvironmentKey, "test")

    val resource = ResourceBundle.getBundle("message")
    val servlet = new ApiController(resource)
    val holder = new ServletHolder(servlet.getClass.getName, servlet)
    // multi-part file upload config
    holder.getRegistration.setMultipartConfig(
      MultipartConfig(
        maxFileSize = Some(3 * 1024 * 1024),
        fileSizeThreshold = Some(1 * 1024 * 1024)
      ).toMultipartConfigElement
    )
    servletContextHandler.addServlet(holder, "/api/*")
    addServlet(new GoogleOAuthController(resource), "/google_oauth/*")
  }

  override def afterAll() {
    DBsWithEnv("test").close()
    super.afterAll()
  }

  before {
    //SpecCommonLogic.insertDummyData()
  }

  after {
    SpecCommonLogic.deleteAllCreateData()
  }

  "Authorization Test" - {
    "Googleアカウントからユーザーを正しく作成できるか" in {
      val dummyGoogleUser = new Userinfoplus()
      dummyGoogleUser.setEmail("dummy@dummy.co.jp")
      dummyGoogleUser.setId("dummyId")
      dummyGoogleUser.setName("dummyName")
      val user = googleService.getUser(dummyGoogleUser).get

      DB.readOnly { implicit s =>
        val u = User.u
        val ma = MailAddress.ma
        val g = Group.g
        val m = Member.m
        val gu = GoogleUser.gu
        withSQL {
          select.from(User as u).where.eqUuid(u.id, user.id)
        }.map(User(u.resultName)).single.apply() match {
          case Some(usr) => {
            usr.name should be(dummyGoogleUser.getEmail)
            usr.fullname should be(dummyGoogleUser.getName)
            usr.imageId should be(AppConf.defaultAvatarImageId)
          }
          case None => fail("ユーザーが作られていません")
        }

        withSQL {
          select.from(MailAddress as ma).where.eqUuid(ma.userId, user.id)
        }.map(MailAddress(ma.resultName)).single.apply() match {
          case Some(mailAddr) => {
            mailAddr.address should be(dummyGoogleUser.getEmail)
            mailAddr.status should be(1)
          }
          case None => fail("メールアドレスが作られていません")
        }

        withSQL {
          select.from(Group as g).where.eq(g.name, dummyGoogleUser.getEmail)
        }.map(Group(g.resultName)).single.apply() match {
          case Some(group) => {
            group.groupType should be(GroupType.Personal)
          }
          case None => fail("グループが作られていません")
        }

        withSQL {
          select.from(Member as m).where.eqUuid(m.userId, user.id)
        }.map(Member(m.resultName)).single.apply() match {
          case Some(member) => {
            member.role should be(GroupMemberRole.Manager)
            member.status should be(1)
          }
          case None => fail("メンバーが作成されていません")
        }

        withSQL {
          select.from(GoogleUser as gu).where.eqUuid(gu.userId, user.id)
        }.map(GoogleUser(gu.resultName)).single.apply() match {
          case Some(googleUsr) => {
            googleUsr.googleId should be(dummyGoogleUser.getId)
          }
          case None => fail("Googleユーザーが作成されていません")
        }
      }
    }
  }
}
