package dsmoq

import org.eclipse.jetty.security.authentication.BasicAuthenticator
import org.eclipse.jetty.security.{ConstraintMapping, ConstraintSecurityHandler, HashLoginService}
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.util.security.{Constraint, Password}
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener

object JettyLauncher {
  def main(args: Array[String]) {
    val port = if (System.getenv("PORT") != null) System.getenv("PORT").toInt else 8090

    val server = new Server(port)
    val context = new WebAppContext()
    context setContextPath "/"
    context.setResourceBase("src/main/webapp")
    context.addEventListener(new ScalatraListener)
    context.addServlet(classOf[DefaultServlet], "/")

    val securityHandler = new ConstraintSecurityHandler
    val constraint = new Constraint
    constraint.setName(Constraint.__BASIC_AUTH)
    constraint.setRoles(Array[String]("user"))
    constraint.setAuthenticate(true)
    // mapping
    val mapping = new ConstraintMapping
    mapping.setPathSpec("/")
    mapping.setConstraint(constraint)
    securityHandler.setConstraintMappings(Array[ConstraintMapping](mapping))
    // login service
    val loginService = new HashLoginService
    loginService.putUser("user", new Password("user"), Array[String]("user"))
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
