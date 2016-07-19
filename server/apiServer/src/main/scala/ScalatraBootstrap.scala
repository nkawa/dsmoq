import org.scalatra._
import java.util.ResourceBundle
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

    val resource = ResourceBundle.getBundle("message")

    context.mount(new ResourceController, "/*")
    context.mount(new SessionsController, "/sessions/*")
    context.mount(new ApiController(resource), "/api/*")
    context.mount(new GoogleOAuthController(resource), "/google_oauth/*")
    context.mount(new ImageController(resource), "/images/*")
    context.mount(new FileController(resource), "/files/*")

    // mock
    context.mount(new MockController, "/mock")

    System.setProperty(org.scalatra.EnvironmentKey, "development")
  }

  override def destroy(context: ServletContext) {
    DBs.close()
  }
}
