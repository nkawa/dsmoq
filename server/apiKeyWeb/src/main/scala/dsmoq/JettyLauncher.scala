package dsmoq

import dsmoq.apikeyweb.AppConfig
import org.eclipse.jetty.security.authentication.BasicAuthenticator
import org.eclipse.jetty.security.{ConstraintMapping, ConstraintSecurityHandler, HashLoginService}
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.util.security.{Constraint, Password}
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener

/**
  * Jettyを起動するためのクラス。
  */
object JettyLauncher {
  /**
    * Jetttyのランチャobjectを作成し、起動する。
    *
    * @param args コンソール引数
    */
  def main(args: Array[String]) {
    val server = new Server(AppConfig.port)
    val context = new WebAppContext()
    context setContextPath "/"
    context.setResourceBase("src/main/webapp")
    context.addEventListener(new ScalatraListener)
    context.addServlet(classOf[DefaultServlet], "/")

    // BASIC認証の定義
    val roles = Array("user")
    val securityHandler = new ConstraintSecurityHandler
    val constraint = new Constraint
    constraint.setName(Constraint.__BASIC_AUTH)
    constraint.setRoles(roles)
    constraint.setAuthenticate(true)
    // mapping
    val mapping = new ConstraintMapping
    mapping.setPathSpec("/")
    mapping.setConstraint(constraint)
    securityHandler.setConstraintMappings(Array[ConstraintMapping](mapping))
    // login service
    val loginService = new HashLoginService
    loginService.putUser(AppConfig.user, new Password(AppConfig.password), roles)
    securityHandler.setLoginService(loginService)
    // authenticator
    val authenticator = new BasicAuthenticator
    securityHandler.setAuthenticator(authenticator)
    // set security
    context.setSecurityHandler(securityHandler)

    server.setHandler(context)

    server.start()
    server.join()
  }
}
