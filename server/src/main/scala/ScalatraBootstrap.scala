import org.scalatra._
import javax.servlet.ServletContext
import scalikejdbc.config.DBs
import scalikejdbc._
import dsmoq.controllers._

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
      enabled = true,
      singleLineMode = true,
      logLevel = 'DEBUG
    )
    DBs.setup()

    context.mount(new ResourceController, "/*")
    context.mount(new SessionsController, "/sessions/*")
    context.mount(new ApiController, "/api/*")
    context.mount(new OAuthController, "/oauth/*")
    context.mount(new ImageController, "/images/*")
    context.mount(new FileController, "/files/*")

    // mock
    context.mount(new MockController, "/mock")

    System.setProperty(org.scalatra.EnvironmentKey, "development")
  }

  override def destroy(context: ServletContext) {
    DBs.close()
  }
}
