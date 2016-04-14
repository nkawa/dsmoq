package dsmoq

import com.typesafe.config.ConfigFactory
import org.eclipse.jetty.security.authentication.BasicAuthenticator
import org.eclipse.jetty.security.{ConstraintMapping, ConstraintSecurityHandler, HashLoginService}
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.util.security.{Constraint, Password}
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener

object JettyLauncher {
  def main(args: Array[String]) {
    val configs = SettingParser.parse()

    val server = new Server(configs.port)
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
    loginService.putUser(configs.user, new Password(configs.password), roles)
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

case class Configs(port : Int, user: String, password: String)

object SettingParser {
  val KEY_APP_PORT = "app.port"
  val KEY_APP_USER = "app.user"
  val KEY_APP_PASSWORD = "app.password"

  def parse(): Configs = {
    val config = ConfigFactory.load()
    val port = if (config.hasPath(KEY_APP_PORT)) config.getInt(KEY_APP_PORT) else 8090
    val user = if (config.hasPath(KEY_APP_USER)) config.getString(KEY_APP_USER) else "user"
    val password = if (config.hasPath(KEY_APP_PASSWORD)) config.getString(KEY_APP_PASSWORD) else "pass"

    Configs(port, user, password)
  }
}
