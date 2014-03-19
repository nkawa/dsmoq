import dsmoq._
import org.scalatra._
import javax.servlet.ServletContext
import scalikejdbc.config.DBs
import scalikejdbc._

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
    context.mount(new ServerApiController, "/api/*")

    // mock
    context.mount(new MockController, "/mock")
  }

  override def destroy(context: ServletContext) {
    DBs.close()
  }
}
